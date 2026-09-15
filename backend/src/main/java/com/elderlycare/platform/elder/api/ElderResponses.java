package com.elderlycare.platform.elder.api;

import com.elderlycare.platform.elder.domain.ElderProfileData;
import java.time.Instant;
import java.util.List;

public final class ElderResponses {
    private ElderResponses() {}
    public enum Status { ACTIVE, ARCHIVED }
    public record Detail(String id, String communityId, ElderProfileData profile, Status status,
                         long version, Instant createdAt, Instant updatedAt) {}
    public record Summary(String id, String name, ElderProfileData.Gender gender, int age,
                          String maskedPhone, ElderProfileData.LivingArrangement livingArrangement,
                          Status status, long version, Instant updatedAt) {}
    public record History(String id, String actorId, String action, List<String> changedFields,
                          long version, Instant occurredAt) {}
}
