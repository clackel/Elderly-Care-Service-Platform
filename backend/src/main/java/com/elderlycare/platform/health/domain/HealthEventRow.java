package com.elderlycare.platform.health.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** health_event 持久化对象，禁止直接作为接口响应。 */
@TableName("health_event")
public class HealthEventRow {
    @TableId
    public Long id;
    public Long communityId;
    public Long actorId;
    public Long targetId;
    public String action;
    public Instant occurredAt;
}

