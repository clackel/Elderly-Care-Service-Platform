package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** service_item持久化对象；不得直接作为接口响应。 */
@TableName("service_item")
public class ServiceRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String category;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer durationMinutes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer priceFen;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String serviceArea;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long providerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean professional;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String qualification;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestHash;
}

