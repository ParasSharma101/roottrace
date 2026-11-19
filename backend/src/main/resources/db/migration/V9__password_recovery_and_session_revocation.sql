ALTER TABLE users ADD COLUMN auth_epoch BIGINT NOT NULL DEFAULT 0 CHECK (auth_epoch>=0);

CREATE TABLE revoked_sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_revoked_sessions_expiry ON revoked_sessions(expires_at);

CREATE TABLE password_resets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_password_resets_user_created ON password_resets(user_id,created_at DESC);

-- Peer addresses are keyed hashes, never raw or forwarded-header IPs.
CREATE TABLE recovery_request_limits (
    peer_hash VARCHAR(64) NOT NULL,
    window_start BIGINT NOT NULL,
    attempts INTEGER NOT NULL,
    PRIMARY KEY(peer_hash,window_start)
);
CREATE INDEX idx_recovery_limit_window ON recovery_request_limits(window_start);

-- Public acknowledgement does not look up an account. A separate worker resolves encrypted requests.
CREATE TABLE password_recovery_requests (
    id UUID PRIMARY KEY,
    encrypted_body BYTEA,
    nonce BYTEA,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((encrypted_body IS NULL)=(nonce IS NULL))
);
CREATE INDEX idx_recovery_request_claim ON password_recovery_requests(status,available_at);

ALTER TABLE outbound_emails ADD COLUMN reset_id UUID REFERENCES password_resets(id);
ALTER TABLE outbound_emails DROP CONSTRAINT outbound_emails_purpose_check;
ALTER TABLE outbound_emails ADD CONSTRAINT outbound_emails_purpose_check
    CHECK (purpose IN ('VERIFY_EMAIL','WORKSPACE_INVITE','PASSWORD_RESET','PASSWORD_CHANGED'));
ALTER TABLE outbound_emails DROP CONSTRAINT outbound_emails_check;
ALTER TABLE outbound_emails ADD CONSTRAINT outbound_emails_reference_check CHECK (
    (purpose='VERIFY_EMAIL' AND verification_id IS NOT NULL AND invitation_id IS NULL AND reset_id IS NULL) OR
    (purpose='WORKSPACE_INVITE' AND invitation_id IS NOT NULL AND verification_id IS NULL AND reset_id IS NULL) OR
    (purpose IN ('PASSWORD_RESET','PASSWORD_CHANGED') AND verification_id IS NULL AND invitation_id IS NULL AND reset_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_reset_email_purpose ON outbound_emails(reset_id,purpose) WHERE reset_id IS NOT NULL;
