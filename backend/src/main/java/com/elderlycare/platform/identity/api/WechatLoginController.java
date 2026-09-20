package com.elderlycare.platform.identity.api;

import com.elderlycare.platform.booking.api.BookingRequests;
import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.identity.service.WechatLoginService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/wechat")
public class WechatLoginController {
    private final WechatLoginService login;
    /** 装配微信身份交换和会话建立入口。 */
    public WechatLoginController(WechatLoginService login) { this.login = login; }
    /** 使用真实微信凭证登录，轮换会话标识和CSRF凭证，并显式保存安全上下文。 */
    @PostMapping("/login")
    public ApiResponse<AccountDto> login(@Valid @RequestBody BookingRequests.Wechat input,
            HttpServletRequest request, HttpServletResponse response) {
        var a = login.login(input.code(), input.enrollmentToken(), request.getRemoteAddr());
        var auth = UsernamePasswordAuthenticationToken.authenticated(a.getUsername(), null,
                AuthorityUtils.createAuthorityList("ROLE_" + a.getRole()));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, request, response);
        new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()).onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(AccountDto.from(a));
    }
}
