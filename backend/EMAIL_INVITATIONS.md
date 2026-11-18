# Verified email and pending workspace invitations

Decision: 2026-11-18. The first SaaS release requires a verified account email matching the invited address. Delivery uses standard SMTP. This increment does not deploy SMTP credentials or migrate a live database.

## Ownership and business rules

The security module owns email proof; workspace services own pending invitations and membership creation. The mail module owns an encrypted PostgreSQL outbox and SMTP delivery. Account creation and mail enqueue commit together when delivery is enabled. Invitation creation and its notification always commit together. SMTP runs after commit, outside database transactions, on its own scheduler.

Signup creates an unverified account and its own workspace. Existing memberships continue to work. Sending invitations requires a currently authorized non-legacy workspace administrator whose email is verified. An invitation grants no access until an authenticated recipient explicitly accepts it with a verified matching email. Recipients can join multiple workspaces; acceptance never changes their selected workspace or issues a session. Invitation IDs are metadata, not bearer credentials; no invitation token is needed or returned.

Verification requires both the emailed proof and authentication as the account that requested it. This prevents an attacker from pre-registering someone else's email and having the recipient activate the attacker's password merely by following a link. The page requires explicit confirmation; mail link scanners cannot verify by GET. Links carry the proof in a URL fragment, which the page removes immediately and holds only in memory. Sign in in another tab or reopen the emailed link after signing in. No proof is placed in browser storage, query parameters, logs, or API responses.

Proofs contain 32 random bytes, are stored as SHA-256 hashes, expire after 24 hours, and are bound to the account and email snapshot. Resend has a database-enforced 60-second per-account cooldown, revokes prior unused links and cancels their queued messages. Changing the email invalidates prior proof. Repeated valid confirmation issues neither sessions nor membership.

Invitations expire after seven days. Creation permits ADMIN, SRE and VIEWER roles, normalizes email, rejects an existing member or duplicate pending invitation, and limits creation to 100 per workspace per rolling hour (`roottrace.invites.hourly-limit`). Workspace locking serializes quota checks. Administrators can revoke pending/expired invitations; accepted invitations require later membership management. Acceptance and revocation lock the invitation: exactly one outcome wins. Repeated acceptance is safe while the same accepted account retains membership; it cannot restore removed membership or overwrite an independently assigned role. List APIs use bounded cursor pagination.

## Schema and rollout

V8 adds paired `users.verified_email`/`email_verified_at`, hashed `email_verifications`, and encrypted `outbound_emails`. It adds invitation actor/timestamp snapshots, retires the old plaintext `token` column, and revokes old PENDING invitations. It preserves all existing memberships and account passwords; old accounts start unverified and receive no automatic email. Review historical memberships created by the prototype before public rollout. Snapshot/restore and coordinated backend deployment are required: the old invite implementation is incompatible with V8. Flyway V1-V7 remain unchanged. No production rollback by editing migration history.

Outbox bodies use AES-256-GCM with a fresh 12-byte nonce and message ID/purpose/recipient as authenticated data. The separate encryption key is required only when mail is enabled. SENT/CANCELLED records retain metadata and scrub the encrypted body/nonce. Failed bodies remain encrypted until superseded, cancelled, or expired. Claims use SKIP LOCKED and five-minute fenced leases; SMTP has bounded network timeouts. Five delivery attempts use 10/20/40/80-second retry delays; exhausted or expired final leases become FAILED. Obsolete/expired messages are cancelled before claiming. SMTP acknowledgement can be lost after delivery, so duplicate notifications are possible. Verification and membership remain idempotent.

## Configuration

Mail defaults off. With it off, signup and existing workspace use continue, while verification requests and invitation creation report delivery unavailable. Acceptance still enforces verified email. Production Compose passes these settings to the backend; direct Spring launches require process environment settings (the root `.env` is not automatically loaded).

| Variable | Requirement |
| --- | --- |
| `ROOTTRACE_MAIL_ENABLED` | `true` to issue/queue mail and run delivery. |
| `ROOTTRACE_MAIL_FROM` | Valid single sender address; use a domain you control. |
| `ROOTTRACE_PUBLIC_APP_URL` | Application origin only, e.g. `https://roottrace.example.com`; HTTPS outside explicit `dev` without `prod`. |
| `ROOTTRACE_MAIL_ENCRYPTION_KEY` | Base64 of 32 independent random bytes; keep in secret management and backups, never reuse JWT/ingestion secrets. |
| `ROOTTRACE_SMTP_HOST`, `ROOTTRACE_SMTP_PORT` | Provider hostname/port; typically 587 for STARTTLS or 465 for implicit TLS. |
| `ROOTTRACE_SMTP_USERNAME`, `ROOTTRACE_SMTP_PASSWORD` | Required outside development when mail is enabled. |
| `ROOTTRACE_SMTP_AUTH` | Defaults true; production must keep true. |
| `ROOTTRACE_SMTP_STARTTLS` | Defaults true and requires successful upgrade. Set false only when using implicit SSL in production. |
| `ROOTTRACE_SMTP_SSL` | Defaults false; enable for implicit TLS. |

