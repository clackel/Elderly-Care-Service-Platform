package com.elderlycare.platform.consent.api;

import com.elderlycare.platform.health.domain.HealthTypes.Scope;
import jakarta.validation.constraints.*;
import java.util.UUID;

/** 独立健康同意请求；账号编号由受权人主动提供。 */
public final class HealthConsentRequests {
    private HealthConsentRequests() {}
    public record Resolve(@NotNull @Positive Long elderId, @NotNull @Positive Long recipientId, @NotNull Scope scope) {}
    public record Grant(@NotNull UUID requestId, @NotNull @Positive Long elderId,
            @NotNull @Positive Long recipientId, @NotNull Scope scope, @NotNull Integer days,
            @AssertTrue boolean accepted, @NotBlank String consentVersion) {}
    public record Revoke(@NotNull UUID requestId, @NotNull @Min(0) Long version) {}
}

