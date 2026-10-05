package com.elderlycare.platform.identity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 账号操作元数据，禁止保存密码、密码摘要及其他凭据正文。 */
@TableName("admin_account_event")
public class AdminAccountEventRow {
    @TableId public Long id;
    public Long communityId;
    public Long accountId;
    public Long actorId;
    public String action;
    public Instant occurredAt;
}
