package com.elderlycare.platform.common.config;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.identity.api.AccountDto;
import com.elderlycare.platform.identity.service.IdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json,
            IdentityService identities, Environment environment) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/actuator/health", "/actuator/health/**", "/api/v1/auth/csrf",
                    "/api/v1/auth/capabilities").permitAll();
            if (dev) {
                auth.requestMatchers("/api/v1/auth/admin/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            }
            auth.requestMatchers("/api/v1/system/**").hasAnyRole("COMMUNITY_OPERATOR", "DUTY_OFFICER", "PLATFORM_ADMIN", "AUDITOR");
            auth.requestMatchers("/api/v1/elders", "/api/v1/elders/**").hasAnyRole("COMMUNITY_OPERATOR", "PLATFORM_ADMIN");
            auth.requestMatchers("/api/v1/**").authenticated();
            auth.anyRequest().denyAll();
        });
        // Keep the default session-backed CSRF repository and BREACH protection.
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, e) -> write(json, res, 401, ApiResponse.error("UNAUTHENTICATED", "请先登录")))
                .accessDeniedHandler((req, res, e) -> write(json, res, 403, ApiResponse.error("FORBIDDEN", "无权操作或安全凭证已失效"))));
        if (dev) {
            http.formLogin(form -> form.loginProcessingUrl("/api/v1/auth/admin/login")
                    .successHandler((req, res, auth) -> write(json, res, 200, ApiResponse.ok(AccountDto.from(identities.find(auth.getName())))))
                    .failureHandler((req, res, e) -> write(json, res, 401, ApiResponse.error("INVALID_CREDENTIALS", "账号或密码错误"))));
        }
        http.logout(logout -> logout.logoutUrl("/api/v1/auth/logout").deleteCookies("ELDERLY_CARE_SESSION")
                .logoutSuccessHandler((req, res, auth) -> write(json, res, 200, ApiResponse.ok(null))));
        http.addFilterAfter(new AccountSessionFilter(identities, json), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    static void write(ObjectMapper json, HttpServletResponse response, int status, Object data) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), data);
    }
}
