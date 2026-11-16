-- ====================================================================
-- RootTrace: Multi-Tenant SaaS Workspaces, Users & RBAC Schema (V2)
-- ====================================================================

-- 1. Workspaces (Tenants)
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL UNIQUE,
    api_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Workspace Members (Role Mapping)
CREATE TABLE workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_SRE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_member_role CHECK (role IN ('ROLE_ADMIN', 'ROLE_SRE', 'ROLE_VIEWER'))
);

-- 4. Workspace Invitations
CREATE TABLE workspace_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_SRE',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    token VARCHAR(128) NOT NULL UNIQUE,
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_invite_role CHECK (role IN ('ROLE_ADMIN', 'ROLE_SRE', 'ROLE_VIEWER')),
    CONSTRAINT chk_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);

-- 5. Multi-Tenant Association on Incidents
ALTER TABLE incidents ADD COLUMN workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE;

-- 6. Seed Default Development Workspace for Backward Compatibility
INSERT INTO workspaces (id, name, slug, api_key)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'RootTrace Cloud Dev',
    'roottrace-dev',
    'roottrace_dev_secret_key'
) ON CONFLICT (id) DO NOTHING;

-- Backfill any existing incidents with the default workspace
UPDATE incidents SET workspace_id = '00000000-0000-0000-0000-000000000001' WHERE workspace_id IS NULL;

-- 7. High-Performance Indexes
CREATE INDEX idx_workspaces_api_key ON workspaces(api_key);
CREATE INDEX idx_workspaces_slug ON workspaces(slug);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);
CREATE INDEX idx_workspace_members_workspace ON workspace_members(workspace_id);
CREATE INDEX idx_workspace_invites_token ON workspace_invites(token);
CREATE INDEX idx_incidents_workspace ON incidents(workspace_id);
