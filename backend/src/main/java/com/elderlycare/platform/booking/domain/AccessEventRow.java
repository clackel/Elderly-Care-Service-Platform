package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** booking_access_event持久化对象；不得直接作为接口响应。 */
@TableName("booking_access_event")
public class AccessEventRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long actorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long targetId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String action;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant occurredAt;
}

