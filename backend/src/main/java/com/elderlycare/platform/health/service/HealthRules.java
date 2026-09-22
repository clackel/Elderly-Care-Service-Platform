package com.elderlycare.platform.health.service;

import com.elderlycare.platform.health.api.HealthRequests.Measurement;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static com.elderlycare.platform.health.service.HealthAccess.error;

/** 仅验证输入和存储边界，不定义医学正常值。 */
@Component
public class HealthRules {
    public static final int TREND_LIMIT = 2000;
    /** 校验固定字段组合，规范化小数和毫秒时间用于加密及幂等摘要。 */
    public Measurement normalize(Measurement m) {
        if (m == null || m.type() == null || m.measuredAt() == null
                || m.measuredAt().isBefore(Instant.EPOCH) || m.measuredAt().isAfter(Instant.now())) invalid("测量时间须为1970年后且不晚于当前时间");
        boolean bp = m.type() == Type.BLOOD_PRESSURE, hr = m.type() == Type.HEART_RATE;
        boolean weight = m.type() == Type.WEIGHT, glucose = m.type() == Type.BLOOD_GLUCOSE;
        if ((bp != (m.systolic() != null)) || (bp != (m.diastolic() != null)) || (hr != (m.heartRate() != null))
                || (weight != (m.weight() != null)) || (glucose != (m.glucose() != null)) || (glucose != (m.glucoseScene() != null)))
            invalid("请按测量类型填写对应字段，血压须同时填写收缩压和舒张压");
        if (bp) { integer(m.systolic()); integer(m.diastolic()); }
        if (hr) integer(m.heartRate());
        if (weight) decimal(m.weight(), "999.99");
        if (glucose) decimal(m.glucose(), "99.99");
        return new Measurement(m.type(), m.measuredAt().truncatedTo(ChronoUnit.MILLIS), m.systolic(), m.diastolic(),
                m.heartRate(), canonical(m.weight()), canonical(m.glucose()), m.glucoseScene());
    }
    /** 原始点趋势必须指定类型；血糖必须按单一场景查询，范围最长90天。 */
    public void trend(Type type, Scene scene, Instant from, Instant to) {
        if (type == null || from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0)
            invalid("请选择测量类型及不超过90天的有效日期范围");
        if ((type == Type.BLOOD_GLUCOSE) != (scene != null)) invalid("血糖趋势必须选择场景，其他指标不使用血糖场景");
    }
    /** 列表日期使用左闭右开区间，可省略任一边界。 */
    public void range(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) invalid("结束时间须晚于开始时间");
    }
    /** 更正、作废原因去除首尾空白，不接受空理由。 */
    public String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length() > 300) invalid("请填写1至300字的更正或作废原因");
        return reason.strip();
    }
    /** 固定单位仅来自指标类型，不接受客户端自定义来源或单位。 */
    public String unit(Type type) {
        return switch (type) { case BLOOD_PRESSURE -> "mmHg"; case HEART_RATE -> "次/分钟"; case WEIGHT -> "kg"; case BLOOD_GLUCOSE -> "mmol/L"; };
    }
    /** 整数指标限制在存储允许范围，不用于判断健康状态。 */
    private void integer(Integer value) { if (value == null || value < 1 || value > 999) invalid("整数测量值须为1至999"); }
    /** 定点数最多两位小数，不自动四舍五入用户输入。 */
    private void decimal(BigDecimal value, String max) {
        if (value.signum() <= 0 || value.compareTo(new BigDecimal(max)) > 0 || value.stripTrailingZeros().scale() > 2)
            invalid("测量值须为正数、最多两位小数且不超过存储上限");
    }
    /** 统一数值表达，使70、70.0与70.00具有相同请求摘要。 */
    private BigDecimal canonical(BigDecimal value) { return value == null ? null : value.stripTrailingZeros(); }
    /** 生成输入约束异常，不回显测量值。 */
    private static void invalid(String message) { throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message); }
}

