# Workspace ownership and authentication

Date: 2026-11-17. Status: business rules accepted; implementation is incremental.

## Agreed rules

- A user may belong to multiple workspaces. Each session token selects at most one workspace.
- Password login establishes account identity; the user explicitly selects a workspace. Signup selects the workspace just created. Local demo login selects its explicitly configured demo workspace.
- Roles belong to memberships, not users globally. Current membership and role must be checked on each authenticated request. No default-workspace or first-membership fallback is allowed.
- Non-members must not distinguish an existing workspace from an unknown ID through the selection API. Selection returns 404 for both. A permitted workspace action rejected by role returns 403.
- A workspace owns its incident and telemetry data. Matching service names in separate workspaces must never combine evidence or deduplicate incidents.
- Ingestion credentials determine ownership; client payload fields cannot grant or override it. Kafka events, jobs, correlation queries, and cache keys must carry workspace identity.
- Preserve prototype data with uncertain ownership in an isolated legacy workspace. New users receive no automatic legacy membership/access. Do not infer ownership from service names or silently assign this data to a newly registered user.

## Implemented increment: account and workspace sessions

The security module issues version 2 JWTs with a stable user ID as subject and an optional selected workspace ID. Roles are not trusted from JWT claims. A transactional authentication service loads the user and selected membership on every request, then constructs an immutable WorkspaceIdentity. Unknown users and missing memberships cannot authenticate a selected-workspace token. Database failures are not disguised as invalid credentials.

Account-only tokens can use authenticated account endpoints, including workspace listing, selection, and profile. They have no workspace roles and cannot use workspace/incident endpoints. Selection issues a token for a membership owned by the authenticated user. Switching does not revoke independently issued sessions; each token retains its own selected workspace and must pass membership validation on use.

| Endpoint | Contract |
| --- | --- |
| POST /api/v1/auth/login | Password login; account-only token, null workspace fields, empty roles. |
| GET /api/v1/auth/workspaces | Only the current user's memberships; no integration keys. |
| POST /api/v1/auth/select-workspace | Validated workspaceId; token/profile for that membership, or 404. |
| GET /api/v1/auth/me | Current account and selected workspace; current role. |
| GET /api/v1/workspace | Selected workspace and members; no fallback. Legacy apiKey field is always null; named key management is ADMIN-only. |
| POST /api/v1/workspace/invites | Requires ADMIN in the selected workspace. Creates a pending invitation; verified matching-email acceptance is required. See EMAIL_INVITATIONS.md. |

JWTs from the prior email/role format require a new login. The frontend now presents a workspace picker after password login and offers switching from the navbar. A successful switch navigates through a full page load to discard previously mounted data. Other tabs observing a session-token change leave their old screen and return to workspace selection (or login on logout). Account-only dashboard visits redirect to selection.

This step reuses users, workspaces, and workspace_members, whose unique (workspace_id, user_id) constraint already supports multiple memberships. No migration or existing data reassignment occurs in this increment. Checking membership on every request adds database reads; caching authorization is deferred to avoid stale permissions.

## Database and pipeline rollout plan

1. Extend the legacy boundary described below to independently stored telemetry and unscoped queued events. Normal signup, membership management, and credential issuance must not grant automatic legacy access.
2. Add workspace ownership to independently ingested roots: alerts, log events, and deployments. Make incident ownership mandatory once all writers supply it. Backfill unknown/untrusted prototype ownership into the legacy container, preserving records. Existing V2 backfills are not proof of ownership.
3. Scope repository queries and indexes by workspace, service, and time. Use workspace plus event identity for deduplication/idempotency.
4. Enforce same-workspace parent relationships with appropriate composite unique keys/foreign keys. Notes and hypotheses inherit ownership from their incident; linked hypotheses must belong to that same incident. PR/deployment ownership must remain consistent, with an explicit preservation path for existing orphan records.
5. Resolve workspace ingestion credentials server-side and propagate ownership through the Kafka envelope before changing consumer writes. Do not accept old unscoped events as new tenant data.
6. Scope investigation jobs, Redis context/GitHub keys, evidence bundles, timeline generation, and exports. Reject cross-workspace object access before assembling content.
7. Test upgrades from existing schema/data, fresh installs, and two workspaces with identical service names and overlapping event times. Then verify all read/write/export paths deny cross-workspace access.

Use forward migrations; preserve V1/V2. Migration order must keep writes and constraints consistent. Do not impose NOT NULL constraints before ingestion/writers and fixtures supply ownership. No production data deletion or transfer is authorized by this plan.

## Implemented increment: incident API and legacy boundary

