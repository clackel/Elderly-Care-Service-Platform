package com.elderlycare.platform.consent.api;

import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.consent.service.BookingConsentService;
import com.elderlycare.platform.identity.service.MobileMemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/booking-access")
public class BookingConsentController {
    private final BookingConsentService service;
    private final MobileMemberService members;
    /** 装配移动账号核验和预约授权管理入口。 */
    public BookingConsentController(BookingConsentService service, MobileMemberService members) { this.service = service; this.members = members; }
    /** 开通码和授权信息不允许缓存。 */
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    /** 社区分页查看已核验移动账号。 */
    @GetMapping("/members") public ApiResponse<PageResponse<BookingResponses.Member>> members(Authentication a, @Valid PageQuery page) { return ApiResponse.ok(members.list(a, page)); }
    /** 社区核验后幂等创建移动账号。 */
    @PostMapping("/members") public ApiResponse<BookingResponses.Member> member(Authentication a, @Valid @RequestBody BookingRequests.Member input) { return ApiResponse.ok(members.create(a, input)); }
    /** 签发或替换一次性开通码，由社区当面交付被核验人员。 */
    @PostMapping("/members/{id}/enrollment") public ApiResponse<BookingResponses.Enrollment> issue(Authentication a, @PathVariable @Positive long id) { return ApiResponse.ok(members.issue(a, id)); }
    /** 社区分页查看本人核验绑定。 */
    @GetMapping("/bindings") public ApiResponse<PageResponse<BookingResponses.Binding>> bindings(Authentication a, @Valid PageQuery page) { return ApiResponse.ok(service.bindings(a, page)); }
    /** 核验绑定或撤销本人账号的档案访问关系。 */
    @PostMapping("/bindings") public ApiResponse<BookingResponses.Binding> bind(Authentication a, @Valid @RequestBody BookingRequests.Binding input) { return ApiResponse.ok(service.bind(a, input)); }
    /** 分页查看当前权限范围内的家属预约授权。 */
    @GetMapping("/grants") public ApiResponse<PageResponse<BookingResponses.Grant>> grants(Authentication a, @Valid PageQuery page) { return ApiResponse.ok(service.grants(a, page)); }
    /** 明确同意后授予查看预约及可选代预约权限。 */
    @PostMapping("/grants") public ApiResponse<BookingResponses.Grant> grant(Authentication a, @Valid @RequestBody BookingRequests.Grant input) { return ApiResponse.ok(service.grant(a, input)); }
    /** 按版本撤销授权，立即阻止后续家属预约访问。 */
    @PostMapping("/grants/{id}/revoke") public ApiResponse<BookingResponses.Grant> revoke(Authentication a, @PathVariable @Positive long id, @Valid @RequestBody BookingRequests.Version input) { return ApiResponse.ok(service.revoke(a, id, input.version())); }
}
