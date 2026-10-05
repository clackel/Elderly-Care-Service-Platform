package com.elderlycare.platform.notification.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 与预约事件同事务保存的发送状态；不包含联系人、健康资料或业务正文。 */
@TableName("notification_outbox")
public class OutboxRow {
    @TableId public Long eventId;
    public Long communityId;
    public Long bookingId;
    public String action;
    public Instant occurredAt;
    public String status;
    public int attempts;
    public Instant nextAttemptAt;
    public Instant leaseUntil;
    public String claimToken;
    public long version;
    public String errorCode;
}
