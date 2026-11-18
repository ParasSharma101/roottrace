# Workspace task recovery

Date: 2026-11-18. V7 adds audited recovery for terminal durable tasks.

## Rules and module boundaries

A selected, non-legacy workspace ADMIN or SRE session can inspect task metadata/history and retry a FAILED task. Viewers, account-only sessions, and ingestion credentials cannot use these APIs. TaskRecoveryService rechecks current database membership/role for every operation, independently of the controller authorization. Foreign and unknown object/cursor IDs return 404 to an authorized operator. Legacy preservation tasks remain outside this workspace recovery API, including development demos.

The workspace module owns operator authorization and the recovery transaction. The jobs module retains claim, lease, execution, retry, and completion ownership. Recovery creates a new attempt cycle on the existing durable task; it does not create a new submission, incident, broker event ID, or provider result. The existing inbox receipts and claim-token fence remain in force.

Only FAILED tasks can be recovered. Each request requires a nonblank reason of at most 500 characters and the expectedRedriveCount observed when inspecting that task. A stale count or any other task state returns 409. The count remains stale even if another operator already retried the task and it failed again. Concurrent requests using the same count yield a single accepted transition/audit entry; the other request conflicts. An uncertain response should be resolved by refreshing task metadata and audit history before another confirmation.

## API

All endpoints require an ADMIN/SRE bearer session and return Cache-Control: no-store. No payload, partition key, broker envelope, hash, or claim token is exposed.

| Endpoint | Contract |
| --- | --- |
| GET `/api/v1/workspace/tasks` | Default status=FAILED; optional kind=PUBLISH or INVESTIGATE, before cursor, limit=1..100 (default 25). |
| GET `/api/v1/workspace/tasks/{id}` | Current task metadata, ownership-linked incident/submission ID, counters, timestamps, and safe error code. |
| GET `/api/v1/workspace/tasks/{id}/redrives` | Recovery audit history, newest first; before cursor and limit=1..100 supported. |
| POST `/api/v1/workspace/tasks/{id}/redrive` | `{ "reason": "Restored broker credentials", "expectedRedriveCount": 0 }`; 202 with the requeued task snapshot. |

List/history pages return items and nextCursor. Pass nextCursor as before for older results. Task status filters support PENDING, RUNNING, SUCCEEDED, FAILED. Cursor paging uses createdAt/id and checks workspace/task ownership; it is a view of current state rather than a frozen queue snapshot. A concurrent state change can remove a task from a filtered list, so operators should inspect a task again before acting.

Task counters are attempts (current cycle, maximum five), totalAttempts (lifetime count since the V7 baseline), and redriveCount (completed operator recovery transitions). A retry resets attempts to zero, increments redriveCount, preserves totalAttempts, clears the completed/error/lease/claim fields, and makes the task immediately eligible. Automatic claims increment both attempts and totalAttempts. Historical manual resets before V7 cannot be reconstructed from existing rows.

The recovery audit records the current database actor ID/email snapshot, trimmed reason, new cycle number, previous attempt count, lifetime count before recovery, previous error code/completion time, and audit timestamp. Actor/recovery fields in extra request JSON cannot override these server-derived values. Email snapshots support the full 255-character user-email capacity. Deleting an actor account sets its audit foreign key to null while preserving the email/reason snapshot.

## Transaction and database design

V7 adds total_attempts and redrive_count, backfills total_attempts from existing attempts without changing task state/payload, and creates durable_task_redrives. A composite task/workspace foreign key prevents cross-workspace history links; a unique task/cycle constraint prevents duplicate recovery records. Queue/audit indexes support scoped lists and history. Recovery history is append-only through application APIs; this is not a tamper-proof ledger against privileged SQL access. No history update/delete endpoint is provided. Audit rows restrict deletion of their parent task until retention is deliberately designed.

The service authorizes the actor, locks the owned task row, checks FAILED and expectedRedriveCount, inserts the audit snapshot, and resets the task in one database transaction. An audit insertion failure or subsequent task update failure rolls back both changes. Workers cannot claim the requeued task until commit. Clearing the old claim token prevents completion/failure from a stale worker affecting the new cycle.

202 confirms recovery was committed, not that the worker or external provider has completed. The worker may claim the task immediately after commit. Publication retries reuse the frozen envelope/event ID, so existing successful inbox effects are preserved. Investigation retries retain the existing result-write fencing and human-resolution behavior; a human RESOLVED state is not reopened. Retry after diagnosing the cause: each operator recovery can schedule up to five more automatic attempts and may repeat an external publication/provider call.

## Settings workflow

Settings includes Task recovery for ADMIN/SRE sessions, with status/kind filters, cursor pagination, current-cycle/lifetime counts, safe error codes, and task inspection. Inspection loads current metadata and audit history. A failed task shows a reason field and Confirm retry action. Successful recovery shows queued status; Refresh obtains later progress. Failures instruct the operator to refresh metadata/history before trying again. Recovery reasons/history stay in component state and server audit storage, with no new localStorage entries. The UI does not include raw telemetry or secrets.

## Rollout and limits

Apply V7 with a coordinated application upgrade. Older workers increment only attempts and are incompatible with the new lifetime-counter constraint; older operator SQL resets bypass the new audit contract. Preserve V1-V6 migrations. No existing development/production database was migrated by these tests. Existing FAILED tasks remain failed until explicitly recovered.

Use the authenticated recovery API as the normal redrive path. Direct SQL changes bypass actor/reason auditing and concurrency guards. Queue dashboards/alerts, retention policy, per-workspace quotas/fairness, production broker authentication/ACLs/TLS, and DLQ replay tooling remain separate work. This API recovers existing FAILED tasks; it does not replay DLQ records or create an investigation job for a historical incident without one. Production readiness remains incomplete.

## Verification

TaskRecoveryIntegrationTest covers SRE/admin access, viewer/account/key denial, foreign reads/recovery/cursors, live permission changes, validation, immutable metadata redaction, current DB actor snapshots including long emails, audit/history pagination boundaries, concurrent/stale recovery confirmations, preservation of event identity/counters, rejection of stale worker completion, and atomic audit rollback on queue-update failure. TaskRecoveryMigrationIntegrationTest upgrades a separate disposable schema from V6 to V7, verifies state/counter preservation and history constraints, and checks actor deletion preserves attribution snapshots. Existing durable pipeline tests continue to verify execution and replay safety.
