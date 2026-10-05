package com.elderlycare.platform.notification.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 消息队列消费只传事件编号，站内消息和完成状态通过数据库事务保持一致。 */
@Service
public class NotificationDeliveryService {
    private final NotificationQueries queries;
    private final CommunityMapper communities;

    /** 装配社区锁及统一权限查询，不信任消息队列提交的接收人或正文。 */
    public NotificationDeliveryService(NotificationQueries queries, CommunityMapper communities) {
        this.queries = queries; this.communities = communities;
    }

    /** 重复消费不重复写入；权限已撤销者跳过，全部失效也确认该事件已处理。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deliver(long eventId) {
        var before = queries.outbox(eventId);
        if (before == null || "DELIVERED".equals(before.status)) return;
        if (communities.lockActiveById(before.communityId) == null)
            throw new IllegalStateException("通知所属社区不可用，请联系管理员处理");
        var event = queries.lockOutbox(eventId);
        if (event == null || "DELIVERED".equals(event.status)) return;
        if (!Objects.equals(before.communityId, event.communityId)) throw new IllegalStateException("通知所属社区已变更，请重新处理");
        var now = Instant.now();
        for (var recipient : queries.deliverable(eventId, now)) {
            if (queries.messageId(eventId, recipient.accountId) != null) continue;
            if (queries.insertMessage(IdWorker.getId(), eventId, recipient.accountId, event.communityId, now) != 1)
                throw new IllegalStateException("站内通知保存失败，请稍后重试");
        }
        if (queries.delivered(eventId) != 1) throw new IllegalStateException("通知完成状态保存失败，请稍后重试");
    }
}
