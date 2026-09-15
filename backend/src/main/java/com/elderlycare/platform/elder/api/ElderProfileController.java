package com.elderlycare.platform.elder.api;

import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.elder.api.ElderResponses.*;
import com.elderlycare.platform.elder.service.ElderProfileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elders")
public class ElderProfileController {
    private final ElderProfileService profiles;
    /** 注入老人档案业务服务。 */
    public ElderProfileController(ElderProfileService profiles) { this.profiles = profiles; }

    /** 禁止客户端缓存老人档案响应。 */
    @ModelAttribute
    void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    /** 校验分页和检索参数，返回当前社区档案摘要。 */
    @GetMapping
    public ApiResponse<PageResponse<Summary>> list(Authentication auth, @Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(required = false) Status status) {
        return ApiResponse.ok(profiles.list(auth, page, keyword, status));
    }

    /** 返回当前社区指定档案的详情。 */
    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(Authentication auth, @PathVariable @Min(1) long id) {
        return ApiResponse.ok(profiles.detail(auth, id));
    }

    /** 校验建档请求及嵌套资料，创建或复用已有请求结果。 */
    @PostMapping
    public ApiResponse<Detail> create(Authentication auth, @Valid @RequestBody ElderRequests.Create input) {
        return ApiResponse.ok(profiles.create(auth, input));
    }

    /** 校验版本及完整资料后更新档案。 */
    @PutMapping("/{id}")
    public ApiResponse<Detail> update(Authentication auth, @PathVariable @Min(1) long id,
                                      @Valid @RequestBody ElderRequests.Update input) {
        return ApiResponse.ok(profiles.update(auth, id, input));
    }

    /** 将指定版本的在管档案归档。 */
    @PostMapping("/{id}/archive")
    public ApiResponse<Detail> archive(Authentication auth, @PathVariable @Min(1) long id,
                                       @Valid @RequestBody ElderRequests.Transition input) {
        return ApiResponse.ok(profiles.archive(auth, id, input.version()));
    }

    /** 将指定版本的归档档案恢复为在管。 */
    @PostMapping("/{id}/restore")
    public ApiResponse<Detail> restore(Authentication auth, @PathVariable @Min(1) long id,
                                       @Valid @RequestBody ElderRequests.Transition input) {
        return ApiResponse.ok(profiles.restore(auth, id, input.version()));
    }

    /** 返回指定档案的分页修改历史。 */
    @GetMapping("/{id}/history")
    public ApiResponse<PageResponse<History>> history(Authentication auth, @PathVariable @Min(1) long id,
                                                      @Valid @ModelAttribute PageQuery page) {
        return ApiResponse.ok(profiles.history(auth, id, page));
    }
}
