package com.elderlycare.platform.health.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** health_record_revision 持久化对象，禁止直接作为接口响应。 */
@TableName("health_record_revision")
public class HealthRevisionRow {
    @TableId
    public Long id;
    public Long communityId;
    public Long recordId;
    public Long version;
    public String payloadCipher;
    public Long actorId;
    public String action;
    public Instant occurredAt;
}

