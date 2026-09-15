package com.elderlycare.platform.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nondev;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "spring.data.redis.password=",
        "spring.rabbitmq.password=",
        "app.elder.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DefaultEnvironmentSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void developmentLoginCannotBeUsedOutsideDevProfile() throws Exception {
        mvc.perform(get("/api/v1/auth/capabilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.developmentLogin").value(false))
                .andExpect(jsonPath("$.data.wechatLogin").value(false));
        mvc.perform(post("/api/v1/auth/admin/login").with(csrf())
                .param("username", "dev.operator").param("password", "any-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsAreNotPublicOutsideDevelopment() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }
}
