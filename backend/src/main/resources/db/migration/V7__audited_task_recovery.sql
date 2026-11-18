ALTER TABLE durable_tasks ADD COLUMN total_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE durable_tasks ADD COLUMN redrive_count INTEGER NOT NULL DEFAULT 0;
UPDATE durable_tasks SET total_attempts = attempts;
ALTER TABLE durable_tasks ADD CONSTRAINT chk_task_attempt_history CHECK (total_attempts >= attempts AND redrive_count >= 0);
ALTER TABLE durable_tasks ADD CONSTRAINT uq_task_workspace UNIQUE (id, workspace_id);
CREATE INDEX idx_tasks_workspace_status_created ON durable_tasks(workspace_id, status, created_at DESC, id DESC);

CREATE TABLE durable_task_redrives (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_email VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    cycle_number INTEGER NOT NULL CHECK (cycle_number > 0),
    previous_attempts INTEGER NOT NULL CHECK (previous_attempts BETWEEN 0 AND 5),
    total_attempts_before INTEGER NOT NULL CHECK (total_attempts_before >= previous_attempts),
    previous_error_code VARCHAR(128),
    previous_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id, workspace_id) REFERENCES durable_tasks(id, workspace_id),
    UNIQUE (task_id, cycle_number)
);
CREATE INDEX idx_task_redrives_history ON durable_task_redrives(workspace_id, task_id, created_at DESC, id DESC);