V3 marks the existing V2 default workspace (`00000000-0000-0000-0000-000000000001`) as `is_legacy=true` and moves currently unassigned incidents into it. Reusing that preservation container keeps existing foreign keys intact. Explicit assignments to other workspaces remain unchanged. Records and content are preserved. A workspace/start-time index supports scoped lists. Incident ownership remains nullable until all ingestion writers can supply trustworthy ownership; later unassigned writes are invisible to workspace API queries, rather than silently assigned to a tenant.

The workspace access policy excludes legacy memberships from listing and selection outside explicit `dev & !prod`, and rejects pre-existing selected legacy tokens. Creating a new workspace never sets the legacy flag. Development demos retain access to their existing legacy container. Even a development emergency header cannot bypass the selected-workspace identity required by incident APIs.

The alert consumer quarantines historical raw/unscoped alerts in that same legacy container. Its deduplication query is workspace-scoped, so an active incident with the same service name in an application tenant cannot absorb those alerts. This preserves development simulations. Payload workspace fields are not trusted or used to assign ownership. Named-key webhooks now supply ownership through a server-created envelope; see INGESTION_KEYS.md.

Every incident API supplies workspace ID explicitly to IncidentWorkspaceService. Lists use a workspace predicate; detail, timeline, status, notes, hypotheses, and both postmortem representations verify the owning incident first. Foreign/unknown object IDs return 404 before child content is assembled or modified. Hypothesis references must belong to the same incident, including note links. Audit authors come from the authenticated identity; request author fields are optional compatibility fields and are ignored. Status-change audit notes also record the authenticated author.

The initial incident boundary excluded unscoped logs/deployments from normal workspace timelines. V5 now gives those records explicit ownership and replaces that temporary suppression with workspace-scoped queries, as described below. Incident-linked alerts, hypotheses, and notes remain available after the owner check. Previously persisted AI summaries/citations are not rewritten by this migration and need a separate provenance review before a production data rollout.

The V2-to-V3 migration test uses an independent schema in a disposable PostgreSQL database to verify content preservation, legacy backfill, and retention of an explicitly assigned workspace. Cross-workspace API tests cover list/read/write/export denial, child-link validation, trusted audit authors, legacy rejection despite membership, and exclusion of unscoped timeline evidence.

## Explicit remaining limitations

Named key management now supports multiple hashed credentials per workspace with one-time disclosure and independent revocation; old plaintext workspace keys are retired by V4. See [INGESTION_KEYS.md](INGESTION_KEYS.md). These credentials now authenticate log/alert webhooks through the tenant-aware event/consumer path.

Incident APIs enforce selected ownership, and the migration-created legacy workspace is excluded from normal sessions. Unscoped alerts are quarantined and their deduplication is restricted to legacy incidents. Logs/deployments, context correlation, timeline/export queries, and Redis incident contexts now carry workspace scope. Authenticated ownership now propagates through webhook-generated versioned Kafka envelopes and consumers. The shared global credential and historical raw events remain legacy-only. Broker isolation/producer ACLs are prerequisites because envelope metadata is trusted. See INGESTION_KEYS.md for acknowledgement, revocation, retry, and rollout semantics. GitHubService is currently unused by production callers and still requires tenant/repository-scoped caching and tenant-specific integration credentials before connecting it. V8 replaces immediate membership grants with pending invitations and verified matching-email acceptance; see EMAIL_INVITATIONS.md. Role management, session revocation, and further key lifecycle controls remain separate work. Public multi-tenant use is not ready.

## Verification boundary

Integration coverage checks explicit selection, membership-only listing, cross-workspace role differences, membership removal, live role changes, old-session rejection, and administrator-only integration-key responses. Incident API coverage is described above. Evidence/correlation isolation coverage is described below. TenantIngestionIntegrationTest now covers authenticated log/alert ingestion through Kafka, persistence, correlation, AI summary, and Redis. Public deployment, certificate lifecycle, deployment ingestion, historical provenance review, and broader operational hardening remain outside this verification; self-hosted broker checks are described in KAFKA_SECURITY.md. Durable delivery and investigation tests are described in DURABLE_PIPELINE.md.

## Implemented increment: evidence and incident-context ownership

V5 adds mandatory workspace foreign keys to log_events and deployments, backfills all historical rows into the legacy preservation workspace, and adds workspace/service/time indexes. It preserves log content, deployment metadata, IDs, and deployment-linked PRs. SQL has no default ownership; inserts without a workspace fail. Existing application constructors explicitly initialize unscoped telemetry to legacy quarantine until authenticated writers replace that value. Ownership is not updated through these JPA entities after insertion. This is not an authorization mechanism for arbitrary database writers.

