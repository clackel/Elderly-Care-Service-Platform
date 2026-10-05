package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.common.api.PageQuery;
import com.elderlycare.platform.common.api.PageResponse;
import com.elderlycare.platform.notification.api.NotificationResponses.*;
import com.elderlycare.platform.notification.domain.MessageRow;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 通知和待办只查询当前授权范围；已读不改变预约或履约状态。 */
@Service
public class NotificationService {
    private static final Set<String> TODO_TYPES = Set.of("PENDING_CONFIRMATION", "EXCEPTION", "OVERDUE", "WAITING_START", "IN_PROGRESS");
    private final BookingAccess access;
    private final NotificationQueries queries;

    /** 复用预约身份与社区边界，SQL另校验事件时权限周期。 */
    public NotificationService(BookingAccess access, NotificationQueries queries) { this.access = access; this.queries = queries; }

    /** 分页列出当前仍有效的通知；同一读取事务使用一致的授权快照和时间点。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Notice> list(Authentication authentication, PageQuery page, boolean unreadOnly) {
        var actor = access.actor(authentication); var now = Instant.now();
        long count = queries.count(actor, unreadOnly, now);
        var rows = queries.list(actor, unreadOnly, now, page.offset(), page.pageSize());
        return new PageResponse<>(rows.stream().map(this::notice).toList(), page.page(), page.pageSize(), count);
    }

    /** 未读数使用与列表完全相同的权限条件，失效通知不计入提醒。 */
    public Unread unread(Authentication authentication) {
        var actor = access.actor(authentication);
        return new Unread(queries.count(actor, true, Instant.now()));
    }

    /** 持有社区锁后重读身份和通知权限；重复标已读安全，不能标记他人或已失效通知。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void read(Authentication authentication, long id) {
        var actor = access.writer(authentication); var now = Instant.now();
        if (queries.visible(actor, id, now) == null)
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在或已不在可访问范围");
        queries.markRead(actor, id, now);
    }

    /** 实时派生社区共享待办或本人执行任务；老人及家属返回空列表，不赋予处理权限。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Todo> todos(Authentication authentication, PageQuery page, String type) {
        var actor = access.actor(authentication);
        type = type == null || type.isBlank() ? null : type.strip();
        if (type != null && !TODO_TYPES.contains(type))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请选择有效的待办类型");
        var now = Instant.now(); long count = queries.todoCount(actor, type, now);
        var rows = queries.todos(actor, type, now, page.offset(), page.pageSize());
        return new PageResponse<>(rows.stream().map(row -> new Todo(row.bookingId.toString(), row.todoType, row.status,
                row.scheduledStart, row.scheduledEnd, row.hasException, row.overdue)).toList(), page.page(), page.pageSize(), count);
    }

    /** 任务移交消息保持无业务编号，其他通知仅提供经过当前权限校验的预约入口。 */
    private Notice notice(MessageRow row) {
        return new Notice(row.id.toString(), row.action, row.bookingId == null ? null : row.bookingId.toString(), row.occurredAt, row.readAt != null);
    }
}
