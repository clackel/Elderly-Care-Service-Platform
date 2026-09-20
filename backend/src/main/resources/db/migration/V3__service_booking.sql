-- 预约相关写入以 community 行锁串行化，跨实例保护安排、授权及归档。
CREATE TABLE service_provider (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 CONSTRAINT uk_provider_request UNIQUE(community_id, request_id),
 CONSTRAINT fk_provider_community FOREIGN KEY(community_id) REFERENCES community(id)
);
CREATE TABLE service_item (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, category VARCHAR(24) NOT NULL,
 name VARCHAR(100) NOT NULL, description VARCHAR(1000) NOT NULL, duration_minutes INT NOT NULL,
 price_fen INT NOT NULL, service_area VARCHAR(80) NOT NULL, provider_id BIGINT NOT NULL,
 professional BOOLEAN NOT NULL, qualification VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 CONSTRAINT uk_service_request UNIQUE(community_id, request_id),
 CONSTRAINT fk_service_provider FOREIGN KEY(provider_id) REFERENCES service_provider(id),
 CONSTRAINT fk_service_community FOREIGN KEY(community_id) REFERENCES community(id),
 CONSTRAINT ck_service_category CHECK(category IN ('MEAL','CLEANING','ESCORT','CARE','REHABILITATION')),
 CONSTRAINT ck_service_duration CHECK(duration_minutes BETWEEN 15 AND 480),
 CONSTRAINT ck_service_price CHECK(price_fen BETWEEN 0 AND 10000000)
);
CREATE INDEX idx_service_catalog ON service_item(community_id, enabled, category);
CREATE TABLE service_worker (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, provider_id BIGINT NOT NULL,
 categories VARCHAR(150) NOT NULL, service_area VARCHAR(80) NOT NULL,
 qualification VARCHAR(100) NOT NULL, qualification_expires_at TIMESTAMP(6) NULL,
 enabled BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_worker_account FOREIGN KEY(id) REFERENCES user_account(id),
 CONSTRAINT fk_worker_provider FOREIGN KEY(provider_id) REFERENCES service_provider(id),
 CONSTRAINT fk_worker_community FOREIGN KEY(community_id) REFERENCES community(id)
);
CREATE TABLE elder_account_binding (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
 elder_id BIGINT NOT NULL, verified_by BIGINT NOT NULL, evidence_cipher TEXT NOT NULL,
 active BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_binding_account UNIQUE(account_id), CONSTRAINT uk_binding_elder UNIQUE(elder_id),
 CONSTRAINT fk_binding_account FOREIGN KEY(account_id) REFERENCES user_account(id),
 CONSTRAINT fk_binding_elder FOREIGN KEY(elder_id) REFERENCES elder_profile(id)
);
CREATE TABLE booking_grant (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, elder_id BIGINT NOT NULL,
 family_id BIGINT NOT NULL, can_book BOOLEAN NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
 revoked BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 granted_by BIGINT NOT NULL, evidence_cipher TEXT NOT NULL,
 CONSTRAINT uk_booking_grant UNIQUE(elder_id, family_id),
 CONSTRAINT fk_grant_elder FOREIGN KEY(elder_id) REFERENCES elder_profile(id),
 CONSTRAINT fk_grant_family FOREIGN KEY(family_id) REFERENCES user_account(id)
);
CREATE TABLE service_booking (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, elder_id BIGINT NOT NULL,
 applicant_id BIGINT NOT NULL, service_id BIGINT NOT NULL, request_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, snapshot_json TEXT NOT NULL, payload_cipher TEXT NOT NULL,
 requested_start TIMESTAMP(6) NOT NULL, scheduled_start TIMESTAMP(6) NULL, scheduled_end TIMESTAMP(6) NULL,
 worker_id BIGINT NULL, status VARCHAR(24) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 started_at TIMESTAMP(6) NULL, completed_at TIMESTAMP(6) NULL, has_exception BOOLEAN NOT NULL DEFAULT FALSE,
 result_cipher TEXT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_booking_request UNIQUE(community_id, applicant_id, request_id),
 CONSTRAINT fk_booking_elder FOREIGN KEY(elder_id) REFERENCES elder_profile(id),
 CONSTRAINT fk_booking_applicant FOREIGN KEY(applicant_id) REFERENCES user_account(id),
 CONSTRAINT fk_booking_service FOREIGN KEY(service_id) REFERENCES service_item(id),
 CONSTRAINT fk_booking_worker FOREIGN KEY(worker_id) REFERENCES service_worker(id),
 CONSTRAINT ck_booking_status CHECK(status IN ('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','REJECTED','CANCELLED','TERMINATED'))
);
CREATE INDEX idx_booking_list ON service_booking(community_id, status, created_at, id);
CREATE INDEX idx_booking_elder ON service_booking(community_id, elder_id, status);
CREATE INDEX idx_booking_worker ON service_booking(community_id, worker_id, status, scheduled_start, scheduled_end);
CREATE TABLE booking_event (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, booking_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, from_status VARCHAR(24) NOT NULL,
 to_status VARCHAR(24) NOT NULL, version BIGINT NOT NULL, note_cipher TEXT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_event_booking FOREIGN KEY(booking_id) REFERENCES service_booking(id)
);
CREATE INDEX idx_booking_event ON booking_event(booking_id, version, id);
CREATE TABLE booking_access_event (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 target_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE mobile_enrollment (
 id BIGINT PRIMARY KEY, community_id BIGINT NOT NULL, request_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, token_hash VARCHAR(64) NULL,
 expires_at TIMESTAMP(6) NOT NULL, used BOOLEAN NOT NULL, evidence_cipher TEXT NOT NULL,
 CONSTRAINT uk_enrollment_request UNIQUE(community_id, request_id),
 CONSTRAINT uk_enrollment_token UNIQUE(token_hash),
 CONSTRAINT fk_enrollment_account FOREIGN KEY(id) REFERENCES user_account(id)
);
CREATE TABLE wechat_identity (
 id BIGINT PRIMARY KEY, subject_hash VARCHAR(64) NOT NULL,
 CONSTRAINT uk_wechat_subject UNIQUE(subject_hash),
 CONSTRAINT fk_wechat_account FOREIGN KEY(id) REFERENCES user_account(id)
);

