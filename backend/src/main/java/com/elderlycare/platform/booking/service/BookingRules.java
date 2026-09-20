package com.elderlycare.platform.booking.service;

import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.BookingTypes.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Component
public class BookingRules {
    private final BookingResponses.Rules rules;
    /** 校验可配置业务边界，开发默认值不代表运营承诺。 */
    public BookingRules(@Value("${app.booking.advance-minutes:60}") int advance,
            @Value("${app.booking.horizon-days:30}") int horizon,
            @Value("${app.booking.cancel-before-minutes:120}") int cancel) {
        if (advance < 0 || advance > 10080 || horizon < 1 || horizon > 365 || cancel < 0 || cancel > 10080
                || advance >= horizon * 1440) throw new IllegalStateException("预约时间配置无效，请检查提前量、预约范围和取消截止时间");
        rules = new BookingResponses.Rules(advance, horizon, cancel);
    }
    /** 返回前端展示使用的实际规则。 */
    public BookingResponses.Rules view() { return rules; }
    /** 检查期望或安排开始时间，统一使用服务端当前时间。 */
    public void start(Instant start) {
        Instant now = Instant.now();
        if (start.isBefore(now.plusSeconds(rules.advanceMinutes() * 60L)) || start.isAfter(now.plusSeconds(rules.horizonDays() * 86400L)))
            throw error(HttpStatus.BAD_REQUEST, "BOOKING_TIME_INVALID", "服务时间不在可预约范围，请根据预约规则重新选择");
    }
    /** 按类别验证必要结构化资料，并拒绝携带其他类别无关字段。 */
    public void specific(BookingResponses.Catalog service, BookingRequests.Application application) {
        var s = application.specific();
        boolean meal = s.mealDate() != null || s.meal() != null || s.portions() != null || nonempty(s.dietaryRequirements());
        boolean clean = s.cleaningScope() != null;
        boolean escort = nonempty(s.hospital()) || s.appointmentTime() != null || nonempty(s.meetingPoint()) || s.assistance() != null;
        boolean care = s.careContent() != null;
        boolean rehab = s.location() != null || s.assessmentRequired() != null;
        if ((meal && service.category() != Category.MEAL) || (clean && service.category() != Category.CLEANING)
                || (escort && service.category() != Category.ESCORT) || (care && service.category() != Category.CARE)
                || (rehab && service.category() != Category.REHABILITATION)
                || (nonempty(s.precautions()) && !Set.of(Category.CARE, Category.REHABILITATION).contains(service.category())))
            invalid("请清除其他服务类别的无关资料");
        switch (service.category()) {
            case MEAL -> {
                if (s.mealDate() == null || s.meal() == null || s.portions() == null) invalid("请选择用餐日期、餐次和份数");
                if (!application.requestedStart().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(s.mealDate()))
                    invalid("用餐日期须与期望服务时间为同一天");
            }
            case CLEANING -> { if (s.cleaningScope() == null) invalid("请选择清洁范围"); }
            case ESCORT -> {
                if (!nonempty(s.hospital()) || !nonempty(s.meetingPoint()) || s.appointmentTime() == null || s.assistance() == null)
                    invalid("请填写医院、就诊时间、集合地点和行动协助需求");
                if (s.appointmentTime().isBefore(application.requestedStart())
                        || s.appointmentTime().isAfter(application.requestedStart().plusSeconds(service.durationMinutes() * 60L)))
                    invalid("就诊时间须在期望服务时段内");
            }
            case CARE -> {
                if (s.careContent() == null) invalid("请选择照护内容");
                if (service.professional() != (s.careContent() == Care.PROFESSIONAL)) invalid("照护内容须与项目的生活或专业服务类型一致");
            }
            case REHABILITATION -> {
                if (s.location() == null || s.assessmentRequired() == null) invalid("请选择服务地点和是否需要先评估");
            }
        }
    }
    /** 确认或改期不得使助餐日期、陪诊时间与服务安排矛盾。 */
    public void arrangement(BookingRequests.Application app, Category category, Instant start, Instant end) {
        if (category == Category.MEAL && !start.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(app.specific().mealDate()))
            invalid("助餐改期必须仍在原用餐日期内；跨日请取消后重新预约");
        if (category == Category.ESCORT && (app.specific().appointmentTime().isBefore(start) || app.specific().appointmentTime().isAfter(end)))
            invalid("实际服务时段必须覆盖原就诊时间");
    }
    /** 空白可选文本视为未填写。 */
    private static boolean nonempty(String value) { return value != null && !value.isBlank(); }
    /** 使用明确中文参数错误中止处理。 */
    private static void invalid(String message) { throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message); }
}
