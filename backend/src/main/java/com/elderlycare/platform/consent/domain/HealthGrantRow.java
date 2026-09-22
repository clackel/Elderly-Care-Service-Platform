package com.elderlycare.platform.consent.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** health_grant 持久化对象，禁止直接作为接口响应。 */
@TableName("health_grant")
public class HealthGrantRow {
    @TableId
    public Long id;
    public Long communityId;
    public Long elderId;
    public Long recipientId;
    public String scope;
    public Long bindingId;
    public Long bindingVersion;
    public Long grantedBy;
    public String consentVersion;
    public Instant createdAt;
    public Instant expiresAt;
    public Boolean revoked;
    public Instant revokedAt;
    public Long revokedBy;
    public Long version;
}

