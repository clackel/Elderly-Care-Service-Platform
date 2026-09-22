package com.elderlycare.platform.health;

import com.elderlycare.platform.health.api.HealthRequests.Measurement;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import com.elderlycare.platform.health.service.HealthRules;
import com.elderlycare.platform.common.api.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class HealthRulesTest {
    private final HealthRules rules = new HealthRules();
    private final Instant time = Instant.parse("2020-01-01T00:00:00Z");
    /** 血压须成对且不能夹带其他类型字段。 */
    @Test void rejectsMixedOrIncompleteFields() {
        assertThatThrownBy(() -> rules.normalize(new Measurement(Type.BLOOD_PRESSURE, time, 120, null, null, null, null, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> rules.normalize(new Measurement(Type.HEART_RATE, time, null, null, 70, BigDecimal.ONE, null, null))).isInstanceOf(BusinessException.class);
        assertThatCode(() -> rules.normalize(new Measurement(Type.BLOOD_PRESSURE, time, 120, 80, null, null, null, null))).doesNotThrowAnyException();
    }
    /** 小数规范化不改变值，不接受额外精度和未来测量。 */
    @Test void normalizesEquivalentValuesAndRejectsStorageViolations() {
        var result = rules.normalize(new Measurement(Type.WEIGHT, time, null, null, null, new BigDecimal("70.00"), null, null));
        assertThat(result.weight()).isEqualTo(new BigDecimal("7E+1"));
        assertThatThrownBy(() -> rules.normalize(new Measurement(Type.WEIGHT, time, null, null, null, new BigDecimal("70.001"), null, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> rules.normalize(new Measurement(Type.HEART_RATE, Instant.now().plusSeconds(60), null, null, 70, null, null, null))).isInstanceOf(BusinessException.class);
    }
    /** 血糖趋势限定单一场景和最长90天。 */
    @Test void requiresGlucoseSceneAndBoundedRange() {
        assertThatThrownBy(() -> rules.trend(Type.BLOOD_GLUCOSE, null, time, time.plusSeconds(86400))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> rules.trend(Type.WEIGHT, null, time, time.plusSeconds(91L * 86400))).isInstanceOf(BusinessException.class);
        assertThatCode(() -> rules.trend(Type.BLOOD_GLUCOSE, Scene.FASTING, time, time.plusSeconds(90L * 86400))).doesNotThrowAnyException();
    }
}

