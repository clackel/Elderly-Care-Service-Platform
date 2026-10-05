-- 通知只保存业务标识及权限快照，不复制老人姓名、地址、电话或事件说明。
ALTER TABLE user_account ADD COLUMN notification_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_booking ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking_grant ADD COLUMN notification_epoch BIGINT NOT NULL DEFAULT 0;

CREATE TABLE notification_outbox (
 event_id BIGINT NOT NULL PRIMARY KEY, community_id BIGINT NOT NULL, booking_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, occurred_at DATETIME(6) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'READY', attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL, lease_until DATETIME(6) NULL, claim_token VARCHAR(36) NULL,
 version BIGINT NOT NULL DEFAULT 0, error_code VARCHAR(40) NULL,
 CONSTRAINT fk_notice_outbox_event FOREIGN KEY(event_id) REFERENCES booking_event(id),
 CONSTRAINT fk_notice_outbox_booking FOREIGN KEY(booking_id) REFERENCES service_booking(id),
 CONSTRAINT ck_notice_outbox_status CHECK(status IN ('READY','IN_FLIGHT','DELIVERED','FAILED'))
);
CREATE INDEX idx_notice_outbox_pending ON notification_outbox(status, next_attempt_at, lease_until);
CREATE INDEX idx_notice_outbox_failed ON notification_outbox(community_id, status, occurred_at);

CREATE TABLE notification_recipient (
 event_id BIGINT NOT NULL, account_id BIGINT NOT NULL, community_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, recipient_role VARCHAR(32) NOT NULL, notification_version BIGINT NOT NULL,
 binding_id BIGINT NULL, binding_version BIGINT NULL, grant_id BIGINT NULL, grant_epoch BIGINT NULL,
 assignment_version BIGINT NULL,
 PRIMARY KEY(event_id, account_id),
 CONSTRAINT fk_notice_recipient_outbox FOREIGN KEY(event_id) REFERENCES notification_outbox(event_id),
 CONSTRAINT fk_notice_recipient_account FOREIGN KEY(account_id) REFERENCES user_account(id)
);
CREATE INDEX idx_notice_recipient_account ON notification_recipient(community_id, account_id, event_id);

CREATE TABLE notification_message (
 id BIGINT NOT NULL PRIMARY KEY, event_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
 community_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, read_at DATETIME(6) NULL,
 CONSTRAINT uk_notice_message UNIQUE(event_id, account_id),
 CONSTRAINT fk_notice_message_recipient FOREIGN KEY(event_id, account_id)
   REFERENCES notification_recipient(event_id, account_id)
);
CREATE INDEX idx_notice_message_account ON notification_message(community_id, account_id, read_at, id);
