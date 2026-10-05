package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.notification.domain.OutboxRow;
import com.elderlycare.platform.notification.mapper.OutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxFailureServiceTest {
    private final BookingAccess access = mock(BookingAccess.class);
    private final OutboxMapper mapper = mock(OutboxMapper.class);
    private final Authentication auth = mock(Authentication.class);
    private final OutboxFailureService service = new OutboxFailureService(access, mapper);

    @Test void retryUsesLockedCurrentCommunityAndVersion() {
        var row = setup(); when(mapper.retry(eq(9L), eq(11L), eq(3L), any())).thenReturn(1);
        service.retry(auth, 11L, 3L);
        verify(access).requireOperator(any()); verify(mapper).retry(eq(9L), eq(11L), eq(3L), any());
        row.status = "READY"; service.retry(auth, 11L, 3L);
        verify(mapper, times(1)).retry(anyLong(), anyLong(), anyLong(), any());
    }

    @Test void staleRetryCannotResetAnotherFailureCycle() {
        setup(); var error = assertThrows(BusinessException.class, () -> service.retry(auth, 11L, 2L));
        assertEquals("VERSION_CONFLICT", error.code()); verify(mapper, never()).retry(anyLong(), anyLong(), anyLong(), any());
    }

    @Test void crossCommunityRecordIsNotReturnedOrRetried() {
        setup(); when(mapper.lockFailure(9L, 11L)).thenReturn(null);
        var error = assertThrows(BusinessException.class, () -> service.retry(auth, 11L, 3L));
        assertEquals("DELIVERY_NOT_FOUND", error.code()); verify(mapper, never()).retry(anyLong(), anyLong(), anyLong(), any());
    }

    /** 提供已在社区锁后核验的身份和失败事项，不建立实际数据库连接。 */
    private OutboxRow setup() {
        var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(9L); actor.setRole("PLATFORM_ADMIN");
        when(access.writer(auth)).thenReturn(actor);
        var row = new OutboxRow(); row.eventId = 11L; row.status = "FAILED"; row.version = 3;
        when(mapper.lockFailure(9L, 11L)).thenReturn(row); return row;
    }
}
