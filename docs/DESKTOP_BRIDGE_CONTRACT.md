# Desktop IPC bridge integration contract

PhAize desktop must own the authoritative actions. This repo contains an HTTP transport client in `gateway/src/desktop-adapter.mjs` and **does not** implement the desktop IPC listener.

Bind the bridge to `127.0.0.1` on a dedicated port with an independent secret, or replace with a Windows named pipe having appropriate ACLs. The bridge must never be internet-facing.

## GET /mobile/v1/snapshot

Returns the same v1 JSON schema used by the Android app:
```json
{"schemaVersion":1,"revision":2,"generatedAt":"2026-11-12T00:00:00Z","mode":"live","host":{"name":"PhAize","online":true,"monitoring":"healthy","containment":"enforced","cpu":10,"memoryPercent":30},"agents":[],"tasks":[],"approvals":[],"files":[],"timeline":[],"statistics":{"eventsToday":0,"blockedToday":0,"completedToday":0}}
```

Events should include a monotonic revision, distinct ID, ISO timestamp, type, title and redacted detail. Paginate large result sets at source and include a stable cursor. Never expose unrestricted file contents or secrets.

## POST /mobile/v1/approvals/decisions

Body: `{"approvalId":"ap-100","decision":"approve_once","deviceId":"device-id","expectedRevision":1,"requestId":"unique-id"}`

The native desktop must verify the authenticated device has approval permission; approval still exists and is pending; expiry is valid; expected revision matches; exact operation and scope are unchanged; containment and monitoring state permit the action; and the request ID has not been consumed with different parameters. Persist the resulting audit record. Return `{"status":"ok","result":{"approvalId":"ap-100","applied":true}}`, or status `forbidden`, `expired`, `conflict`, `not_found`, `unavailable`.

## POST /mobile/v1/agent-control

Body: `{"agentId":"codex","action":"pause","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`. Allowed actions: start, pause, resume, emergency_stop. `start` must start only an existing authorized native agent/session definition, not execute a mobile-supplied command or path. The native service must confirm the agent is stopped, ensure its workspace and policy remain valid, and enforce MXC before launch. The desktop must authenticate and authorize the device and verify session revision, lifetime, and status. Emergency-stop must invoke native desktop controls and audit the outcome.

## POST /mobile/v1/agent-permissions

Body: `{"agentId":"codex","read":"allow","write":"ask","delete":"ask","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`.

Only `read=allow|ask|deny`, `write=ask|deny` and `delete=ask|deny` are accepted by the gateway. Mobile cannot enable unrestricted writes/deletes. These values are requests, not effective permissions. The native desktop must authorize the device to manage this agent's policy, validate a live revision and workspace, compare against organizational/admin floors, update the enforcement policy atomically, apply any required MXC/lease reconfiguration and persist audit + idempotency **before** reporting success. If enforcement update fails, retain the old stricter effective policy and return `unavailable` or `forbidden`. Return effective policy in a subsequent snapshot as `agents[].permissions = {"read":"allow","write":"ask","delete":"ask"}`. The mobile UI must not imply success from a request accepted only by the HTTP gateway.

## POST /mobile/v1/tasks

Body: `{"title":"Fix gateway test","description":"Investigate and report","agentId":"codex","priority":"normal","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`.

`agentId` may be null for auto-delegation. The native desktop must authenticate the device, check task-creation permission, validate every task field and rate limit, verify workspace and current session revision, and enqueue via the existing task orchestration coordinator. **Never execute a raw mobile task as shell commands**. No automatic privilege escalation. Commit an audit record and stable result before replying. The gateway rejects unknown fields, instructions over 4000 characters and priorities outside low/normal/high. The phone may send task descriptions as user content; they are not authorization.

## GET /mobile/v1/files/browse?parent=<opaque-id>

Return `{"parentId":null,"entries":[{"id":"dir_1","name":"src","kind":"directory","sizeBytes":0,"modifiedAt":"2026-11-13T00:00:00Z","risk":"low"}],"truncated":false}`. Parent IDs are **opaque desktop-generated identifiers**, not paths. Validate device read capability, resolve each ID to a canonical path strictly within the selected allowed workspace (including links/junctions), revalidate on each navigation, and redact sensitive names/metadata. Return at most 200 entries, no file contents, no direct filesystem write/delete APIs. Keep UI read-only. The root is selected by desktop scope; the phone cannot supply a root path.

## GET /mobile/v1/errors?after=<opaque-cursor>

Return `{"entries":[{"id":"err1","severity":"error","source":"watcher","message":"ETW queue overload (redacted)","timestamp":"2026-11-13T00:00:00Z","agentId":"codex"}],"nextCursor":null}`. Device needs diagnostics-read permission. Redact tokens, private paths, prompts, file contents and user data; cap page size at 200. Cursor must be authenticated to device and host/session scope, with a stable ordering and server-side expiry. Include watcher drop/reconnect, containment failure, broker/MXC and task coordinator errors. Never use raw log-file paths supplied by the phone.

## Agent chat (native session-bound)

GET `/mobile/v1/agents/:agentId/chat?after=<opaque-cursor>` returns `{"messages":[{"id":"m1","agentId":"codex","sender":"agent","text":"Completed review","timestamp":"2026-11-13T12:00:00Z"}],"nextCursor":null}`. POST `/mobile/v1/agent-chat` body: `{"agentId":"codex","message":"Check tests","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`. Native desktop must bind messages to an existing authorized chat/session, enforce agent ownership and workspace, redact secrets, and never treat phone-supplied instructions as an authorization override. Bound replies and bounded history, max 100 per page, 4,000 characters per outgoing message. Revalidate session revision/idempotency, audit every send. Do not permit commands to execute outside native agent containment.

