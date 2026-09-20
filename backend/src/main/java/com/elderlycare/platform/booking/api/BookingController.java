package com.elderlycare.platform.booking.api;

import com.elderlycare.platform.booking.domain.BookingTypes.Status;
import com.elderlycare.platform.booking.service.*;
import com.elderlycare.platform.common.api.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService service;
    private final BookingRules rules;
    private final BookingAccess access;
    /** 装配预约入口；身份、事务和数据库访问留在服务层。 */
    public BookingController(BookingService service, BookingRules rules, BookingAccess access) { this.service = service; this.rules = rules; this.access = access; }
    /** 预约和联系人资料禁止HTTP缓存。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    /** 查询当前账号实际可见预约或本人任务。 */
    @GetMapping public ApiResponse<PageResponse<BookingResponses.Summary>> list(Authentication auth, @Valid PageQuery page, @RequestParam(required=false) Status status) {
        return ApiResponse.ok(service.list(auth, page, status));
    }
    /** 返回实际时间规则，供表单和取消提示展示。 */
    @GetMapping("/rules") public ApiResponse<BookingResponses.Rules> rules(Authentication auth) { access.actor(auth); return ApiResponse.ok(rules.view()); }
    /** 返回当前账号的可预约老人及最小必要预填资料。 */
    @GetMapping("/elders") public ApiResponse<List<BookingResponses.ElderOption>> elders(Authentication auth, @Valid PageQuery page) {
        return ApiResponse.ok(service.elderOptions(auth, page));
    }
    /** 幂等提交五类服务申请，成功仅表示待社区确认。 */
    @PostMapping public ApiResponse<BookingResponses.Detail> create(Authentication auth, @Valid @RequestBody BookingRequests.Create input) {
        return ApiResponse.ok(service.create(auth, input));
    }
    /** 读取有权访问的详情与实际处理状态。 */
    @GetMapping("/{id}") public ApiResponse<BookingResponses.Detail> detail(Authentication auth, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.detail(auth, id));
    }
    /** 分页读取不可修改的处理记录。 */
    @GetMapping("/{id}/history") public ApiResponse<PageResponse<BookingResponses.Event>> history(Authentication auth, @PathVariable @Positive long id, @Valid PageQuery page) {
        return ApiResponse.ok(service.history(auth, id, page));
    }
    /** 社区确认或重新安排时间和人员，必须携带当前版本。 */
    @PostMapping("/{id}/arrange") public ApiResponse<BookingResponses.Detail> arrange(Authentication auth, @PathVariable @Positive long id, @Valid @RequestBody BookingRequests.Arrange input) {
        return ApiResponse.ok(service.arrange(auth, id, input));
    }
    /** 处理拒绝、取消、开始、完成、异常、解决和终止，服务层逐动作验证角色。 */
    @PostMapping("/{id}/{action:reject|cancel|start|complete|exception|resolve|terminate}")
    public ApiResponse<BookingResponses.Detail> action(Authentication auth, @PathVariable @Positive long id, @PathVariable String action, @Valid @RequestBody BookingRequests.Action input) {
        return ApiResponse.ok(service.action(auth, id, action, input));
    }
}
