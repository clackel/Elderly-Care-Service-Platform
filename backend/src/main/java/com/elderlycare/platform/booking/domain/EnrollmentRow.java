package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** mobile_enrollment持久化对象；不得直接作为接口响应。 */
@TableName("mobile_enrollment")
public class EnrollmentRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String requestHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String tokenHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant expiresAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean used;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String evidenceCipher;
}

