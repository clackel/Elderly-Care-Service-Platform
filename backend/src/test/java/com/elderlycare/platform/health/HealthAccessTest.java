package com.elderlycare.platform.health;

import com.elderlycare.platform.health.mapper.*;
import com.elderlycare.platform.health.service.HealthAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.*;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthAccessTest {
    /** 管理员、工作人员等角色不能因为已有会话获得健康访问权。 */
    @Test void rejectsDefaultAdministrativeAccess() {
        var identities = mock(CommunityAccess.class);
        var access = new HealthAccess(identities, mock(CommunityMapper.class), mock(HealthQueries.class), mock(HealthEventMapper.class));
        var actor = new UserAccount();
        for (String role : new String[]{"PLATFORM_ADMIN", "STAFF", "AUDITOR", "DUTY_OFFICER"}) {
            actor.setRole(role); when(identities.current(null)).thenReturn(actor);
            assertThatThrownBy(() -> access.actor(null)).isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException)e).status().value()).isEqualTo(403));
        }
    }
    /** 对象不可见统一404；家属不能写入、社区不能读取趋势或历史。 */
    @Test void enforcesObjectAndRoleBoundaries() {
        var access = new HealthAccess(mock(CommunityAccess.class), mock(CommunityMapper.class), mock(HealthQueries.class), mock(HealthEventMapper.class));
        var actor = new UserAccount(); actor.setRole("FAMILY");
        assertThatThrownBy(() -> access.record(actor, 1)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException)e).status().value()).isEqualTo(404));
        assertThatThrownBy(() -> access.writable(actor, null)).isInstanceOf(BusinessException.class);
        actor.setRole("COMMUNITY_OPERATOR");
        assertThatThrownBy(() -> access.trend(actor)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> access.owner(actor, 1)).isInstanceOf(BusinessException.class);
    }
}

