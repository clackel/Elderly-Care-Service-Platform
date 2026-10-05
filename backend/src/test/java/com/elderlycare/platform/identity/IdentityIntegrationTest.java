package com.elderlycare.platform.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.elderlycare.platform.identity.service.AdminLoginThrottle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.dev-password=Integration-Only-2026"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @MockitoBean AdminLoginThrottle loginThrottle;

    @BeforeEach
    void resetAccounts() {
        jdbc.update("UPDATE user_account SET status='ACTIVE', role='COMMUNITY_OPERATOR', community_id=10001, password_change_required=FALSE WHERE username='dev.operator'");
        jdbc.update("DELETE FROM user_account WHERE username='second.operator'");
        jdbc.update("DELETE FROM community WHERE id=10002");
        jdbc.update("INSERT INTO community (id,name) VALUES (10002,'另一社区')");
        jdbc.update("INSERT INTO user_account (id,username,password_hash,display_name,role,community_id) VALUES (10002,'second.operator',?,'另一个运营','COMMUNITY_OPERATOR',10002)", encoder.encode("Integration-Only-2026"));
    }

    @Test
    void unauthenticatedRequestHasJsonErrorAndTraceId() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).get("traceId").asText())
                .isEqualTo(response.getHeader("X-Trace-Id"));
    }

    @Test
    void loginRequiresCsrfAndValidCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/admin/login").param("username", "dev.operator")
                .param("password", "Integration-Only-2026")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/admin/login").with(csrf()).param("username", "dev.operator")
                .param("password", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void realCsrfTokenSupportsLoginSessionRotationAndLogout() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        String beforeId = session.getId();
        var token = json.readTree(result.getResponse().getContentAsString()).get("data");
        mvc.perform(post("/api/v1/auth/admin/login").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", "dev.operator").param("password", "Integration-Only-2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("10001"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        assertThat(session.getId()).isNotEqualTo(beforeId);
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
        var refreshed = mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
        var newToken = json.readTree(refreshed.getResponse().getContentAsString()).get("data");
        mvc.perform(post("/api/v1/auth/logout").session(session)
                .header(newToken.get("headerName").asText(), newToken.get("token").asText()))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void communityComesFromAccountAndIgnoresClientSuppliedScope() throws Exception {
        mvc.perform(get("/api/v1/system/community").session(login("dev.operator")).param("communityId", "10002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("10001"));
        mvc.perform(get("/api/v1/system/community").session(login("second.operator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("10002"));
    }

    @Test
    void roleChangeAndAccountDisableApplyToExistingSessionImmediately() throws Exception {
        var session = login("dev.operator");
        jdbc.update("UPDATE user_account SET role='ELDER' WHERE username='dev.operator'");
        mvc.perform(get("/api/v1/system/community").session(session)).andExpect(status().isForbidden());
        jdbc.update("UPDATE user_account SET status='DISABLED' WHERE username='dev.operator'");
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void platformAdminDoesNotImplicitlyBypassCommunityScope() throws Exception {
        var session = login("dev.operator");
        jdbc.update("UPDATE user_account SET role='PLATFORM_ADMIN', community_id=NULL WHERE username='dev.operator'");
        mvc.perform(get("/api/v1/system/community").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMUNITY_REQUIRED"));
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/admin/login").with(csrf())
                .param("username", username).param("password", "Integration-Only-2026"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
