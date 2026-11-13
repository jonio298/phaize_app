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

Body: `{"agentId":"codex","action":"pause","deviceId":"device-id","expectedRevision":5,"requestId":"unique-id"}`. Allowed actions: pause, resume, emergency_stop. The desktop must authenticate and authorize the device and verify session revision, lifetime, and status. Emergency-stop must invoke native desktop controls and audit the outcome.

## GET /mobile/v1/chats and GET /mobile/v1/chats/{id}

Read-only conversation views for the phone. There is no write, send or approve path through chats.

List: `{"chats":[{"id":"chat-codex","title":"...","agentId":"codex","preview":"...","updatedAt":"2026-11-12T00:00:00Z","messageCount":12}]}` (newest first, at most 100).

Detail: `{"id":"chat-codex","title":"...","agentId":"codex","messages":[{"id":"m1","role":"user|agent|system","speaker":"You","text":"...","timestamp":"2026-11-12T00:00:00Z"}]}` (at most the latest 200 messages, each text bounded to 4000 characters).

The desktop must redact secrets, tokens, credentials and file contents it does not intend the phone to see. The gateway only whitelists and bounds fields; it cannot judge content. A chat never grants or implies any permission.

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
