package com.elderlycare.platform.elder.domain;

import java.time.Instant;

public record ElderAuditRow(Long id, Long elderId, Long communityId, Long actorId, String action,
                            String changedFields, Long version, Instant occurredAt) {}
