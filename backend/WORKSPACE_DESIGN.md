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

## Database and pipeline plan — not yet implemented

1. Introduce a reserved legacy workspace that normal signup, membership management, and credential issuance cannot access automatically. Define its enforced system status before migrating existing rows.
2. Add workspace ownership to independently ingested roots: alerts, log events, and deployments. Make incident ownership mandatory once all writers supply it. Backfill unknown/untrusted prototype ownership into the legacy container, preserving records. Existing V2 backfills are not proof of ownership.
3. Scope repository queries and indexes by workspace, service, and time. Use workspace plus event identity for deduplication/idempotency.
4. Enforce same-workspace parent relationships with appropriate composite unique keys/foreign keys. Notes and hypotheses inherit ownership from their incident; linked hypotheses must belong to that same incident. PR/deployment ownership must remain consistent, with an explicit preservation path for existing orphan records.
5. Resolve workspace ingestion credentials server-side and propagate ownership through the Kafka envelope before changing consumer writes. Do not accept old unscoped events as new tenant data.
6. Scope investigation jobs, Redis context/GitHub keys, evidence bundles, timeline generation, and exports. Reject cross-workspace object access before assembling content.
7. Test upgrades from existing schema/data, fresh installs, and two workspaces with identical service names and overlapping event times. Then verify all read/write/export paths deny cross-workspace access.

Use forward migrations; preserve V1/V2. Migration order must keep writes and constraints consistent. Do not impose NOT NULL constraints before ingestion/writers and fixtures supply ownership. No production data deletion or transfer is authorized by this plan.

## Explicit remaining limitations

Incident repositories, telemetry, ingestion, correlation, and caches still lack complete tenant scoping. The existing migration-created default workspace has not yet been quarantined. Workspace selection is an authentication foundation, not complete data isolation, and this version must not be exposed as a multi-tenant production service. Invitations still immediately add existing users; acceptance, role management, session revocation, and workspace key lifecycle remain separate work.

## Verification boundary

Integration coverage checks explicit selection, membership-only listing, cross-workspace role differences, membership removal, live role changes, old-session rejection, and administrator-only integration-key responses. It does not prove incident/telemetry isolation, which will require dedicated regression tests during the following increments.
