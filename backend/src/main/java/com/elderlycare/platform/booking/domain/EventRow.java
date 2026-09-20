package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** booking_event持久化对象；不得直接作为接口响应。 */
@TableName("booking_event")
public class EventRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long bookingId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long actorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String action;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String fromStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String toStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String noteCipher;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant occurredAt;
}

