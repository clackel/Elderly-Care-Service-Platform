CREATE TABLE elder_profile (
    id BIGINT NOT NULL PRIMARY KEY,
    community_id BIGINT NOT NULL,
    payload_cipher TEXT NOT NULL,
    name_hash VARCHAR(44) NOT NULL,
    phone_hash VARCHAR(44) NULL,
    identity_hash VARCHAR(44) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(44) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_elder_community FOREIGN KEY (community_id) REFERENCES community(id),
    CONSTRAINT uk_elder_request UNIQUE (community_id, request_id),
    CONSTRAINT uk_elder_identity UNIQUE (community_id, identity_hash),
    CONSTRAINT ck_elder_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX idx_elder_page ON elder_profile(community_id, status, created_at, id);
CREATE INDEX idx_elder_name ON elder_profile(community_id, name_hash);
CREATE INDEX idx_elder_phone ON elder_profile(community_id, phone_hash);

CREATE TABLE elder_profile_event (
    id BIGINT NOT NULL PRIMARY KEY,
    elder_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    changed_fields VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_elder_event_profile FOREIGN KEY (elder_id) REFERENCES elder_profile(id),
    CONSTRAINT fk_elder_event_actor FOREIGN KEY (actor_id) REFERENCES user_account(id),
    CONSTRAINT ck_elder_event_action CHECK (action IN ('CREATE','UPDATE','ARCHIVE','RESTORE','VIEW'))
);
CREATE INDEX idx_elder_event_history ON elder_profile_event(community_id, elder_id, version, id);
