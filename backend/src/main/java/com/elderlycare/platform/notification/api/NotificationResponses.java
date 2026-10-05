package com.elderlycare.platform.notification.api;

import java.time.Instant;

/** 对外只返回状态元数据，业务详情必须通过原预约权限入口读取。 */
public final class NotificationResponses {
    private NotificationResponses() {}
    public record Notice(String id, String action, String bookingId, Instant occurredAt, boolean read) {}
    public record Unread(long count) {}
    public record Todo(String bookingId, String type, String status, Instant scheduledStart, Instant scheduledEnd,
            boolean hasException, boolean overdue) {}
}
