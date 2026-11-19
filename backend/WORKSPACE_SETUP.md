# Workspace setup evidence

`GET /api/v1/workspace/setup-status` returns `observedAt`, `activeKeyCount`, `acceptedRequestCount`, nullable `lastRequestAt`, `processedEventCount` and nullable `lastProcessedAt`.

The controller delegates to a read-only transactional service. It requires a selected workspace and checks current membership through `MembershipAccess`, including legacy isolation. Any current member can see these aggregate counts. Responses use `Cache-Control: no-store`; no ingestion secret, key metadata or event payload is exposed.

The query reads existing `workspace_ingestion_keys`, `ingestion_submissions` and `ingestion_receipts`, always scoped by workspace ID. Revoked keys are excluded from the active count. Accepted requests and processed events are separate: durable acceptance is not processing completion. Historical counts are onboarding evidence, not a live health check. No schema or migration changes are required.

`WorkspaceSetupIntegrationTest` verifies foreign-workspace exclusion, empty viewer results, active versus revoked keys, distinct acceptance/processing evidence, absence of credentials, no-store, account-only rejection and revoked membership. Full suite: 163 passing tests. Focused setup tests passed again after extracting the service.

Restart an already-running backend in the same terminal and environment to load this endpoint. Existing SMTP configuration must be retained.
