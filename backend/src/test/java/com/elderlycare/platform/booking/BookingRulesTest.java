package com.elderlycare.platform.booking;

import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.BookingTypes.*;
import com.elderlycare.platform.booking.service.BookingRules;
import com.elderlycare.platform.common.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class BookingRulesTest {
    private final BookingRules rules = new BookingRules(60, 30, 120);
    private final Instant start = Instant.parse("2026-10-02T02:00:00Z");

    /** 合成助餐资料验证正常申请、日期边界及跨分类冗余字段。 */
    @Test void mealDateAndCategoryMustMatch() {
        var good = meal(LocalDate.of(2026, 10, 2), null);
        assertThatCode(() -> rules.specific(service(Category.MEAL, false), app(good))).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.specific(service(Category.MEAL, false), app(meal(LocalDate.of(2026, 10, 3), null))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("同一天");
        assertThatThrownBy(() -> rules.specific(service(Category.MEAL, false), app(meal(LocalDate.of(2026, 10, 2), "合成医院"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无关资料");
    }

    /** 专业护理与生活照护不能通过切换客户端字段混用。 */
    @Test void professionalCareRequiresMatchingType() {
        var data = new BookingRequests.Specific(null, null, null, null, null, null, null, null, null,
                Care.DAILY_LIVING, "", null, null);
        assertThatCode(() -> rules.specific(service(Category.CARE, false), app(data))).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.specific(service(Category.CARE, true), app(data)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("类型一致");
    }

    /** 人工调整时段必须覆盖陪诊时刻，不能仅更新安排而破坏原申请。 */
    @Test void escortArrangementMustContainAppointment() {
        var data = new BookingRequests.Specific(null, null, null, null, null, "合成医院",
                start.plusSeconds(1800), "合成集合点", Assistance.NONE, null, null, null, null);
        assertThatCode(() -> rules.arrangement(app(data), Category.ESCORT, start, start.plusSeconds(3600))).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.arrangement(app(data), Category.ESCORT, start.plusSeconds(2000), start.plusSeconds(5600)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("覆盖原就诊时间");
    }

    /** 使用足够裕量检验可预约时间窗，避免精确当前时间带来的不稳定。 */
    @Test void requestedTimeMustRespectConfiguredWindow() {
        assertThatCode(() -> rules.start(Instant.now().plusSeconds(7200))).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.start(Instant.now())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> rules.start(Instant.now().plusSeconds(31L * 86400))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new BookingRules(1440, 1, 120)).isInstanceOf(IllegalStateException.class);
    }

    /** 大整数对象编号在响应和加密载荷中仍使用字符串，防止前端精度丢失。 */
    @Test void applicationIdsRemainStrings() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var data = app(meal(LocalDate.of(2026, 10, 2), null));
        var value = json.readTree(json.writeValueAsString(data));
        assertThat(value.path("elderId").isTextual()).isTrue();
        assertThat(value.path("elderId").asText()).isEqualTo("9007199254740993");
        assertThat(json.readValue(json.writeValueAsString(data), BookingRequests.Application.class)).isEqualTo(data);
    }

    /** 生成不含真实个人信息的最小服务快照。 */
    private BookingResponses.Catalog service(Category category, boolean professional) {
        return new BookingResponses.Catalog("1", category, "合成服务", "合成说明", 60, 100,
                "合成社区", "2", "合成提供方", professional, professional ? "合成资质" : "", true, 0);
    }
    /** 生成固定期望时间的合成申请。 */
    private BookingRequests.Application app(BookingRequests.Specific data) {
        return new BookingRequests.Application(9007199254740993L, 1L, start, "合成地址", "合成联系人", "13900139000", "", data);
    }
    /** 生成助餐分类资料，医院字段仅用于验证跨分类拒绝。 */
    private BookingRequests.Specific meal(LocalDate date, String hospital) {
        return new BookingRequests.Specific(date, Meal.LUNCH, 1, "", null, hospital, null, null, null, null, null, null, null);
    }
}
