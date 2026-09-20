package com.elderlycare.platform.catalog.api;

import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.BookingTypes.Category;
import com.elderlycare.platform.catalog.service.ServiceCatalogService;
import com.elderlycare.platform.common.api.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ServiceCatalogController {
    private final ServiceCatalogService service;
    /** 装配服务目录入口。 */
    public ServiceCatalogController(ServiceCatalogService service) { this.service = service; }
    /** 目录和人员范围随会话变化，不允许共享缓存。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    /** 按类别分页查询本社区服务项目。 */
    @GetMapping("/services") public ApiResponse<PageResponse<BookingResponses.Catalog>> list(Authentication a, @Valid PageQuery page,
            @RequestParam(required=false) Category category, @RequestParam(defaultValue="false") boolean available) {
        return ApiResponse.ok(service.list(a, page, category, available));
    }
    /** 查询服务项目说明。 */
    @GetMapping("/services/{id}") public ApiResponse<BookingResponses.Catalog> detail(Authentication a, @PathVariable @Positive long id) { return ApiResponse.ok(service.detail(a, id)); }
    /** 社区创建服务项目。 */
    @PostMapping("/services") public ApiResponse<BookingResponses.Catalog> create(Authentication a, @Valid @RequestBody BookingRequests.Catalog input) { return ApiResponse.ok(service.save(a, null, input)); }
    /** 社区按版本编辑或上下架项目。 */
    @PutMapping("/services/{id}") public ApiResponse<BookingResponses.Catalog> update(Authentication a, @PathVariable @Positive long id, @Valid @RequestBody BookingRequests.Catalog input) { return ApiResponse.ok(service.save(a, id, input)); }
    /** 社区分页查询提供方。 */
    @GetMapping("/service-providers") public ApiResponse<PageResponse<BookingResponses.Provider>> providers(Authentication a, @Valid PageQuery page) { return ApiResponse.ok(service.providers(a, page)); }
    /** 社区幂等创建提供方。 */
    @PostMapping("/service-providers") public ApiResponse<BookingResponses.Provider> createProvider(Authentication a, @Valid @RequestBody BookingRequests.Provider input) { return ApiResponse.ok(service.saveProvider(a, null, input)); }
    /** 社区按版本维护提供方。 */
    @PutMapping("/service-providers/{id}") public ApiResponse<BookingResponses.Provider> updateProvider(Authentication a, @PathVariable @Positive long id, @Valid @RequestBody BookingRequests.Provider input) { return ApiResponse.ok(service.saveProvider(a, id, input)); }
    /** 社区分页查询人员可安排信息。 */
    @GetMapping("/service-workers") public ApiResponse<PageResponse<BookingResponses.Worker>> workers(Authentication a, @Valid PageQuery page) { return ApiResponse.ok(service.workers(a, page)); }
    /** 维护已核验账号的人员能力和可安排状态。 */
    @PutMapping("/service-workers/{id}") public ApiResponse<BookingResponses.Worker> worker(Authentication a, @PathVariable @Positive long id, @Valid @RequestBody BookingRequests.Worker input) { return ApiResponse.ok(service.saveWorker(a, id, input)); }
}
