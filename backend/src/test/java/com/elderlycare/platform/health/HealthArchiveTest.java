package com.elderlycare.platform.health;

import com.elderlycare.platform.elder.service.*;
import com.elderlycare.platform.elder.domain.*;
import com.elderlycare.platform.elder.mapper.*;
import com.elderlycare.platform.identity.service.CommunityAccess;
import com.elderlycare.platform.identity.domain.*;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.booking.mapper.BookingQueries;
import com.elderlycare.platform.consent.mapper.HealthGrantQueries;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HealthArchiveTest {
    /** 归档在社区锁之后撤销第三方健康授权；恢复档案不修改授权事实。 */
    @Test void archiveRevokesUnderCommunityLockButRestoreDoesNotRevive() {
        var access = mock(CommunityAccess.class); var communities = mock(CommunityMapper.class);
        var profiles = mock(ElderProfileMapper.class); var events = mock(ElderAuditMapper.class);
        var grants = mock(HealthGrantQueries.class); var actor = new UserAccount();
        actor.setId(1L); actor.setCommunityId(2L); actor.setRole("COMMUNITY_OPERATOR");
        var row = new ElderProfileRow(); row.setId(3L); row.setCommunityId(2L); row.setStatus("ACTIVE"); row.setVersion(0L);
        when(access.current(null)).thenReturn(actor); when(communities.findActiveById(2L)).thenReturn(new Community(2L, "合成社区"));
        when(communities.lockActiveById(2L)).thenReturn(new Community(2L, "合成社区"));
        when(profiles.find(2L, 3L)).thenReturn(row);
        when(profiles.transition(eq(2L), eq(3L), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(events.insert(any())).thenReturn(1);
        var service = new ElderProfileService(access, communities, profiles, events, mock(ElderCrypto.class), mock(BookingQueries.class), grants);
        service.archive(null, 3, 0);
        var order = inOrder(communities, grants);
        order.verify(communities).lockActiveById(2L);
        order.verify(grants).revokeForArchive(eq(2L), eq(3L), eq(1L), any());
        clearInvocations(grants);
        service.restore(null, 3, 1);
        verifyNoInteractions(grants);
    }
}

