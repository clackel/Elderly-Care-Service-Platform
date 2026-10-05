package com.elderlycare.platform.notification;

import com.elderlycare.platform.booking.domain.BookingRow;
import com.elderlycare.platform.booking.domain.EventRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.notification.domain.RecipientRow;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import com.elderlycare.platform.notification.service.NotificationEventService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证改派通知的收件关系和权限快照，不解析或复制业务正文。 */
class NotificationEventServiceTest {
    /** 新人员收到正常安排事件，旧人员仅有移交动作且没有旧安排权限快照。 */
    @Test void reassignmentCapturesGenericTransferWithoutGrantingOldWorkerBookingAccess() {
        var queries = mock(NotificationQueries.class); var service = new NotificationEventService(queries);
        var actor = new UserAccount(); actor.setId(1L);
        var booking = new BookingRow(); booking.id = 8L; booking.communityId = 9L; booking.elderId = 6L;
        booking.workerId = 3L; booking.assignmentVersion = 4;
        var event = new EventRow(); event.id = 7L; event.action = "RESCHEDULE"; event.occurredAt = Instant.now();
        var current = new RecipientRow(); current.accountId = 3L; current.assignmentVersion = 4L;
        var previous = new RecipientRow(); previous.accountId = 2L;
        when(queries.insertOutbox(any())).thenReturn(1);
        when(queries.snapshots(eq(9L), eq(6L), eq(3L), eq(4L), eq(1L), any())).thenReturn(List.of(current));
        when(queries.transferred(9, 2)).thenReturn(previous); when(queries.insertRecipient(any())).thenReturn(1);
        service.capture(actor, booking, event, 2L);
        assertThat(current.action).isEqualTo("RESCHEDULE"); assertThat(previous.action).isEqualTo("TASK_TRANSFERRED");
        assertThat(previous.assignmentVersion).isNull(); assertThat(previous.bindingId).isNull(); assertThat(previous.grantId).isNull();
        assertThat(current.eventId).isEqualTo(7); assertThat(previous.eventId).isEqualTo(7);
        clearInvocations(queries); service.capture(actor, booking, event, 3L);
        verify(queries, never()).transferred(anyLong(), anyLong());
    }
}
