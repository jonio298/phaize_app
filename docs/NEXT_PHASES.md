# Integration roadmap and acceptance gates

## Phase A — Desktop authority (highest priority)
- [ ] Implement the actual PhAize desktop bridge, verified by live integration tests.
- [ ] Map native approvals, agent sessions, task events, file history and changelog to a redacted snapshot.
- [ ] Apply native authorization, MXC containment, writer leases, and fail-closed behavior for each action.
- [ ] Persist durable idempotency and audit outcomes on the desktop.
- [ ] Test offline requests, restarts, expired approvals, concurrent conflicting decisions and containment failures.

## Phase B — Phone security
- [x] Android Keystore-encrypted gateway token (development credential).
- [ ] QR pairing, device-scoped tokens, revocation and rotation.
- [ ] Biometric confirmation for approvals and destructive controls.
- [ ] Configure private TLS connectivity and certificate trust model.
- [ ] Verify no unencrypted secrets or backups.

## Phase C — Feature parity
- [x] Overview, agents, approvals, file event feed, timeline, and basic settings.
- [x] Approval and agent-control request contract (native bridge required).
- [x] Mobile task creation/assignment request UI and gateway contract (requires native bridge).
- [x] Mobile scoped file-browser metadata, file activity and error-log UI and gateway contracts (requires native bridge).
- [ ] Native task queue hookup, task review actions, agent chat, detailed process metrics, file diffs and audit drill-downs.
- [ ] Pagination and virtualization for long event histories.
- [ ] Multiple workspace/host profiles.

## Phase D — Live delivery
- [x] Periodic polling and revision-based events endpoint.
- [ ] Persistent SSE/WebSocket transport with bounded replay cursors.
- [ ] FCM notifications via secure broker with payload minimization.
- [ ] Notification routing for urgent approvals and monitoring failures.

## Phase E — Release
- [ ] Android build and instrumented UI tests on a real device.
- [ ] Gateway + desktop integration tests and load testing.
- [ ] Privacy/permission audit, threat model, signing and release configuration.
- [ ] Optional authenticated Vercel relay only after security gates.

The repository CI build and gateway tests are a baseline, not proof of production readiness.
