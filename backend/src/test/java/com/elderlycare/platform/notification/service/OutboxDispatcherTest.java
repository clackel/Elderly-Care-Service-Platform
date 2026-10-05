package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.notification.domain.OutboxRow;
import com.elderlycare.platform.notification.mapper.OutboxMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    private final Instant now = Instant.parse("2026-10-05T00:00:00Z");
    private final OutboxMapper mapper = mock(OutboxMapper.class);
    private final OutboxPublisher publisher = mock(OutboxPublisher.class);
    private final OutboxDispatcher dispatcher = new OutboxDispatcher(mapper, publisher, Clock.fixed(now, ZoneOffset.UTC));

    @Test void brokerConfirmationDoesNotMarkDeliveryComplete() {
        due(0); dispatcher.dispatch();
        verify(publisher).publish(eq(11L), anyString());
        verify(mapper, never()).failed(anyLong(), anyString(), anyString(), any(), anyString());
        verify(mapper).claim(eq(11L), eq(3L), anyString(), eq(now), eq(now.plusSeconds(120)), eq(5));
    }

    @Test void losingClaimCannotPublishDuplicateWork() {
        due(0); when(mapper.claim(anyLong(), anyLong(), anyString(), any(), any(), anyInt())).thenReturn(0);
        dispatcher.dispatch(); verifyNoInteractions(publisher);
    }

    @Test void transientFailureBacksOffAndLastAttemptStops() {
        due(1); when(publisher.publish(anyLong(), anyString())).thenReturn("UNROUTABLE"); dispatcher.dispatch();
        verify(mapper).failed(eq(11L), anyString(), eq("READY"), eq(now.plusSeconds(10)), eq("UNROUTABLE"));
        clearInvocations(mapper); due(4); dispatcher.dispatch();
        verify(mapper).failed(eq(11L), anyString(), eq("FAILED"), eq(now.plusSeconds(80)), eq("UNROUTABLE"));
    }

    @Test void exhaustedLeaseIsReportedWithoutAnotherPublish() {
        due(5); dispatcher.dispatch(); verify(mapper).exhausted(11L, 3L, now); verifyNoInteractions(publisher);
    }

    /** 构造一条到期事件和成功占用结果，测试不访问数据库或消息队列。 */
    private void due(int attempts) {
        var row = new OutboxRow(); row.eventId = 11L; row.version = 3; row.attempts = attempts;
        when(mapper.due(now, 20)).thenReturn(List.of(row));
        when(mapper.claim(anyLong(), anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
    }
}