Correlation loads the incident owner and scopes both telemetry queries by that workspace, service, and time window. A missing incident owner stops context construction and AI investigation; it never triggers global or default-tenant lookup. PRs are reached only through deployments selected by the scoped query. IncidentContextBundle includes workspaceId. Timeline generation and postmortem timeline sections use the same workspace predicates, including for legacy demos. Thus quarantine cannot read normal tenant telemetry either.

Redis incident contexts use `context:v2:workspace:{workspaceId}:incident:{incidentId}:context`. Lookup and eviction require both IDs, and a cached bundle must match both embedded identities. Old `incident:{id}:context` entries are ignored and expire under their existing TTL; no fallback reads or bulk Redis deletion are performed. AI derives the cache workspace from the persisted incident, never the request payload. This does not make cached evidence fresh after late-arriving telemetry; invalidation/reinvestigation policy remains future work.

Use a coordinated rollout with writers stopped/drained: old application versions cannot insert logs/deployments after V5 because they omit mandatory ownership, and old nodes still contain unsafe global correlation reads. Do not mix old/new versions or roll back only application binaries. Review large-table backfill/locking on representative data before production. No live database migration has been performed by this work.

EvidenceTenantIsolationIntegrationTest verifies same-service overlapping tenant/quarantine evidence, scoped context including linked PRs, timeline/export inclusion and exclusion, reverse legacy isolation, and refusal of unassigned incidents. EvidenceOwnershipMigrationIntegrationTest upgrades V4 to V5 in a separate disposable schema, checks content/PR preservation and mandatory valid ownership. Redis integration coverage checks workspace isolation, identity mismatch rejection, scoped eviction, and bypass of old global keys.

## Implemented increment: authenticated log/alert ingestion

Named keys resolve to a typed workspace ingestion principal. Selected ADMIN/SRE sessions may also ingest; mixed key and bearer headers return 400. Controllers normalize payloads and create eventVersion=1 envelopes with eventType and workspaceId derived from authentication. Kafka partition keys include workspace and service. Consumers validate envelopes and scope incident deduplication/creation and log persistence to the decoded owner. Raw old-format messages never establish tenant ownership, even with a workspaceId field. Keys are checked on each new request, while accepted events retain their original ownership after revocation.

V6 now adds durable submissions, outbox/investigation tasks, and processing receipts, replacing the earlier broker-acknowledgement path. Alerts remain incident-owned children. Incident ownership is still nullable for the transition; mandatory incident/alert-link constraints and cleanup of unassigned historical roots are deferred. A 202 acknowledges committed database queuing; broker delivery and AI completion happen later. See INGESTION_KEYS.md for the complete contract and remaining production boundaries.

## Implemented increment: durable pipeline and replay identity

See [DURABLE_PIPELINE.md](DURABLE_PIPELINE.md) for the full design. Submission identity is scoped by workspace and endpoint; event receipts are scoped by workspace and stable event ID. V6 composite foreign keys prevent tasks referencing an incident/submission in a different workspace. Investigation work commits with incident/alert creation, and claim-token fencing makes result application and job completion atomic. Provider calls run outside database transactions. Old incidents are not automatically queued or rewritten. Self-hosted broker TLS/ACL and scoped queue summaries are now implemented as described below. Production rollout, certificate lifecycle, retention/alert routing, late evidence, tenant integrations, and remaining account/workspace hardening are still pending.

## Implemented increment: workspace operator recovery

V7 adds recovery history and lifetime counters without changing existing task identity or state. Current ADMIN/SRE memberships can inspect scoped task metadata and recover only FAILED work with a reason and expected recovery count. The task lock, audit record, and queue reset share one transaction; stale confirmations conflict and claim fencing remains unchanged. Task/actor snapshots and the Settings flow are documented in [TASK_RECOVERY.md](TASK_RECOVERY.md). Legacy preservation work is excluded from this API. Queue monitoring and self-hosted broker security configuration are described below; retention, public deployment, certificate lifecycle and other SaaS work remain pending.

## Implemented increment: scoped monitoring and self-hosted broker isolation

Workspace operator authorization is shared by task recovery and on-demand queue summaries. Counts/age/lease signals do not expose task content or other workspace totals, and global Actuator diagnostics are denied to tenant roles. Self-hosted Compose now uses private data networking, required client TLS identity, a privileged one-shot bootstrap separate from the regular backend, and broker ACLs. See QUEUE_MONITORING.md and KAFKA_SECURITY.md for verification/rollout limits. No existing stack or volume has been upgraded by these tests.
