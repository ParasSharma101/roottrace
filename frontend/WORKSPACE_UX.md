# Workspace workflow

## Design decision — 2026-11-19

The dashboard previously mixed account security, workspace selection, keys, members and incident operations. Primary navigation now follows the user's work:

| Destination | Responsibility |
| --- | --- |
| Overview | Next setup action, incoming-data evidence and active incidents |
| Incidents | Search/filter incidents and investigate their evidence |
| Integrations | Named ingestion keys, log/alert request examples and processing evidence |
| Team | Members, role management, invitations and membership audit |
| Settings | Workspace identity and advanced processing/recovery tools |
| Account menu | Personal verification, password recovery, sessions and workspace switching |

Signup enters the new workspace overview. Login retains explicit workspace selection. The chooser puts available workspaces first, with invitations in a separate disclosure. Public authentication and recovery pages share the white/teal shell.

`WorkspaceSession` refreshes the server profile, uses live roles, clears mounted workspace state when identity changes and rejects stale profile responses. Server authorization remains authoritative. Viewer investigation controls are read-only; note authors come from the signed-in profile. Incident detail has keyboard-operable Timeline, Hypotheses, Alerts, Notes and Postmortem tabs. Failed requests offer inline retry rather than implying empty results or success.

## Connection evidence

`GET /api/v1/workspace/setup-status` supplies read-only evidence from existing workspace tables. A named key alone does not establish a connection. HTTP 202 means accepted for queued processing; a processing receipt is separate evidence. Missing/unavailable status stays unknown. Historical counts and timestamps do not establish current uptime or continuous delivery. Batch receipts count processed events, not individual log lines.

The guide copies examples only; it never sends data automatically. Secrets are shown only by the existing one-time key flow. No provider connectors or OAuth controls are presented without implementations. Demo simulations remain development-only, under Settings for the isolated legacy demo workspace.

## Verification and limits

- 21 frontend protocol/navigation tests and TypeScript checks passed.
- Production build validation initially used an isolated source copy. The final standard frontend build passed with the dev server stopped, following dependency restoration. The local preview was then restarted.
- Browser inspection covered Overview, Integrations, Team, Settings, Account and mobile navigation with the user's existing workspace. No ingestion keys, invitations or incident fixtures were created in that workspace.
- Backend full suite: 163 passing tests; setup integration tests passed again after service extraction.
- The running backend must be restarted with its existing environment to load the new endpoint. No database migration is needed.
- Real incident review actions were not exercised in the user's live workspace. A populated staging workspace remains appropriate for broader usability testing.

The scoped light-theme mapping for existing investigation/admin components is a compatibility layer. Future component work should replace old utility colors with shared semantic styles. Setup aggregates read historical workspace rows on demand; large deployments should measure query cost before introducing cached counters.
