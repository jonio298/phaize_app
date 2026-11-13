# PhAize Android Companion

A phone-first Android companion and local-first Node.js gateway for PhAize desktop. This repository was initialized from an empty `jonio298/phaize_app` repository.

## Implemented

- Android Jetpack Compose: Overview, Agents, Approvals, Files, Timeline/Changelog, and Settings.
- Search and filtering for agent, file and event activity.
- Approve once / deny confirmations; pause, resume and emergency-stop requests.
- Android Keystore-backed encrypted saved gateway token.
- Local gateway authenticated with bearer token, limited JSON body, rate limits, request IDs, idempotency and revision checking.
- Demo adapter, optional desktop-bridge adapter, bounded event polling endpoint.
- CI definitions for Node gateway tests and Android debug build.

## Important: what is NOT completed

The real PhAize desktop IPC bridge is **not implemented in this repository**. The desktop must provide `/mobile/v1/snapshot`, `/mobile/v1/approvals/decisions` and `/mobile/v1/agent-control` through a loopback-only authenticated service. All remote actions must be checked by PhAize native enforcement, including MXC, leases, revisions, expiration, scope and device authorization.

No Android APK, FCM push delivery, biometric approval enforcement, QR pairing, live WebSocket transport, or Vercel deployment is included. The app uses polling. Nothing is publicly deployed. Do not treat demo approvals as real.

## Local development

Gateway:
```bash
cd gateway
npm test
PHAIZE_DEMO=1 PHAIZE_MOBILE_TOKEN="replace-with-a-long-random-secret" npm start
```
Gateway listens on 127.0.0.1:8787 by default.

Android: open the `android/` directory in Android Studio, sync Gradle, and run the `app` module. Enter an authenticated **HTTPS** gateway address under Settings. For testing across devices, put a private HTTPS/Tailscale proxy in front of the loopback gateway. No cleartext network connection is permitted by the Android manifest.

## Production security

- Never expose port 8787 directly to the internet.
- Never make Vercel an approval authority. A relay must forward signed/authenticated requests to the host for native validation.
- Never return arbitrary file contents from event endpoints. Apply redaction and project scope filters at the desktop.
- Use device-scoped, revocable credentials and pairing before production release (current shared bearer is development-only).
- Store audit entries and idempotency state durably on the desktop before supporting critical remote approvals.
- Mobile emergency stop must fail closed if desktop monitoring / enforcement is unavailable.
- Do not send secrets or project content in push notification payloads.

See `docs/DESKTOP_BRIDGE_CONTRACT.md`, `docs/NEXT_PHASES.md`, and `docs/SECURITY.md`.
