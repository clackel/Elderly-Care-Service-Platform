package com.elderlycare.platform.health.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** health_mutation_request 持久化对象，禁止直接作为接口响应。 */
@TableName("health_mutation_request")
public class HealthMutationRow {
    @TableId
    public Long id;
    public Long communityId;
    public Long actorId;
    public String operation;
    public String requestId;
    public String requestHash;
    public Long resultId;
    public Long resultVersion;
    public Instant createdAt;
}

