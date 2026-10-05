package com.elderlycare.platform.common.config;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.service.IdentityService;
import com.elderlycare.platform.identity.service.AccountSessions;
import com.elderlycare.platform.common.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 逐请求验证登录版本、账号状态及必须改密状态，旧会话不得随账号恢复复活。 */
final class AccountSessionFilter extends OncePerRequestFilter {
    private final IdentityService identities;
    private final ObjectMapper json;

    /** 装配实时身份读取及统一错误序列化器。 */
    AccountSessionFilter(IdentityService identities, ObjectMapper json) {
        this.identities = identities;
        this.json = json;
    }

    /** 版本不符时销毁本会话；初始或重置密码仅允许改密及身份辅助接口。 */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            UserAccount account = identities.find(authentication.getName());
            try { AccountSessions.requireCurrent(authentication, account); }
            catch (BusinessException e) {
                if (req.getSession(false) != null) { req.getSession(false).invalidate(); }
                SecurityContextHolder.clearContext();
                SecurityConfig.write(json, res, 401, ApiResponse.error(e.code(), e.getMessage()));
                return;
            }
            if (account.isPasswordChangeRequired() && !passwordRoute(req)) {
                SecurityConfig.write(json, res, 403, ApiResponse.error("PASSWORD_CHANGE_REQUIRED", "请先修改初始或重置密码"));
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(AccountSessions.authentication(account));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(req, res);
    }

    /** 改密前仅开放本人改密、退出和恢复身份所需接口，不开放业务读取。 */
    private boolean passwordRoute(HttpServletRequest req) {
        String path = req.getServletPath();
        return ("GET".equals(req.getMethod()) && java.util.Set.of(
                "/api/v1/auth/me", "/api/v1/auth/csrf", "/api/v1/auth/capabilities").contains(path))
                || ("POST".equals(req.getMethod()) && java.util.Set.of(
                "/api/v1/auth/password", "/api/v1/auth/logout").contains(path));
    }
}
