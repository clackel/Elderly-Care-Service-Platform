package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** service_worker持久化对象；不得直接作为接口响应。 */
@TableName("service_worker")
public class WorkerRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long providerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String categories;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String serviceArea;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String qualification;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant qualificationExpiresAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
}

