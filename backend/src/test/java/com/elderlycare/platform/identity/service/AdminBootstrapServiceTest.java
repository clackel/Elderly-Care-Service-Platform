package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.identity.domain.Community;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {
    private final AdminBootstrapMapper bootstrap = mock(AdminBootstrapMapper.class);
    private final CommunityMapper communities = mock(CommunityMapper.class);
    private final UserAccountMapper accounts = mock(UserAccountMapper.class);
    private final AdminAccountEventMapper events = mock(AdminAccountEventMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AdminBootstrapService service = new AdminBootstrapService(bootstrap, communities, accounts, events, encoder);

    @Test void initializedCommunityNeverOverwritesOrRecreatesAdministrator() {
        when(bootstrap.communityExists(1L)).thenReturn(1);
        when(communities.lockActiveById(1L)).thenReturn(new Community(1L, "合成社区"));
        when(bootstrap.initialized(1L)).thenReturn(1);
        service.initialize(1L, "", "ignored.admin", "忽略的配置", "");
        verifyNoInteractions(accounts, events, encoder);
    }

    @Test void firstAdministratorRequiresPasswordChangeAndIsAudited() {
        when(bootstrap.communityExists(1L)).thenReturn(1);
        when(communities.lockActiveById(1L)).thenReturn(new Community(1L, "合成社区"));
        when(encoder.encode(anyString())).thenReturn("synthetic-hash");
        when(accounts.insert(any(UserAccount.class))).thenReturn(1);
        when(events.insert(any(com.elderlycare.platform.identity.domain.AdminAccountEventRow.class))).thenReturn(1);
        service.initialize(1L, "", " Test.Admin ", "测试管理员", "Synthetic-Password-2026");
        var captured = ArgumentCaptor.forClass(UserAccount.class); verify(accounts).insert(captured.capture());
        assertThat(captured.getValue().getUsername()).isEqualTo("test.admin");
        assertThat(captured.getValue().getRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(captured.getValue().isPasswordChangeRequired()).isTrue();
        verify(events).insert(any(com.elderlycare.platform.identity.domain.AdminAccountEventRow.class));
    }

    @Test void disabledCommunityCannotReceiveAnAdministrator() {
        when(bootstrap.communityExists(1L)).thenReturn(1);
        assertThatThrownBy(() -> service.initialize(1L, "", "test.admin", "测试管理员", "Synthetic-Password-2026"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("停用");
        verifyNoInteractions(accounts, events, encoder);
    }
}
