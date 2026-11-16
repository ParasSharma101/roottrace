-- The V2 default workspace is a preservation container, not a tenant assignment.
ALTER TABLE workspaces ADD COLUMN is_legacy BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE workspaces SET is_legacy = TRUE
WHERE id = '00000000-0000-0000-0000-000000000001';

UPDATE incidents SET workspace_id = '00000000-0000-0000-0000-000000000001'
WHERE workspace_id IS NULL;

-- Nullable ownership remains during the ingestion writer transition. New unassigned
-- records are excluded from tenant API queries; do not silently default new writes.
CREATE INDEX idx_incidents_workspace_start ON incidents(workspace_id, start_time DESC);
