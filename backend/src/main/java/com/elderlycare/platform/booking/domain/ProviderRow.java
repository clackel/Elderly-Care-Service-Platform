package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** service_provider持久化对象；不得直接作为接口响应。 */
@TableName("service_provider")
public class ProviderRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestHash;
}

