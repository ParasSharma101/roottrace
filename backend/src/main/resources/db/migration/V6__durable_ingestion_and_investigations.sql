CREATE TABLE ingestion_submissions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    request_kind VARCHAR(80) NOT NULL,
    idempotency_hash VARCHAR(64),
    payload_hash VARCHAR(64) NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count BETWEEN 1 AND 500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, request_kind, idempotency_hash),
    UNIQUE (id, workspace_id)
);

ALTER TABLE incidents ADD CONSTRAINT uq_incident_workspace UNIQUE (id, workspace_id);

-- PostgreSQL is the durable queue. No external call is made while claiming work.
CREATE TABLE durable_tasks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('PUBLISH', 'INVESTIGATE')),
    submission_id UUID,
    incident_id UUID REFERENCES incidents(id),
    topic VARCHAR(128),
    partition_key VARCHAR(257),
    event_payload TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    claim_token UUID,
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    FOREIGN KEY (incident_id, workspace_id) REFERENCES incidents(id, workspace_id),
    FOREIGN KEY (submission_id, workspace_id) REFERENCES ingestion_submissions(id, workspace_id),
    CHECK ((kind = 'PUBLISH' AND submission_id IS NOT NULL AND incident_id IS NULL AND topic IS NOT NULL AND partition_key IS NOT NULL AND event_payload IS NOT NULL)
        OR (kind = 'INVESTIGATE' AND incident_id IS NOT NULL AND submission_id IS NULL AND topic IS NULL AND partition_key IS NULL AND event_payload IS NULL))
);
CREATE UNIQUE INDEX uq_incident_investigation ON durable_tasks(incident_id) WHERE kind = 'INVESTIGATE';
CREATE INDEX idx_tasks_claim ON durable_tasks(kind, status, available_at, lease_until);
CREATE INDEX idx_tasks_submission ON durable_tasks(workspace_id, submission_id);

CREATE TABLE ingestion_receipts (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    event_id UUID NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, event_id)
);
