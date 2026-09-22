-- 健康正文独立加密；所有写操作先锁定 community，再检查绑定、授权及版本。
CREATE TABLE health_record (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, elder_id BIGINT NOT NULL,
 type VARCHAR(24) NOT NULL, original_actor_id BIGINT NOT NULL, entry_mode VARCHAR(24) NOT NULL,
 owner_taken_over BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL, measured_at DATETIME(6) NOT NULL, glucose_scene VARCHAR(24) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_health_record_elder FOREIGN KEY(elder_id) REFERENCES elder_profile(id),
 CONSTRAINT fk_health_record_actor FOREIGN KEY(original_actor_id) REFERENCES user_account(id),
 CONSTRAINT ck_health_record_type CHECK(type IN ('BLOOD_PRESSURE','HEART_RATE','WEIGHT','BLOOD_GLUCOSE')),
 CONSTRAINT ck_health_record_status CHECK(status IN ('ACTIVE','VOID')),
 CONSTRAINT ck_health_record_mode CHECK(entry_mode IN ('SELF','COMMUNITY_ASSIST'))
);
CREATE INDEX idx_health_record_list ON health_record(community_id, elder_id, status, type, measured_at, id);
CREATE INDEX idx_health_record_assist ON health_record(community_id, original_actor_id, elder_id, owner_taken_over);
CREATE TABLE health_record_revision (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, record_id BIGINT NOT NULL,
 version BIGINT NOT NULL, payload_cipher TEXT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(16) NOT NULL, occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_health_revision UNIQUE(record_id, version),
 CONSTRAINT fk_health_revision_record FOREIGN KEY(record_id) REFERENCES health_record(id)
);
CREATE TABLE health_grant (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, elder_id BIGINT NOT NULL,
 recipient_id BIGINT NOT NULL, scope VARCHAR(24) NOT NULL,
 binding_id BIGINT NOT NULL, binding_version BIGINT NOT NULL, granted_by BIGINT NOT NULL,
 consent_version VARCHAR(32) NOT NULL, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 revoked BOOLEAN NOT NULL DEFAULT FALSE, revoked_at DATETIME(6) NULL, revoked_by BIGINT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_health_grant_elder FOREIGN KEY(elder_id) REFERENCES elder_profile(id),
 CONSTRAINT fk_health_grant_recipient FOREIGN KEY(recipient_id) REFERENCES user_account(id),
 CONSTRAINT fk_health_grant_binding FOREIGN KEY(binding_id) REFERENCES elder_account_binding(id),
 CONSTRAINT ck_health_grant_scope CHECK(scope IN ('FAMILY_READ','COMMUNITY_ASSIST'))
);
CREATE INDEX idx_health_grant_access ON health_grant(community_id, elder_id, recipient_id, scope, revoked, expires_at);
CREATE TABLE health_event (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 target_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, occurred_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_health_event_rate ON health_event(community_id, actor_id, action, occurred_at);
CREATE TABLE health_mutation_request (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 operation VARCHAR(24) NOT NULL, request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 result_id BIGINT NOT NULL, result_version BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_health_mutation UNIQUE(community_id, actor_id, operation, request_id)
);