Production startup rejects plaintext SMTP, disabled hostname verification, missing credentials, invalid sender/origin, or malformed/shared encryption keys when mail is enabled. These checks do not establish SMTP reachability or inbox delivery. Configure provider sender verification, SPF/DKIM/DMARC, quotas and bounce handling separately. Do not expose secrets through `docker compose config` output.

Generate an independent key in a trusted secret provisioning environment, e.g. PowerShell `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`, and save it directly into your secret manager. Do not rotate it while encrypted unsent messages remain: this version has one active encryption key, not a key ring. Preserve the key securely with database backups; a database restore without it cannot deliver pending messages.

### Local mail catcher

From the repository root, `docker compose --profile mail up -d mailpit` starts the optional pinned [Mailpit v1.34.0](https://github.com/axllent/mailpit/releases/tag/v1.34.0). SMTP is loopback port 1025; inbox UI is `http://localhost:8025`. No relay or forwarding is configured. This is a development service, absent from production Compose. Messages contain real verification links; use disposable accounts and keep the UI local.

For a backend launched on the host with profile `dev`, set mail enabled, a development sender, public origin `http://localhost:3000`, an independent encryption key, SMTP host `localhost`, port `1025`, auth false and STARTTLS false. Existing signing/ingestion settings still apply. The production profile takes precedence over dev and rejects this plaintext configuration.

### Operator checks and recovery

Verification status and administrator invitation lists expose delivery state. SENT means submitted to the SMTP server, not delivered to an inbox. The generic actuator health does not contact SMTP: durable API traffic should not become unhealthy solely because email is down. Monitor pending age/FAILED counts of `outbound_emails` through trusted platform operations; it is separate from the ingestion/task recovery API. Never select/decrypt message payloads in diagnostics. After fixing delivery, users can request a new verification link after cooldown; administrators can revoke and create a new invitation. There is no mail redrive API in this increment. A worker may already be sending when revocation occurs; any late notification grants no access.

## API and verification

All endpoints below require a valid account session; workspace invitation administration additionally requires selected-workspace ADMIN.

| Endpoint | Contract |
| --- | --- |
| `GET /api/v1/auth/email/status` | Own email, verified state, delivery configuration/status, next resend time. |
| `POST /api/v1/auth/email/verification` | 202 generic acknowledgement after durable request/cooldown check. |
| `POST /api/v1/auth/email/verify` | JSON `token`; confirms only the authenticated requesting account. |
| `POST /api/v1/workspace/invites` | JSON `email`, `role`; 201 pending metadata, no secret. |
| `GET /api/v1/workspace/invites` | Administrator metadata/history, optional `before` UUID and `limit` 1-100. |
| `DELETE /api/v1/workspace/invites/{id}` | 204 revoke; 409 if accepted. |
| `GET /api/v1/auth/invitations` | Own verified-email pending inbox; same pagination. |
| `POST /api/v1/auth/invitations/{id}/accept` | Verified matching email, explicit acceptance; current membership role returned. |

Tests exercise real local SMTP protocol against an in-process loopback catcher that never forwards, isolated PostgreSQL migrations and transactions, retries/scrubbing, hash/encryption storage, wrong/no requesting session, stale/expired/revoked proof, invitation expiry/revocation, concurrent acceptance/revocation, atomic queue failure, quota, and preservation of existing membership/session selection. Cipher/config tests enforce production TLS/auth/key separation. A scheduler test checks automatic delivery on its dedicated thread. Production provider delivery, DNS configuration, the optional Mailpit image and browser interactions require deployment/manual verification.

Password reset and current/all-session revocation are now implemented in V9; see [ACCOUNT_RECOVERY.md](ACCOUNT_RECOVERY.md). Remaining account lifecycle work includes account disable/delete and broader abuse controls. V10 now implements audited membership removal/role changes with last-admin protection; see [MEMBERSHIP_MANAGEMENT.md](MEMBERSHIP_MANAGEMENT.md). This increment does not make the complete service production-ready.
