package com.elderlycare.platform.common.config;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.service.AdminLoginThrottle;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在密码散列校验之前限制请求次数及输入长度，保留上游CSRF验证。 */
final class AdminLoginFilter extends OncePerRequestFilter {
    private final AdminLoginThrottle throttle;
    private final ObjectMapper json;

    /** 装配限流服务与统一错误序列化器。 */
    AdminLoginFilter(AdminLoginThrottle throttle, ObjectMapper json) { this.throttle = throttle; this.json = json; }

    /** 仅对后台表单登录生效，不改变微信登录渠道。 */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(req.getMethod()) && "/api/v1/auth/admin/login".equals(req.getServletPath())) {
            String username = req.getParameter("username"), password = req.getParameter("password");
            if (username == null || username.length() > 64 || password == null || password.length() > 64
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                SecurityConfig.write(json, res, 401, ApiResponse.error("INVALID_CREDENTIALS", "账号或密码错误"));
                return;
            }
            try { throttle.check(req.getRemoteAddr(), username); }
            catch (BusinessException e) {
                SecurityConfig.write(json, res, e.status().value(), ApiResponse.error(e.code(), e.getMessage()));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
