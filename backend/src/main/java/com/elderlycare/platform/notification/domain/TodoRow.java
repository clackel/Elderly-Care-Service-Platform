package com.elderlycare.platform.notification.domain;

import java.time.Instant;

/** 待办直接由当前预约状态派生，不建立可独立完成的第二套状态。 */
public class TodoRow {
    public Long bookingId;
    public String todoType;
    public String status;
    public Instant scheduledStart;
    public Instant scheduledEnd;
    public boolean hasException;
    public boolean overdue;
}
