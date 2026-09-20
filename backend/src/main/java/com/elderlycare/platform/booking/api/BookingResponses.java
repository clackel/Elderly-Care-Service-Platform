package com.elderlycare.platform.booking.api;

import com.elderlycare.platform.booking.domain.BookingTypes.*;
import java.time.*;
import java.util.*;

public final class BookingResponses {
    private BookingResponses() {}
    public record Provider(String id, String name, boolean enabled, long version) {}
    public record Catalog(String id, Category category, String name, String description, int durationMinutes,
            int priceFen, String serviceArea, String providerId, String providerName, boolean professional,
            String qualification, boolean enabled, long version) {}
    public record Worker(String id, String name, String providerId, Set<Category> categories,
            String serviceArea, String qualification, Instant qualificationExpiresAt, boolean enabled, long version) {}
    public record Summary(String id, String elderId, String serviceName, Category category, Status status,
            Instant requestedStart, Instant scheduledStart, Instant scheduledEnd, boolean hasException, long version) {}
    public record Detail(String id, String elderId, String applicantId, Catalog service,
            BookingRequests.Application application, Status status, String workerId, String workerName,
            Instant scheduledStart, Instant scheduledEnd, Instant startedAt, Instant completedAt,
            boolean hasException, String result, long version, Instant createdAt, Instant updatedAt) {}
    public record Event(String id, String actorId, String action, Status fromStatus, Status toStatus,
            long version, String note, Instant occurredAt) {}
    public record ElderOption(String id, String name, boolean canBook, String address, String contactName, String contactPhone) {}
    public record Member(String id, String displayName, String role, String status, boolean wechatLinked) {}
    public record Enrollment(String accountId, String token, Instant expiresAt) {}
    public record Binding(String id, String accountId, String elderId, boolean active, long version) {}
    public record Grant(String id, String elderId, String familyId, boolean canBook, Instant expiresAt, boolean revoked, long version) {}
    public record Rules(int advanceMinutes, int horizonDays, int cancelBeforeMinutes) {}
}

