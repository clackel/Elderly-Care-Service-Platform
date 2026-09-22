package com.elderlycare.platform.health.api;

import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import com.elderlycare.platform.health.service.HealthRecordService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

/** 健康记录独立入口，身份和事务均在服务层。 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthRecordService service;
    /** 装配健康记录业务服务。 */
    public HealthController(HealthRecordService service) { this.service = service; }
    /** 禁止健康响应进入HTTP缓存。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    /** 分页返回当前健康范围内的最小老人选项。 */
    @GetMapping("/elders") public ApiResponse<PageResponse<HealthResponses.Elder>> elders(Authentication auth, @Valid PageQuery page) {
        return ApiResponse.ok(service.elders(auth, page));
    }
    /** 按测量时间左闭右开筛选当前版本。 */
    @GetMapping("/elders/{id}/records") public ApiResponse<PageResponse<HealthResponses.Record>> records(Authentication auth,
            @PathVariable @Positive long id, @Valid PageQuery page, @RequestParam(required=false) Type type,
            @RequestParam(required=false) Status status, @RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to) {
        return ApiResponse.ok(service.list(auth, id, page, type, status, from, to));
    }
    /** 本人或指定获授权社区账号新增测量。 */
    @PostMapping("/elders/{id}/records") public ApiResponse<HealthResponses.Mutation> create(Authentication auth,
            @PathVariable @Positive long id, @Valid @RequestBody HealthRequests.Create input) {
        return ApiResponse.ok(service.create(auth, id, input));
    }
    /** 返回当前可见详情，正文不包含历史旧值。 */
    @GetMapping("/records/{id}") public ApiResponse<HealthResponses.Record> detail(Authentication auth, @PathVariable @Positive long id) {
        return ApiResponse.ok(service.detail(auth, id));
    }
    /** 携带读取版本追加更正，类型不可改变。 */
    @PostMapping("/records/{id}/corrections") public ApiResponse<HealthResponses.Mutation> correct(Authentication auth,
            @PathVariable @Positive long id, @Valid @RequestBody HealthRequests.Correct input) {
        return ApiResponse.ok(service.correct(auth, id, input));
    }
    /** 携带原因和版本作废，不物理删除。 */
    @PostMapping("/records/{id}/void") public ApiResponse<HealthResponses.Mutation> voidRecord(Authentication auth,
            @PathVariable @Positive long id, @Valid @RequestBody HealthRequests.VoidRecord input) {
        return ApiResponse.ok(service.voidRecord(auth, id, input));
    }
    /** 仅本人分页读取历史版本和原因。 */
    @GetMapping("/records/{id}/revisions") public ApiResponse<PageResponse<HealthResponses.Revision>> revisions(Authentication auth,
            @PathVariable @Positive long id, @Valid PageQuery page) { return ApiResponse.ok(service.history(auth, id, page)); }
    /** 返回有界原始点，血糖按单一场景查询。 */
    @GetMapping("/elders/{id}/trends") public ApiResponse<List<HealthResponses.Record>> trends(Authentication auth,
            @PathVariable @Positive long id, @RequestParam Type type, @RequestParam(required=false) Scene scene,
            @RequestParam Instant from, @RequestParam Instant to) {
        return ApiResponse.ok(service.trends(auth, id, type, scene, from, to));
    }
}

