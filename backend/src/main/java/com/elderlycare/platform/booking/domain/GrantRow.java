package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** booking_grant持久化对象；不得直接作为接口响应。 */
@TableName("booking_grant")
public class GrantRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long elderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long familyId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean canBook;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant expiresAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean revoked;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long grantedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String evidenceCipher;
}

