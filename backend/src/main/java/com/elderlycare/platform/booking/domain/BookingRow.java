package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** service_booking持久化对象；不得直接作为接口响应。 */
@TableName("service_booking")
public class BookingRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long elderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long applicantId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long serviceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String snapshotJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String payloadCipher;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant requestedStart;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant scheduledStart;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant scheduledEnd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long workerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant completedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean hasException;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String resultCipher;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant updatedAt;
}

