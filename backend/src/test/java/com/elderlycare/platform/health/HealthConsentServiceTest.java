package com.elderlycare.platform.health;

import com.elderlycare.platform.consent.api.HealthConsentRequests;
import com.elderlycare.platform.consent.domain.HealthGrantRow;
import com.elderlycare.platform.consent.mapper.*;
import com.elderlycare.platform.consent.service.HealthConsentService;
import com.elderlycare.platform.health.mapper.HealthQueries;
import com.elderlycare.platform.health.service.*;
import com.elderlycare.platform.health.domain.HealthTypes.Scope;
import com.elderlycare.platform.booking.domain.BindingRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HealthConsentServiceTest {
    /** 授权绑定到当前本人绑定版本，每次新同意保留新的事实。 */
    @Test void grantCapturesCurrentBindingVersionAndSupersedesOldFact() {
        var access = mock(HealthAccess.class); var queries = mock(HealthGrantQueries.class);
        var mapper = mock(HealthGrantMapper.class); var accounts = mock(UserAccountMapper.class);
        var service = new HealthConsentService(access, mock(HealthQueries.class), queries, mapper, accounts, mock(HealthMutations.class));
        var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(2L); actor.setRole("ELDER");
        var family = new UserAccount(); family.setId(4L); family.setCommunityId(2L); family.setRole("FAMILY"); family.setStatus("ACTIVE");
        var binding = new BindingRow(); binding.id = 5L; binding.version = 7L;
        when(access.writer(null)).thenReturn(actor); when(access.owner(actor, 3)).thenReturn(binding);
        when(accounts.selectById(4L)).thenReturn(family);
        service.grant(null, new HealthConsentRequests.Grant(UUID.randomUUID(), 3L, 4L, Scope.FAMILY_READ, 30, true, "health-v1"));
        var captured = ArgumentCaptor.forClass(HealthGrantRow.class); verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().bindingId).isEqualTo(5L);
        assertThat(captured.getValue().bindingVersion).isEqualTo(7L);
        assertThat(captured.getValue().grantedBy).isEqualTo(1L);
        verify(queries).supersede(eq(2L), eq(3L), eq(4L), eq("FAMILY_READ"), eq(1L), any());
    }
    /** 家属和社区人员不能代替老人签署健康授权。 */
    @Test void requiresOwnerBeforeAnyMutation() {
        var access = mock(HealthAccess.class); var mapper = mock(HealthGrantMapper.class); var mutations = mock(HealthMutations.class);
        var service = new HealthConsentService(access, mock(HealthQueries.class), mock(HealthGrantQueries.class), mapper, mock(UserAccountMapper.class), mutations);
        var actor = new UserAccount(); when(access.writer(null)).thenReturn(actor);
        when(access.owner(actor, 3)).thenThrow(HealthAccess.missing());
        assertThatThrownBy(() -> service.grant(null, new HealthConsentRequests.Grant(UUID.randomUUID(), 3L, 4L, Scope.FAMILY_READ, 30, true, "health-v1")))
                .isInstanceOf(com.elderlycare.platform.common.api.BusinessException.class);
        verifyNoInteractions(mapper, mutations);
    }
}

