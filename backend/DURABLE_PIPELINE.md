# Durable ingestion and investigation processing

Date: 2026-11-17. Implemented in V6. This replaces the synchronous broker-acknowledgement/AI-listener path.

## Requirements and ownership

The ingestion module owns request identity and consumer receipts. PostgreSQL stores an accepted submission and every outbox event atomically. The infrastructure jobs module owns polling, claims, leases, retries, and completion. The AI module builds fresh workspace-scoped context, calls the provider outside a database transaction, and applies results only inside the worker's fenced completion transaction. Alert consumers create a pending investigation task in the same transaction as the incident, alert, and processing receipt. There is one initial investigation task per incident; no in-memory application event is required to start it.

## HTTP contract

POST log, batch, and alert webhooks accept an optional `Idempotency-Key` header containing 1-128 visible ASCII characters. Identity is scoped to the authenticated workspace and endpoint kind (`LOG`, `LOG_BATCH`, or `ALERT:{source}`). The key is stored as a SHA-256 digest. Repeating a key with the same normalized payload returns the same trackingId, count, and receivedAt, without another outbox insertion. A different payload returns 409. Missing keys create independent submissions. Keep the same key when retrying an uncertain response; use a new key for a distinct intended operation.

Payload hashing recursively sorts object keys and preserves array order, values, and numeric representations. DTO normalization applies first, including parsed timestamps and default alert severity. All batch items are validated before submission. A database failure rolls back the submission and every outbox row, including a failure after an earlier batch item was inserted.

**202 now means the entire request is committed to PostgreSQL for eventual publication.** It does not require Kafka to be available and does not mean evidence persistence or investigation completion. Publication no longer blocks the HTTP request. A database failure returns 503 and cannot return a durable acceptance. This intentionally changes the prior broker-acknowledgement contract.

GET `/api/v1/webhooks/requests/{trackingId}` requires a valid ingestion key or selected ADMIN/SRE session in the owning workspace. Unknown/foreign requests return 404. The response includes count, published, processed, failed, and status:

| Status | Meaning |
| --- | --- |
| PENDING | Some outbox work remains queued or leased. |
| PUBLISHED | All publication tasks have recorded broker acknowledgement; consumer persistence is not complete. |
| PROCESSED | Every event has a committed ingestion receipt and its database effects. This does not include AI completion. |
| FAILED | At least one publication task exhausted its attempts and the request is not fully processed. |

The status is a snapshot; consumer processing can finish before the publisher records success. A quarantined consumer event is not counted as processed and can remain PUBLISHED pending DLQ inspection/replay. Repeating an HTTP idempotency key does not automatically reset a failed task. Idempotency records/receipts have no automatic expiry; retention and operator redrive tooling remain future work.

## Publication and consumer replay

Outbox rows contain immutable version 2 envelopes with workspaceId, eventType, eventId, and payload. The eventId equals the outbox task ID and is reused for every publication attempt. Partition keys remain workspaceId:serviceName. A crash after Kafka accepted a send but before the database records success can publish again; the system does not promise exactly-once network delivery.

Consumers validate the envelope/payload, then acquire a unique (workspaceId,eventId) receipt in their database transaction. The receipt and evidence writes commit together or roll back together. Concurrent/repeated deliveries with identical type/content have no additional database effects. Reusing an event ID with a different type/content routes the conflicting event to DLQ. Log-batch receipt acquisition and saveAll share one transaction. Incident/alert writes and investigation-task creation also share one transaction, with a JPA flush before the JDBC queue insertion.

Version 1 envelopes and historical raw messages remain readable. They have no producer-stable event ID, so consumers derive an identity from topic/partition/offset. Redelivery of the same broker record is suppressed; copying an old event to a new offset cannot be deduplicated as the same original operation. Do not recreate topics with reused offsets against retained legacy receipts without a recovery plan. Raw ownership claims still map only to legacy quarantine. Trusted broker producers and private broker authentication/ACLs/TLS remain production requirements; event IDs are not signed tenant claims.

Transient/resource database failures and inability to start a consumer transaction retry with a one-second backoff instead of going to DLQ during an outage. Other failures retain bounded Kafka retry/recovery. Explicit poison-record sends wait for DLQ acknowledgement, and the recovery publisher is configured to fail when DLQ delivery is not confirmed. Offsets must not be treated as recovered on a failed DLQ send.

