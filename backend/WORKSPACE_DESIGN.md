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
| GET /api/v1/workspace | Selected workspace and members; no fallback. Integration key is returned only to ADMIN. |
| POST /api/v1/workspace/invites | Requires ADMIN in the selected workspace. Invitation acceptance remains separate unfinished work. |

JWTs from the prior email/role format require a new login. The frontend now presents a workspace picker after password login and offers switching from the navbar. A successful switch navigates through a full page load to discard previously mounted data. Other tabs observing a session-token change leave their old screen and return to workspace selection (or login on logout). Account-only dashboard visits redirect to selection.

This step reuses users, workspaces, and workspace_members, whose unique (workspace_id, user_id) constraint already supports multiple memberships. No migration or existing data reassignment occurs in this increment. Checking membership on every request adds database reads; caching authorization is deferred to avoid stale permissions.

## Remaining database and pipeline work

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

The interim alert consumer explicitly quarantines new unscoped alerts in that same legacy container. Its deduplication query is workspace-scoped, so an active incident with the same service name in an application tenant cannot absorb those alerts. This preserves development simulations. Payload workspace fields are not trusted or used to assign ownership; real workspace-owned ingestion is still pending.

Every incident API supplies workspace ID explicitly to IncidentWorkspaceService. Lists use a workspace predicate; detail, timeline, status, notes, hypotheses, and both postmortem representations verify the owning incident first. Foreign/unknown object IDs return 404 before child content is assembled or modified. Hypothesis references must belong to the same incident, including note links. Audit authors come from the authenticated identity; request author fields are optional compatibility fields and are ignored. Status-change audit notes also record the authenticated author.

Logs and deployments still lack tenant ownership. Until the ingestion/correlation migration supplies it, normal workspace timelines and postmortem timeline sections exclude those unscoped records and PRs reached through deployments. Explicitly accessible legacy development demos can still display them. This restriction deliberately avoids presenting globally matched telemetry as tenant-owned evidence. Incident-linked alerts, hypotheses, and notes remain available after the owner check; pre-existing AI text/citations and all background investigation paths still require the following pipeline work.

The V2-to-V3 migration test uses an independent schema in a disposable PostgreSQL database to verify content preservation, legacy backfill, and retention of an explicitly assigned workspace. Cross-workspace API tests cover list/read/write/export denial, child-link validation, trusted audit authors, legacy rejection despite membership, and exclusion of unscoped timeline evidence.

## Explicit remaining limitations

Incident APIs now enforce selected ownership, and the migration-created default workspace is excluded from normal sessions. Unscoped alerts are quarantined and their deduplication is restricted to legacy incidents. Independently stored telemetry, workspace-owned ingestion, background correlation/AI, and caches still lack complete tenant scoping and must be fixed before public multi-tenant use. Logs/deployments have not been assigned to a preservation workspace yet. Invitations still immediately add existing users; acceptance, role management, session revocation, and workspace key lifecycle remain separate work.

## Verification boundary

Integration coverage checks explicit selection, membership-only listing, cross-workspace role differences, membership removal, live role changes, old-session rejection, and administrator-only integration-key responses. Incident API coverage is described above. Whole-pipeline isolation still requires dedicated ingestion/correlation regressions in the following increments.
