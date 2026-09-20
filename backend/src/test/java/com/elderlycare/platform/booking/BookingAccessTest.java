package com.elderlycare.platform.booking;

import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookingAccessTest {
    /** 家属有效关系必须由持久化权限查询确认，不能只根据提交的老人编号放行。 */
    @Test void deniedOrExpiredObjectAccessIsNotFound() {
        var queries = mock(BookingQueries.class);
        var access = new BookingAccess(mock(CommunityAccess.class), mock(CommunityMapper.class), queries, mock(AccessEventMapper.class));
        var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(2L); actor.setRole("FAMILY");
        when(queries.elderAccess(eq(actor), eq(3L), eq(true), any())).thenReturn(0L);
        assertThatThrownBy(() -> access.elder(actor, 3L, true)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException)e).status().value()).isEqualTo(404));
        when(queries.elderAccess(eq(actor), eq(3L), eq(true), any())).thenReturn(1L);
        assertThatCode(() -> access.elder(actor, 3L, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requireOperator(actor)).isInstanceOf(BusinessException.class);
    }
}
