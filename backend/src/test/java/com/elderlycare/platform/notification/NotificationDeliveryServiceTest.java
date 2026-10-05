package com.elderlycare.platform.notification;

import com.elderlycare.platform.identity.domain.Community;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.notification.domain.OutboxRow;
import com.elderlycare.platform.notification.domain.RecipientRow;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import com.elderlycare.platform.notification.service.NotificationDeliveryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用合成事件验证重复消费及失败边界，不连接消息队列或数据库。 */
class NotificationDeliveryServiceTest {
    private final NotificationQueries queries = mock(NotificationQueries.class);
    private final CommunityMapper communities = mock(CommunityMapper.class);
    private final NotificationDeliveryService service = new NotificationDeliveryService(queries, communities);

    /** 重复投递已有消息时只补缺失接收人，消息完成后才确认发件箱。 */
    @Test void redeliverySkipsExistingMessagesAndCommitsCompletionLast() {
        prepare(); var first = recipient(2L); var second = recipient(3L);
        when(queries.deliverable(eq(7L), any())).thenReturn(List.of(first, second));
        when(queries.messageId(7, 2)).thenReturn(20L);
        when(queries.insertMessage(anyLong(), eq(7L), eq(3L), eq(9L), any())).thenReturn(1);
        when(queries.delivered(7)).thenReturn(1);
        service.deliver(7);
        verify(queries, never()).insertMessage(anyLong(), eq(7L), eq(2L), anyLong(), any());
        var order = inOrder(queries); order.verify(queries).insertMessage(anyLong(), eq(7L), eq(3L), eq(9L), any());
        order.verify(queries).delivered(7);
    }

    /** 所有接收人的授权均已失效时仍结束事件，但不得生成任何站内消息。 */
    @Test void revokedRecipientsProduceNoMessagesAndDoNotRemainPending() {
        prepare(); when(queries.deliverable(eq(7L), any())).thenReturn(List.of()); when(queries.delivered(7)).thenReturn(1);
        service.deliver(7);
        verify(queries, never()).insertMessage(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(queries).delivered(7);
    }

    /** 任一消息保存失败时不能提前标为完成；已完成事件再次收到时不重新扩大接收范围。 */
    @Test void failedMessageCannotBeAcknowledgedAndDeliveredEventIsNoOp() {
        var event = prepare(); when(queries.deliverable(eq(7L), any())).thenReturn(List.of(recipient(2L)));
        assertThatThrownBy(() -> service.deliver(7)).isInstanceOf(IllegalStateException.class);
        verify(queries, never()).delivered(anyLong());
        clearInvocations(queries, communities); event.status = "DELIVERED";
        service.deliver(7);
        verify(queries, never()).deliverable(anyLong(), any()); verifyNoInteractions(communities);
    }

    /** 合成投递事项只携带事件和社区编号。 */
    private OutboxRow prepare() {
        var event = new OutboxRow(); event.eventId = 7L; event.communityId = 9L; event.status = "IN_FLIGHT";
        when(queries.outbox(7)).thenReturn(event); when(queries.lockOutbox(7)).thenReturn(event);
        when(queries.messageId(anyLong(), anyLong())).thenReturn(null);
        when(communities.lockActiveById(9L)).thenReturn(new Community(9L, "合成社区"));
        return event;
    }

    /** 构造已由共享权限查询筛选的接收账号摘要。 */
    private RecipientRow recipient(long id) { var row = new RecipientRow(); row.accountId = id; return row; }
}
