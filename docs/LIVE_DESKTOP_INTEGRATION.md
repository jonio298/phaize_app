# PhAize Mobile — Real Desktop Read-only Integration

This feature is split across two repositories:

- **Windows desktop**: `jonio298/phaize`, branch `feature/mobile-live-desktop-bridge`.
- **Android + gateway**: `jonio298/phaize_app`, branch `feature/mobile-live-desktop-bridge`.

## State of the integration

✅ Real agent sessions are read from the Windows Tauri native SessionRegistry.
✅ Recent events and file activity are read from the desktop SQLite event store.
✅ Redacted filenames, event categories, actions and event timestamps flow through a loopback-only read-only HTTP bridge to the Node gateway and Android UI.
✅ Missing desktop or invalid credentials fail closed.
✅ Android distinguishes a real connection from available approval/agent-control authorities.

🚫 Not supported: remote approvals, remote stop/pause/resume, task delegation from phone, notifications, file content, durable event replay, production phone pairing, Vercel relay, and any claim that remote snapshot indicates healthy monitoring/MXC enforcement.

## Local-only configuration

On Windows, before launching the desktop process, set an independent secret in its environment:
```powershell
$env:PHAIZE_MOBILE_BRIDGE_TOKEN = "REPLACE_WITH_AN_INDEPENDENT_RANDOM_SECRET_AT_LEAST_32_CHARACTERS"
```
Without this variable the desktop bridge is disabled, and it will never bind port 8790. With it, the bridge binds **127.0.0.1:8790 only** and accepts:
- `GET /mobile/v1/snapshot` (Bearer auth)
- `POST /mobile/v1/approvals/decisions` → 403
- `POST /mobile/v1/agent-control` → 403

Then start the companion gateway on the **same Windows computer**, using a different secret:
```powershell
cd gateway
$env:PHAIZE_DEMO = "0"
$env:PHAIZE_HOST = "127.0.0.1"
$env:PHAIZE_PORT = "8787"
$env:PHAIZE_MOBILE_TOKEN = "REPLACE_WITH_A_SECOND_RANDOM_SECRET_AT_LEAST_32_CHARACTERS"
$env:PHAIZE_DESKTOP_BRIDGE_URL = "http://127.0.0.1:8790"
$env:PHAIZE_DESKTOP_BRIDGE_TOKEN = $env:PHAIZE_MOBILE_BRIDGE_TOKEN
node src/server.mjs
```
Check gateway snapshot locally:
```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8787/v1/snapshot" -Headers @{ Authorization = "Bearer $env:PHAIZE_MOBILE_TOKEN" }
```
It must show `mode: live`, real native observed sessions (when any), and `approvalsAvailability: not_connected`, `controlsAvailability: not_connected`. If the desktop is missing or unavailable, the request must fail rather than return demo data.

## Connecting Android

Run the gateway only on 127.0.0.1 and expose it via a **private authenticated HTTPS reverse tunnel** to the paired phone. The Android app requires HTTPS. Do not point the Android app at `http://` or publicly forward port 8787. Enter the tunnel's HTTPS endpoint and the second gateway secret on the phone Settings screen.

**This is a development-only credential model.** Device-specific revocable pairing, host authorization and biometric confirmation are prerequisites to enabling any remote actions.

## Privacy / fidelity

The initial native snapshot:
- Includes a bounded sample of live sessions and native stored events.
- Redacts full paths to basenames; never exports event metadata_json, prompt contents, network destinations or file contents.
- Marks monitoring/containment status as `not_attested_by_bridge`; these cannot be inferred from merely reaching the desktop.
- Leaves remote tasks, approvals, and controls explicitly `not_connected`.
- Does not offer event cursor replay; snapshots refresh via polling. It is not a complete audit export.

## Next engineering gates

1. Add an authenticated device registry and pairing UI in desktop with physical owner approval.
2. Wire pending approvals to the **actual outstanding native broker request**, binding exact process/session/workspace/policy revision/expiry.
3. Require revalidation of request lifetime and changed operation on every phone decision. Native MXC and policy engine remain authoritative.
4. Add durable request-specific idempotency, audit records, and device revocation.
5. Implement native pause/stop controls only after verifying session ownership and identity.
6. Add cursor-based efficient queries and relevant redacted changelog/task state.
7. Add FCM notifications with non-sensitive payloads, and an optional relay that never authorizes.
8. Compile and run desktop Rust tests on Windows; test phone connectivity and controls on a real device.

This integration must **not** be considered production-safe for remote approvals until all authorization requirements and adversarial tests are complete.
