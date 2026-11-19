ALTER TABLE users ADD COLUMN verified_email VARCHAR(255);
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT chk_verified_email_pair CHECK ((verified_email IS NULL) = (email_verified_at IS NULL));

ALTER TABLE workspace_invites ADD COLUMN invited_by_email VARCHAR(255);
ALTER TABLE workspace_invites ADD COLUMN accepted_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE workspace_invites ADD COLUMN accepted_at TIMESTAMPTZ;
ALTER TABLE workspace_invites ADD COLUMN revoked_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE workspace_invites ADD COLUMN revoked_at TIMESTAMPTZ;
UPDATE workspace_invites i SET invited_by_email=u.email FROM users u WHERE i.invited_by=u.id;
-- Old plaintext credentials are retired; existing memberships are deliberately preserved.
UPDATE workspace_invites SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP WHERE status='PENDING';
ALTER TABLE workspace_invites DROP COLUMN token;
ALTER TABLE workspace_invites DROP CONSTRAINT chk_invite_status;
ALTER TABLE workspace_invites ADD CONSTRAINT chk_invite_status CHECK (status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED'));
CREATE UNIQUE INDEX uq_pending_invitation ON workspace_invites(workspace_id,email) WHERE status='PENDING';
CREATE INDEX idx_recipient_invites ON workspace_invites(email,status,expires_at);
CREATE INDEX idx_workspace_invites_created ON workspace_invites(workspace_id,created_at DESC,id DESC);

CREATE TABLE email_verifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_active_email_verification ON email_verifications(user_id) WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE TABLE outbound_emails (
    id UUID PRIMARY KEY,
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','WORKSPACE_INVITE')),
    recipient VARCHAR(255) NOT NULL,
    verification_id UUID REFERENCES email_verifications(id),
    invitation_id UUID REFERENCES workspace_invites(id),
    encrypted_body BYTEA,
    nonce BYTEA,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','SENT','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CHECK ((purpose='VERIFY_EMAIL' AND verification_id IS NOT NULL AND invitation_id IS NULL)
        OR (purpose='WORKSPACE_INVITE' AND invitation_id IS NOT NULL AND verification_id IS NULL)),
    CHECK ((encrypted_body IS NULL) = (nonce IS NULL))
);
CREATE UNIQUE INDEX uq_verification_email ON outbound_emails(verification_id) WHERE verification_id IS NOT NULL;
CREATE UNIQUE INDEX uq_invitation_email ON outbound_emails(invitation_id) WHERE invitation_id IS NOT NULL;
CREATE INDEX idx_outbound_email_claim ON outbound_emails(status,available_at,lease_until);
