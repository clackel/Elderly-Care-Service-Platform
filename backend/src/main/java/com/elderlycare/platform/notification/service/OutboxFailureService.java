package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.notification.mapper.OutboxMapper;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 社区管理人员查看本社区发送异常并按版本重试，不能修改接收人或消息正文。 */
@Service
public class OutboxFailureService {
    public record Failure(String eventId, String action, Instant occurredAt, int attempts, String errorCode, long version) {}
    private final BookingAccess access;
    private final OutboxMapper mapper;

    /** 复用预约管理角色及社区边界，平台管理员同样不能跨社区操作。 */
    public OutboxFailureService(BookingAccess access, OutboxMapper mapper) { this.access = access; this.mapper = mapper; }

    /** 只返回失败状态元数据，不暴露预约编号、接收人或异常堆栈。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Failure> list(Authentication auth, PageQuery page) {
        var actor = access.operator(auth);
        long count = mapper.failureCount(actor.getCommunityId());
        var items = mapper.failures(actor.getCommunityId(), page.offset(), page.pageSize()).stream()
                .map(row -> new Failure(row.eventId.toString(), row.action, row.occurredAt, row.attempts, row.errorCode, row.version)).toList();
        return new PageResponse<>(items, page.page(), page.pageSize(), count);
    }

    /** 获取社区锁后重验权限；重复点击已重新排队的记录无副作用，旧版本不能覆盖下一轮失败。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void retry(Authentication auth, long eventId, long version) {
        var actor = access.writer(auth); access.requireOperator(actor);
        var row = mapper.lockFailure(actor.getCommunityId(), eventId);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "发送记录不存在或不在本社区范围");
        if (!"FAILED".equals(row.status)) return;
        BookingAccess.version(row.version, version);
        if (mapper.retry(actor.getCommunityId(), eventId, version, Instant.now()) != 1)
            throw BookingAccess.conflict("VERSION_CONFLICT", "发送记录已更新，请刷新后重新操作");
    }
}
