package com.elderlycare.platform.identity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 账号写请求只保存专用 HMAC 和原结果，防止重复执行或暴露密码。 */
@TableName("admin_account_mutation_request")
public class AdminAccountMutationRow {
    @TableId public Long id;
    public Long communityId;
    public Long actorId;
    public String operation;
    public String requestId;
    public String requestHash;
    public Long resultId;
    public Long resultVersion;
    public Instant createdAt;
}
