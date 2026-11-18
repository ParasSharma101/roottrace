ALTER TABLE workspace_members ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0);

CREATE TABLE workspace_membership_audit (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    membership_id UUID NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_email VARCHAR(255) NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    target_email VARCHAR(255) NOT NULL,
    action VARCHAR(24) NOT NULL CHECK(action IN ('ROLE_CHANGED','MEMBER_REMOVED','MEMBER_LEFT')),
    previous_role VARCHAR(32) NOT NULL CHECK(previous_role IN ('ROLE_ADMIN','ROLE_SRE','ROLE_VIEWER')),
    new_role VARCHAR(32) CHECK(new_role IN ('ROLE_ADMIN','ROLE_SRE','ROLE_VIEWER')),
    membership_version BIGINT NOT NULL CHECK(membership_version>=0),
    reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
    revoked_invitations INTEGER NOT NULL DEFAULT 0 CHECK(revoked_invitations>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((action='ROLE_CHANGED' AND new_role IS NOT NULL AND new_role<>previous_role)
        OR (action IN ('MEMBER_REMOVED','MEMBER_LEFT') AND new_role IS NULL))
);
CREATE INDEX idx_membership_audit_workspace_created ON workspace_membership_audit(workspace_id,created_at DESC,id DESC);
