package com.elderlycare.platform.health.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** health_record 持久化对象，禁止直接作为接口响应。 */
@TableName("health_record")
public class HealthRecordRow {
    @TableId
    public Long id;
    public Long communityId;
    public Long elderId;
    public String type;
    public Long originalActorId;
    public String entryMode;
    public Boolean ownerTakenOver;
    public Long version;
    public String status;
    public Instant measuredAt;
    public String glucoseScene;
    public Instant createdAt;
    public Instant updatedAt;
}

