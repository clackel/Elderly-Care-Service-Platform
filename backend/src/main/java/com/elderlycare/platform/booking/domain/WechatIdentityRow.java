package com.elderlycare.platform.booking.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** wechat_identity持久化对象；不得直接作为接口响应。 */
@TableName("wechat_identity")
public class WechatIdentityRow {
    @TableId
    public Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String subjectHash;
}

