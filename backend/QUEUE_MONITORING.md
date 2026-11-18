# Workspace queue monitoring

Date: 2026-11-18. Monitoring is sampled on demand and does not send external notifications.

GET `/api/v1/workspace/queue-health` requires a selected, non-legacy ADMIN/SRE session and rechecks current database membership through WorkspaceOperatorAccess. It returns no-store JSON for that workspace only. Viewers, account-only sessions, ingestion keys, and legacy preservation sessions cannot use it. No task IDs, user labels, payloads, claim tokens, or cross-workspace totals are included.

The response has observedAt, pendingOverdueSeconds, two queues (PUBLISH and INVESTIGATE, including empty queues), and signals. Each queue includes pending/running/succeeded/failed counts, ready count, overduePending count, expiredLeases count, and oldestReadyAgeSeconds. These are counts over retained task records, rather than a time-window throughput metric. A single PostgreSQL query computes both queues against one database-clock sample.

| Signal | Meaning and operator response |
| --- | --- |
| FAILED_TASKS | At least one task exhausted its cycle. Inspect failure metadata and use audited recovery after fixing the cause. |
| OVERDUE_PENDING | Ready work has waited at least the configured threshold. Check worker scheduling, resource pressure, and dependencies. |
| EXPIRED_LEASES | Running work has an expired lease. Check worker availability; claims can be recovered under the existing lease policy. |

`roottrace.monitoring.queue.pending-overdue-seconds` defaults to 300 and must be positive. Pending age is measured from available_at, not created_at: intentional retry backoff does not count as overdue work while it is scheduled in the future. Oldest ready age is zero when no ready pending work exists. Failed tasks remain visible until their state is deliberately changed; recovery history is not deleted to clear an indicator.

Settings > Task recovery includes Queue health and a separate Refresh health button. The last observed timestamp is shown. A failed refresh is an error, not an empty healthy snapshot; any displayed earlier result is explicitly labelled as the previous check. There is no automatic browser polling, historical trend storage, worker-heartbeat API, broker connectivity check, consumer/DLQ lag metric, or pager integration in this increment. Absence of signals does not certify broker/provider availability or complete ingestion processing.

The summary reuses existing task data/indexes and introduces no schema migration. Validate query cost on representative large retained queues before production. Retention, alert routing, platform-level scrape credentials, queue fairness/quotas, and sustained-load testing remain separate decisions.

## Management endpoint boundary

Public HTTP access is limited to the default minimal status probes: /actuator/health, /actuator/health/liveness, /actuator/health/readiness. Health details/components are suppressed even for workspace administrators. Other Actuator endpoints and discovery links are denied; workspace ADMIN does not mean platform operator. Endpoint-aware matching keeps this restriction when the Actuator base path changes. Changing that path fails closed for health probes until the explicit probe routes/healthcheck configuration are updated together.

Process health probes do not incorporate workspace queue signals. A service can accept durable submissions while publication is backlogged; it should not remove itself from service merely because one tenant has a failed task. Platform metrics remain inaccessible through these tenant sessions; no anonymous/global Prometheus export was added.

QueueMonitoringIntegrationTest covers scoped counts and ages, intentional retry delays, empty queues, role/credential denial, live role changes, and public diagnostic redaction. ManagementBasePathIntegrationTest checks that relocating management endpoints cannot grant global diagnostic access to workspace administrators.
