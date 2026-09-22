package com.elderlycare.platform.health.api;

import com.elderlycare.platform.health.domain.HealthTypes.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 固定字段请求；单位和原始来源由服务端确定。 */
public final class HealthRequests {
    private HealthRequests() {}
    public record Measurement(@NotNull Type type, @NotNull Instant measuredAt,
            Integer systolic, Integer diastolic, Integer heartRate,
            BigDecimal weight, BigDecimal glucose, Scene glucoseScene) {
        /** 防止隐式日志输出健康测量正文。 */
        @Override public String toString() { return "Measurement[REDACTED]"; }
    }
    public record Create(@NotNull UUID requestId, @Valid @NotNull Measurement measurement) {}
    public record Correct(@NotNull UUID requestId, @NotNull @Min(1) Long version,
            @Valid @NotNull Measurement measurement, @NotBlank @Size(max=300) String reason) {
        /** 更正原因不进入隐式日志。 */
        @Override public String toString() { return "Correct[REDACTED]"; }
    }
    public record VoidRecord(@NotNull UUID requestId, @NotNull @Min(1) Long version,
            @NotBlank @Size(max=300) String reason) {
        /** 作废原因不进入隐式日志。 */
        @Override public String toString() { return "VoidRecord[REDACTED]"; }
    }
}

