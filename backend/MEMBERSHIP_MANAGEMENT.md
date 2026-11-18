# Workspace membership changes

Decision: 2026-11-18. Workspace services own membership role changes, removal, departure, and the corresponding audit history. Authentication continues to resolve current membership on every request. This increment does not change accounts, incidents, workspace-owned ingestion keys, or historical ownership.

## Business rules

Current non-legacy workspace administrators with verified email can assign ADMIN/SRE/VIEWER roles and remove other members. Members of any role can leave their own workspace without requiring email verification. Administrators can demote or remove themselves only when another administrator remains. The administrator count is based on current membership roles; this release has no account-disable state. Existing prototype memberships are preserved and still require operational review.

Changing the last administrator's role or removing/leaving that membership returns 409. Promote another member to administrator before handing off access. No ownership-transfer role is introduced: administrators remain peers. Role promotion is an explicit administrator action, separate from invitation consent. Changing an existing member's role does not require accepting another invitation.

Every mutation requires the exact membership ID, its reviewed version, and a nonblank reason of at most 500 characters. A stale version returns 409; an old ID cannot affect a newly created membership after removal/rejoin. Assigning the current role with the correct version is a no-op, without a new audit record or version increment. Missing/foreign membership IDs return 404 within the selected workspace. Unsupported roles and malformed requests return 400. Unauthorized callers cannot act through forged role/author/workspace fields.

Removal revokes PENDING invitations addressed to the removed user's current email in that workspace and cancels/scrubs their unsent mail in the same transaction. An old accepted invitation cannot recreate removed membership. A fresh invitation created after removal can legitimately authorize a new membership. Invitations to other recipients remain workspace-owned; remaining administrators should review them, together with ingestion credentials, during compromised-user offboarding. Workspace data, account credentials, other workspace memberships and integration keys are preserved.

Role changes affect authorization on the next request without a new login. A JWT selected into a removed workspace is rejected; independent account-only/other-workspace sessions remain valid. Self-removal clears this browser's local session and sends the user to sign in again, while self-demotion refreshes the selected session's role and reloads mounted views. An explicit team refresh also detects changes to the current user's role and refreshes its local session metadata. Requests authenticated before revocation may already be in flight; this increment does not cancel them.

## Serialization, schema and audit

V10 adds `workspace_members.version` (initially 0, also mapped as JPA `@Version`) and `workspace_membership_audit`. It preserves all existing memberships and roles, and does not invent historical audit entries. V1-V9 remain unchanged. Deploy the backend and updated clients together: new mutations require membership IDs/versions returned in `GET /api/v1/workspace`. No live database migration was performed during implementation.

Mutation lock order is acting account, selected non-legacy workspace, then affected membership/invitation rows. The account and workspace use `FOR NO KEY UPDATE`: account/session mutations stay serialized while foreign-key key-share checks remain compatible. This avoids a lock cycle when audit insertion references another user who is accepting an invitation or changing account state. Current administrator role and verified state are re-read from the database after acquiring the workspace lock; the JWT/frontend role is insufficient.

Invitation creation, revocation and acceptance now use that same workspace serialization. Acceptance locks its account, resolves its addressed invitation's workspace, locks the workspace, then locks/rechecks the invitation. Concurrent removal either follows acceptance and removes the resulting membership, or precedes acceptance and revokes the pending invitation. Concurrent administrator departures/demotions cannot both leave the workspace without an administrator. Role changes and removal use workspace serialization and reviewed versions; direct trusted SQL/operator edits can bypass application last-admin protection and must be reviewed separately. No database trigger silently repairs administrator-less prototype workspaces.

Mutation and audit append commit together. An audit failure rolls back the role/removal, invitation revocations and mail cancellation. Audit snapshots record acting/target account emails, user references, membership identity/version, previous/new roles, reason, action, and number of revoked invitations. Deleted membership rows do not erase this history. The client cannot choose the recorded actor. Administrator-only history is workspace-scoped, cursor-paginated and bounded to 1-100 records per page; foreign cursors return 404. It has no public update/delete endpoint. Retention, account/workspace deletion and platform-level tamper-resistant audit export remain separate work.

## API

All endpoints require a valid account session with selected workspace membership. Administrative actions additionally require current ADMIN (and verified email for mutations affecting another member or any role change). Legacy workspaces cannot use this service.

| Endpoint | Request / response |
| --- | --- |
| `GET /api/v1/workspace` | Existing member roster now includes `membershipId` and `version`. |
| `PATCH /api/v1/workspace/members/{membershipId}/role` | JSON `role` (`ROLE_ADMIN`, `ROLE_SRE`, `ROLE_VIEWER`), `expectedVersion`, `reason`; 200 updated member. |
| `POST /api/v1/workspace/members/{membershipId}/remove` | JSON `expectedVersion`, `reason`; 204. Self-departure or verified ADMIN removal of another member. |
| `GET /api/v1/workspace/members/audit` | ADMIN only; optional `before` UUID and `limit` (default 25). Returns `items` and `nextCursor`. |

Responses use `Cache-Control: no-store`. Removal is an explicit POST action so required review metadata does not depend on a DELETE request body surviving proxies. Repeating a completed removal returns 404; refreshing resolves an uncertain response without replaying against a different membership. On 409 the UI preserves the reviewed intent and asks the user to refresh; it never silently retries with the newest version. Reasons appear as escaped text in the audit UI.

## Verification and limits

Integration tests exercise live role changes with existing JWTs, sole-admin handoff, concurrent demotions/departures, target removal versus invitation acceptance, stale version/rejoined IDs, self-departure, unverified-admin and viewer denial, cross-workspace targets/cursors, input constraints, audit rollback including invitation/mail state, and compatibility with another user's session lock. A real loopback HTTP check performs a versioned role update and verifies removed membership is rejected. Independent V9-to-V10 migration coverage preserves historical roles/version defaults without fabricated history. Frontend protocol tests cover reviewed version/identity, empty 204 success, conflict behavior and clearing revoked sessions. Production provider delivery and browser interaction are not established by these checks.

The authorization design was checked against [OWASP authorization guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html); lock compatibility was checked against [PostgreSQL 16 row-level locking](https://www.postgresql.org/docs/16/explicit-locking.html#LOCKING-ROWS). The implementation enforces permissions at the service transaction boundary and uses the database to serialize conflicting mutations.

Account disable/delete, workspace deletion, broader login/signup abuse protection, trusted ingress policy, MFA, external audit/alert routing, tenant integrations and deployment/restore rehearsal remain pending. This increment does not claim complete production readiness.
