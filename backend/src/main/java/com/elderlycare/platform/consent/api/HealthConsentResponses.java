package com.elderlycare.platform.consent.api;

import java.time.Instant;

/** 授权事实仅输出最少身份及生命周期信息。 */
public final class HealthConsentResponses {
    private HealthConsentResponses() {}
    public record Recipient(String id, String displayName, String role) {}
    public record Grant(String id, String elderId, String recipientId, String scope, Instant createdAt,
            Instant expiresAt, boolean revoked, boolean effective, long version, String consentVersion) {}
}

