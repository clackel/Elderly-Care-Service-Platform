-- 后台账号沿用单角色及社区边界；凭证版本撤销旧会话，业务版本处理编辑冲突。
ALTER TABLE user_account ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_account ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE user_account SET password_change_required = TRUE
WHERE role IN ('COMMUNITY_OPERATOR', 'DUTY_OFFICER', 'PLATFORM_ADMIN', 'AUDITOR');

CREATE TABLE admin_account_event (
 id BIGINT NOT NULL PRIMARY KEY, community_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_admin_event_account FOREIGN KEY(account_id) REFERENCES user_account(id),
 CONSTRAINT fk_admin_event_actor FOREIGN KEY(actor_id) REFERENCES user_account(id)
);
CREATE INDEX idx_admin_event_account ON admin_account_event(community_id, account_id, occurred_at, id);

CREATE TABLE admin_account_mutation_request (
 id BIGINT NOT NULL PRIMARY KEY, community_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 operation VARCHAR(24) NOT NULL, request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 result_id BIGINT NOT NULL, result_version BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_admin_mutation UNIQUE(community_id, actor_id, operation, request_id)
);
