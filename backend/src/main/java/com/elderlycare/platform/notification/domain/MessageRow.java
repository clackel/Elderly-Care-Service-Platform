package com.elderlycare.platform.notification.domain;

import java.time.Instant;

/** 已通过通知权限查询得到的安全投影，转交任务不携带业务入口。 */
public class MessageRow {
    public Long id;
    public String action;
    public Long bookingId;
    public Instant occurredAt;
    public Instant readAt;
}
