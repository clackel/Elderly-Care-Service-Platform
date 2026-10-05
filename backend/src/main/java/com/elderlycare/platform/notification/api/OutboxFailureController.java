package com.elderlycare.platform.notification.api;

import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.notification.service.OutboxFailureService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 通知发送异常只向当前社区管理角色开放。 */
@RestController
@RequestMapping("/api/v1/notifications/deliveries")
public class OutboxFailureController {
    public record Retry(@NotNull @PositiveOrZero Long version) {}
    private final OutboxFailureService service;

    /** 装配发送管理服务，控制器不接触数据库。 */
    public OutboxFailureController(OutboxFailureService service) { this.service = service; }

    /** 禁止浏览器缓存管理处理元数据。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    /** 分页返回本社区已经耗尽自动重试的记录。 */
    @GetMapping public ApiResponse<PageResponse<OutboxFailureService.Failure>> list(Authentication auth, @Valid PageQuery page) {
        return ApiResponse.ok(service.list(auth, page));
    }

    /** 将指定版本重新排队；成功响应只表示接受重试，不代表通知已经送达。 */
    @PostMapping("/{eventId}/retry") public ApiResponse<Void> retry(Authentication auth, @PathVariable long eventId, @Valid @RequestBody Retry input) {
        service.retry(auth, eventId, input.version()); return ApiResponse.ok(null);
    }
}
