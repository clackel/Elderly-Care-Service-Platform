package com.elderlycare.platform.notification.domain;

/** 事件发生时的接收范围快照，不保存个人正文；读取和投递时仍需重新核验。 */
public class RecipientRow {
    public Long eventId;
    public Long accountId;
    public Long communityId;
    public String action;
    public String recipientRole;
    public long notificationVersion;
    public Long bindingId;
    public Long bindingVersion;
    public Long grantId;
    public Long grantEpoch;
    public Long assignmentVersion;
}
