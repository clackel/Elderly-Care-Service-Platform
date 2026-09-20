package com.elderlycare.platform.identity.api;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final CommunityAccess access;
    private final Environment environment;
    private final com.elderlycare.platform.identity.service.WechatLoginService wechat;

    /** 装配当前身份及真实微信登录配置能力。 */
    public AuthController(CommunityAccess access, Environment environment, com.elderlycare.platform.identity.service.WechatLoginService wechat) {
        this.access = access;
        this.environment = environment;
        this.wechat = wechat;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfDto> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfDto(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/me")
    public ApiResponse<AccountDto> me(Authentication authentication) {
        return ApiResponse.ok(AccountDto.from(access.current(authentication)));
    }

    /** 返回实际配置的登录入口能力，不生成模拟认证身份。 */
    @GetMapping("/capabilities")
    public ApiResponse<CapabilitiesDto> capabilities() {
        return ApiResponse.ok(new CapabilitiesDto(environment.acceptsProfiles(Profiles.of("dev")), wechat.enabled()));
    }

    public record CsrfDto(String headerName, String token) {}
    public record CapabilitiesDto(boolean developmentLogin, boolean wechatLogin) {}
}