## Leases and investigation results

A short PostgreSQL transaction claims one task using FOR UPDATE SKIP LOCKED. Each claim increments attempts, assigns a fresh random claim token, and leases the task for five minutes. Eligible expired leases can be reclaimed by another process. Both publication and investigation tasks stop after five attempts; expired fifth attempts become FAILED. Failure retries are delayed by 10, 20, 40, then 80 seconds. Persisted errors contain exception class/code only, not provider messages, credentials, payloads, or stack traces.

The worker commits the claim before broker/provider I/O. A publication send waits up to ten seconds on its future after Kafka send returns; producer metadata/buffer waits can add time. AI HTTP uses ten-second connect and sixty-second read timeouts. Existing Gemini failure fallback remains: a valid heuristic result can succeed even when Gemini is unavailable. A SUCCEEDED job does not certify use of an external model.

Investigation evaluation always rebuilds scoped context, so a retry does not rely on an old cached bundle. After evaluation, a new transaction locks the current unexpired task claim and the owned incident, applies the complete result, and marks the job SUCCEEDED. Hypothesis/incident/job writes roll back together on error. A replaced or expired claim cannot save results or change replacement job state. A provider call can still repeat after a crash/lease loss; only the committed database result is fenced. No distributed exactly-once provider-call guarantee is claimed.

A human RESOLVED state observed when applying results is preserved; no late AI hypotheses/summary are applied, and the job finishes as SUCCEEDED. Fresh evidence arriving after a completed investigation does not automatically create another investigation; late-evidence refresh and deliberate reinvestigation remain separate features.

GET `/api/v1/incidents/{id}/investigation` uses normal selected-workspace read permissions and returns queue status, attempts, scheduling/lease timestamps, and a safe error code. Foreign/unknown IDs return 404. Historical incidents without a job return NOT_SCHEDULED; V6 does not re-investigate existing incidents or rewrite old hypotheses.

## Scheduling and operations

Scheduling is enabled by default. `roottrace.jobs.scheduling.enabled=false` stops local polling while preserving queued records. Two scheduler threads separate publication from investigation. Publication polls every 500 ms (up to 20 claims per poll); investigation polls every 1000 ms (one claim). Delay properties are roottrace.jobs.publish-delay-ms and roottrace.jobs.investigation-delay-ms. Multiple application processes share PostgreSQL claims safely. This implementation does not yet provide per-workspace fairness, autoscaling, rate quotas, or queue dashboards.

Monitor FAILED tasks, old PENDING tasks, expired RUNNING leases, receipt lag, and DLQs. Terminal rows are retained for investigation, not silently deleted. After diagnosing/fixing a cause, an operator can deliberately requeue an owned FAILED row by resetting status=PENDING, attempts=0, available_at=current_timestamp, completed_at=null, last_error_code=null, claim_token=null, lease_until=null in one transaction with an exact task ID, expected workspace, and status='FAILED' predicate. This is a documented recovery operation, not an action performed by this implementation. An authenticated/audited redrive API and automated retention are still pending. Never change an event ID or delete receipts merely to force replay.

Use a coordinated rollout: migrate V6 and deploy compatible consumers before enabling v2 producers. Old consumers cannot interpret v2, and old synchronous listener nodes bypass job fencing. Do not run mixed versions or roll back application binaries alone. No existing development/production database has been migrated by the tests. Review index creation, queue growth, backfill/locking, and restore/replay behavior on representative data before a production rollout.

## Verification

DurablePipelineIntegrationTest covers concurrent HTTP retry identity and conflict, tenant status denial, atomic rollback after partial batch enqueue, broker failure and replay after uncertain publication, receipt/evidence/job rollback, duplicate log and alert deliveries, expired-worker fencing, preservation of a concurrent human resolution, and full result rollback plus five-attempt exhaustion. Provider callbacks assert no active database transaction. DurablePipelineMigrationIntegrationTest upgrades an independent disposable schema from V5 to V6, preserves an existing incident without enqueuing it, and checks queue ownership/unique-job constraints. Existing tenant, correlation, AI, and Kafka regressions run against the new pipeline. Test scheduling is disabled and workers are ticked deterministically.
