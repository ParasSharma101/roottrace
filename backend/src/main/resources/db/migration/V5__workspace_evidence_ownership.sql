-- Preserve historical evidence in quarantine; service names cannot establish ownership.
ALTER TABLE log_events ADD COLUMN workspace_id UUID;
ALTER TABLE deployments ADD COLUMN workspace_id UUID;

UPDATE log_events SET workspace_id = '00000000-0000-0000-0000-000000000001';
UPDATE deployments SET workspace_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE log_events ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE deployments ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE log_events ADD CONSTRAINT fk_logs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id);
ALTER TABLE deployments ADD CONSTRAINT fk_deployments_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id);

-- No SQL default: older application versions must not silently write unowned rows.
CREATE INDEX idx_logs_workspace_service_time ON log_events(workspace_id, service_name, timestamp DESC);
CREATE INDEX idx_deployments_workspace_service_time ON deployments(workspace_id, service_name, deployment_time DESC);
