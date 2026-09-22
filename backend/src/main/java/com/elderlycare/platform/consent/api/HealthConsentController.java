package com.elderlycare.platform.consent.api;

import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.consent.service.HealthConsentService;
import com.elderlycare.platform.health.api.HealthResponses.Mutation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 健康同意入口，与预约授权路由完全分离。 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthConsentController {
    private final HealthConsentService service;
    /** 装配独立健康同意服务。 */
    public HealthConsentController(HealthConsentService service) { this.service = service; }
    /** 授权身份信息禁止HTTP缓存。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    /** 分页显示本人授予或当前账号收到的授权事实。 */
    @GetMapping("/grants") public ApiResponse<PageResponse<HealthConsentResponses.Grant>> list(Authentication auth, @Valid PageQuery page) {
        return ApiResponse.ok(service.list(auth, page));
    }
    /** 本人精确核对同社区允许角色账号，不开放成员目录。 */
    @PostMapping("/grant-recipients/resolve") public ApiResponse<HealthConsentResponses.Recipient> resolve(Authentication auth,
            @Valid @RequestBody HealthConsentRequests.Resolve input) { return ApiResponse.ok(service.resolve(auth, input)); }
    /** 在线明确同意后授予限定范围，返回事实编号和版本。 */
    @PostMapping("/grants") public ApiResponse<Mutation> grant(Authentication auth, @Valid @RequestBody HealthConsentRequests.Grant input) {
        return ApiResponse.ok(service.grant(auth, input));
    }
    /** 本人撤销或受权人放弃，不允许管理员代操作。 */
    @PostMapping("/grants/{id}/revoke") public ApiResponse<Mutation> revoke(Authentication auth, @PathVariable @Positive long id,
            @Valid @RequestBody HealthConsentRequests.Revoke input) { return ApiResponse.ok(service.revoke(auth, id, input)); }
}

