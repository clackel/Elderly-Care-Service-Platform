package com.elderlycare.platform.health.api;

import com.elderlycare.platform.health.api.HealthRequests.Measurement;
import java.time.Instant;

/** 健康接口输出，不包含密文、摘要和档案联系人。 */
public final class HealthResponses {
    private HealthResponses() {}
    public record Elder(String id, String name, boolean archived, boolean canWrite, boolean canTrend, boolean canGrant) {}
    public record Mutation(String id, long version) {}
    public record Record(String id, String elderId, Measurement measurement, String unit, String status,
            long version, String originalActorId, String entryMode, boolean ownerTakenOver, boolean corrected,
            Instant createdAt, Instant updatedAt, boolean canWrite, boolean canHistory) {}
    public record Revision(long version, Measurement measurement, String reason, String actorId, String action, Instant occurredAt) {}
    /** 每一版的测量与原因一并加密，作废保留上一版测量。 */
    public record Payload(Measurement measurement, String reason) {
        /** 加密对象不输出健康正文。 */
        @Override public String toString() { return "Payload[REDACTED]"; }
    }
}

