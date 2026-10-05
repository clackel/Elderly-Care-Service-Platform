package com.elderlycare.platform.notification.api;

import com.elderlycare.platform.common.api.ApiResponse;
import com.elderlycare.platform.common.api.PageQuery;
import com.elderlycare.platform.common.api.PageResponse;
import com.elderlycare.platform.notification.api.NotificationResponses.*;
import com.elderlycare.platform.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 站内通知和预约待办入口，接收范围由服务端确定。 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService service;

    /** 装配通知服务，控制器不直接读取业务数据库。 */
    public NotificationController(NotificationService service) { this.service = service; }

    /** 返回当前用户消息列表，查询不接受客户端角色或社区参数。 */
    @GetMapping("/notifications")
    public ApiResponse<PageResponse<Notice>> list(Authentication authentication, @Valid PageQuery page,
            @RequestParam(defaultValue = "false") boolean unreadOnly, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.list(authentication, page, unreadOnly));
    }

    /** 返回当前有效消息的未读数，授权外消息不参与计数。 */
    @GetMapping("/notifications/unread-count")
    public ApiResponse<Unread> unread(Authentication authentication, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.unread(authentication));
    }

    /** 幂等标记单条本人消息为已读，不完成或取消任何预约待办。 */
    @PutMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(Authentication authentication, @PathVariable long id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); service.read(authentication, id); return ApiResponse.ok(null);
    }

    /** 返回本社区管理待办或工作人员本人任务，状态取自预约当前值。 */
    @GetMapping("/todos")
    public ApiResponse<PageResponse<Todo>> todos(Authentication authentication, @Valid PageQuery page,
            @RequestParam(required = false) String type, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return ApiResponse.ok(service.todos(authentication, page, type));
    }
}
