package com.elderlycare.platform.booking;

import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.domain.Community;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookingAccessTest {
    /** 等待社区锁期间账号被要求改密时，旧请求不得继续处理预约或产生通知。 */
    @Test void writerRechecksIdentityAfterCommunityLock() {
        var identities = mock(CommunityAccess.class); var communities = mock(CommunityMapper.class);
        var access = new BookingAccess(identities, communities, mock(BookingQueries.class), mock(AccessEventMapper.class));
        var before = new UserAccount(); before.setId(1L); before.setCommunityId(2L); before.setRole("COMMUNITY_OPERATOR");
        var after = new UserAccount(); after.setId(1L); after.setCommunityId(2L); after.setRole("COMMUNITY_OPERATOR"); after.setPasswordChangeRequired(true);
        when(identities.current(null)).thenReturn(before, after);
        when(communities.findActiveById(2L)).thenReturn(new Community(2L, "合成社区"));
        when(communities.lockActiveById(2L)).thenReturn(new Community(2L, "合成社区"));
        assertThatThrownBy(() -> access.writer(null)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PASSWORD_CHANGE_REQUIRED");
        verify(identities, times(2)).current(null);
    }

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
