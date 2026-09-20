package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** elder_account_binding持久化对象；不得直接作为接口响应。 */
@TableName("elder_account_binding")
public class BindingRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long communityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long accountId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long elderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long verifiedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String evidenceCipher;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean active;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Long version;
}

