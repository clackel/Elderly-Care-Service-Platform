package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.booking.domain.BookingRow;
import com.elderlycare.platform.booking.domain.EventRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.notification.domain.OutboxRow;
import com.elderlycare.platform.notification.domain.RecipientRow;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 预约事件同事务记录发件箱及接收范围，事务外不得单独补发通知。 */
@Service
public class NotificationEventService {
    private final NotificationQueries queries;

    /** 装配通知快照持久化，调用方必须已持有预约所属社区锁。 */
    public NotificationEventService(NotificationQueries queries) { this.queries = queries; }

    /** 保存事件时刻有权限的接收人；改派旧人员只收到无详情链接的任务移交提示。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(UserAccount actor, BookingRow booking, EventRow event, Long previousWorker) {
        var outbox = new OutboxRow(); outbox.eventId = event.id; outbox.communityId = booking.communityId;
        outbox.bookingId = booking.id; outbox.action = event.action; outbox.occurredAt = event.occurredAt;
        if (queries.insertOutbox(outbox) != 1) throw new IllegalStateException("预约通知事件保存失败");
        var recipients = queries.snapshots(booking.communityId, booking.elderId, booking.workerId,
                booking.assignmentVersion, actor.getId(), event.occurredAt);
        for (var recipient : recipients) save(recipient, event.id, event.action);
        if (previousWorker != null && !Objects.equals(previousWorker, booking.workerId) && !Objects.equals(previousWorker, actor.getId())) {
            var previous = queries.transferred(booking.communityId, previousWorker);
            if (previous != null) save(previous, event.id, "TASK_TRANSFERRED");
        }
    }

    /** 仅保存接收人及权限周期，通知文本在客户端按固定动作生成。 */
    private void save(RecipientRow recipient, long eventId, String action) {
        recipient.eventId = eventId; recipient.action = action;
        if (queries.insertRecipient(recipient) != 1) throw new IllegalStateException("通知接收范围保存失败");
    }
}
