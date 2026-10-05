package com.elderlycare.platform.common.config;

import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.service.AccountSessions;
import com.elderlycare.platform.identity.service.IdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccountSessionFilterTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void restoredAccountDoesNotReviveOldSession() throws Exception {
        var account = account();
        SecurityContextHolder.getContext().setAuthentication(AccountSessions.authentication(account));
        account.setCredentialVersion(2);
        var request = request("GET", "/api/v1/auth/me"); var session = request.getSession();
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter(account).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_REVOKED");
        assertThat(((org.springframework.mock.web.MockHttpSession) session).isInvalid()).isTrue();
        verifyNoInteractions(chain);
    }

    @Test void initialPasswordCannotReadBusinessDataButCanChangePassword() throws Exception {
        var account = account(); account.setPasswordChangeRequired(true);
        SecurityContextHolder.getContext().setAuthentication(AccountSessions.authentication(account));
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter(account).doFilter(request("GET", "/api/v1/admin/accounts"), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
        verifyNoInteractions(chain);
        var passwordRequest = request("POST", "/api/v1/auth/password"); var passwordResponse = new MockHttpServletResponse();
        filter(account).doFilter(passwordRequest, passwordResponse, chain);
        verify(chain).doFilter(passwordRequest, passwordResponse);
    }

    @Test void loginSnapshotRejectsResetThatFinishedDuringPasswordVerification() throws Exception {
        var account = account(); account.setPasswordHash("synthetic-hash");
        var principal = new AccountSessions.LoginUser(account);
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        account.setCredentialVersion(1);
        var response = new MockHttpServletResponse();
        filter(account).doFilter(request("GET", "/api/v1/auth/me"), response, mock(FilterChain.class));
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /** 合成账号仅用于验证请求过滤，不访问数据库。 */
    private UserAccount account() {
        var account = new UserAccount(); account.setId(1L); account.setUsername("test.admin");
        account.setRole("PLATFORM_ADMIN"); account.setStatus("ACTIVE"); account.setCommunityId(1L);
        return account;
    }

    /** 用实时账号快照替代数据访问，保留真实过滤器逻辑。 */
    private AccountSessionFilter filter(UserAccount account) {
        var identities = mock(IdentityService.class); when(identities.find(account.getUsername())).thenReturn(account);
        return new AccountSessionFilter(identities, new ObjectMapper());
    }

    /** 显式设置映射路径，模拟应用实际接收的请求。 */
    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path); request.setServletPath(path); return request;
    }
}