## Goal delegation

POST `/mobile/v1/goals` body: `{"goal":"Complete Android UI","instructions":"Include tests","priority":"normal","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`. Native coordinator validates device/workspace/revision, creates an auditable goal, plans tasks and assigns agents only within their permitted scopes and security policies. No auto-grant privileges or raw code execution. Return goal ID and queued state only after it is committed.

## Code review

GET `/mobile/v1/reviews?after=<opaque-cursor>` returns `{"reviews":[{"id":"rev_1","agentId":"codex","title":"Update UI","summary":"Changed layout","status":"pending","revision":1,"files":[{"path":"src/App.tsx","additions":4,"deletions":2,"patch":"@@ ..."}]}],"nextCursor":null}`. Native must derive and redact the diff from an authorized revision/commit; return bounded/sanitized snippets only. Never allow untrusted file paths or raw requested patch application from mobile. POST `/mobile/v1/reviews/decision` body: `{"reviewId":"rev_1","decision":"approve_review","deviceId":"device-id","expectedRevision":1,"requestId":"unique-id"}` (also `request_changes`). Decision is a **review acknowledgment only**, not an MXC permission grant, merge, or file write. Native validates immutable review content hash, freshness, device authorization, and current revision before acknowledging.

## Alerts & local notifications

GET `/mobile/v1/alerts?after=<opaque-cursor>` returns `{"alerts":[{"id":"alert_1","severity":"critical","title":"Containment failure","message":"Operation blocked","timestamp":"2026-11-13T12:00:00Z","agentId":"codex"}],"nextCursor":null}`. Native must redact paths/secrets, enforce device visibility, issue stable monotonic cursors and bounded pages. Android has an in-app alert center; **background FCM push delivery is not implemented yet**. A later push service must forward only an opaque alert identifier with no user content and require secure fetch for details. Native desktop must not infer that an alert was delivered or read.

## Advanced operations dashboard

GET `/mobile/v1/ops` returns a bounded and redacted object:
```json
{"health":{"cpuPercent":20,"memoryPercent":40,"diskPercent":30,"temperatureC":55,"networkState":"restricted","coverage":"healthy"},"git":{"branch":"feature/x","dirtyFiles":2,"ahead":1,"behind":0,"lastCommit":"short redacted summary"},"checkpoints":[{"id":"check_01","title":"Before upgrade","createdAt":"2026-11-13T00:00:00Z","description":"Verified snapshot"}],"captures":[],"terminalHistory":[]}
```
Authorization must be per device and current workspace. Values must be actually observed, never fabricated. Git and terminal output are untrusted and may contain secrets: filter and bound returned data, redact environment variables, credentials, personal file paths, patch contents and source code as required. Max 30 checkpoints, 10 captures, 30 command records, 1200 rendered output characters per item. Return `unavailable` if monitoring or a required native subsystem is missing.

POST `/mobile/v1/ops/action` accepts one of exactly these discriminator shapes, each plus `deviceId`, `expectedRevision` and unique `requestId`:
- `{"action":"lockdown","reason":"Emergency security concern"}`: native coordinator freezes agents and blocks pending new starts until local recovery. Audit actor and reason. Remote unlocking is **not** permitted.
- `{"action":"limits","agentId":"codex","cpuPercent":50,"memoryMb":2048,"network":"restricted"}`: per-agent caps, CPU 10–100, memory 256–65536 MB, network one of `off|restricted|policy`. `policy` restores the desktop-owned effective network policy, not unrestricted access. Reject limits weaker than the administrative floor.
- `{"action":"terminal_preset","agentId":"codex","commandId":"run_tests"}`: only `git_status|git_diff_stat|run_tests`. Resolve the command from a native **immutable server-side registry** bound to workspace and session, not from user-supplied arguments, shell templates, working directories or environment. Enforce MXC, resource caps, timeouts and output redaction; a mobile device cannot request arbitrary terminal input.
- `{"action":"checkpoint_restore","checkpointId":"check_01"}`: a **request**, not immediate execution. Native must verify identity, checkpoint integrity, scope, revision, uncommitted work, and require separate local authorization for destructive restore or a two-phase challenge with impact summary. Do not return `ok` for a completed restore unless native enforcement actually committed it.
- `{"action":"capture","target":"phaize_window"}`: request a sanitized capture of **only** the PhAize application window after native window identity verification, secret redaction and authorization. The companion currently shows metadata and capture IDs, not image pixels. Do not serve raw desktop screenshots or unrestricted image URLs.

All actions require permission checks, durable audit, revision/idempotency, native confirmation, and MXC containment where applicable. The gateway cannot authorize a command by itself. Responses `status=forbidden|conflict|unavailable` are never success. Native host restart invalidates stale pending controls. Maintain separate device-scoped capabilities (view health, run presets, read Git, control limits, restore checkpoint, invoke lockdown, capture window).

## Integration steps

1. Implement an immutable redacted snapshot adapter in PhAize desktop.
2. Add authenticated local IPC with explicit origin/device authorization and bounded request size.
3. Wire live pending approvals from the actual native broker; do not synthesize approval IDs.
4. Wire file history, agent status, task state, and changelog using bounded/filtered queries.
5. Wire approval and agent actions to the existing native validation paths, not direct filesystem/process calls.
6. Ensure responses are received only after the decision has been durably applied or definitively denied.
7. Handle desktop restart: invalidate stale revisions, credentials and events as required.
8. Run replay, expiry, conflicting revision, offline, containment-failure, and monitoring-failure tests.

## Network

Android -> private HTTPS tunnel -> local gateway (127.0.0.1:8787) -> local PhAize bridge (127.0.0.1:8790 or named pipe) -> existing native coordinator/broker/MXC.
