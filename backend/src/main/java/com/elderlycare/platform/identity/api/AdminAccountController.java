package com.elderlycare.platform.identity.api;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.common.api.PageQuery;
import com.elderlycare.platform.common.api.PageResponse;
import com.elderlycare.platform.identity.api.AdminAccountResponses.*;
import com.elderlycare.platform.identity.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 后台账号入口只绑定请求，账号权限、事务和幂等语义均由业务层实现。 */
@RestController
@RequestMapping("/api/v1")
public class AdminAccountController {
    private final AdminAccountService service;

    /** 装配后台账号服务，不在控制器直接访问数据库。 */
    public AdminAccountController(AdminAccountService service) { this.service = service; }

    /** 返回当前社区后台账号的安全摘要，支持有界分页和筛选。 */
    @GetMapping("/admin/accounts")
    public ApiResponse<PageResponse<Item>> list(Authentication authentication, @Valid PageQuery page,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(authentication, page, keyword, role, status));
    }

    /** 幂等创建后台账号，仅平台管理员可以指定固定后台角色。 */
    @PostMapping("/admin/accounts")
    public ApiResponse<Mutation> create(Authentication authentication, @Valid @RequestBody AdminAccountRequests.Create input) {
        return ApiResponse.ok(service.create(authentication, input));
    }

    /** 按版本调整名称、角色或状态，社区归属保持创建时值。 */
    @PutMapping("/admin/accounts/{id}")
    public ApiResponse<Mutation> update(Authentication authentication, @PathVariable long id, @Valid @RequestBody AdminAccountRequests.Update input) {
        return ApiResponse.ok(service.update(authentication, id, input));
    }

    /** 重置其他后台账号密码，返回结果不包含密码或密码哈希。 */
    @PostMapping("/admin/accounts/{id}/reset-password")
    public ApiResponse<Mutation> resetPassword(Authentication authentication, @PathVariable long id,
            @Valid @RequestBody AdminAccountRequests.ResetPassword input) {
        return ApiResponse.ok(service.resetPassword(authentication, id, input));
    }

    /** 返回有权管理账号的操作元数据，不暴露密码、健康信息或请求摘要。 */
    @GetMapping("/admin/accounts/{id}/events")
    public ApiResponse<PageResponse<Event>> events(Authentication authentication, @PathVariable long id, @Valid PageQuery page) {
        return ApiResponse.ok(service.events(authentication, id, page));
    }

    /** 改密事务完成后销毁当前会话，全部旧会话由凭证版本撤销，客户端须重新登录。 */
    @PostMapping("/auth/password")
    public ApiResponse<Void> changePassword(Authentication authentication, @Valid @RequestBody AdminAccountRequests.ChangePassword input,
            HttpServletRequest request, HttpServletResponse response) {
        service.changePassword(authentication, input);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(null);
    }
}
