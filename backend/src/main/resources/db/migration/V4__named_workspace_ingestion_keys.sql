CREATE TABLE workspace_ingestion_keys (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    key_prefix VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_ingestion_key_hash CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ingestion_key_name CHECK (length(btrim(name)) > 0)
);

CREATE INDEX idx_ingestion_keys_workspace_created ON workspace_ingestion_keys(workspace_id, created_at DESC);

-- Preserve existing non-legacy credentials without retaining their plaintext.
-- PostgreSQL 16 provides SHA-256 directly; no extension is required.
INSERT INTO workspace_ingestion_keys(id, workspace_id, name, key_hash, key_prefix, created_at)
SELECT gen_random_uuid(), id, 'Imported workspace key',
       encode(sha256(convert_to(api_key, 'UTF8')), 'hex'), left(api_key, 12), created_at
FROM workspaces
WHERE NOT is_legacy AND length(api_key) >= 32 AND api_key NOT LIKE 'retired\_%' ESCAPE '\';

-- Keep the old NOT NULL/unique column temporarily for schema compatibility.
-- Its contents are now inert placeholders, never credentials or API responses.
UPDATE workspaces SET api_key = 'retired_' || replace(gen_random_uuid()::text, '-', '');
