# Mobile security requirements

Current gateway shared bearer token is suitable only as an initial development demonstration. Before production:
- Build local physical-presence QR pairing with a 60-second single-use secret and scoped device credentials.
- Store revocable device registrations in a durable protected database.
- Protect approvals with biometric confirmation and/or strong device authentication.
- Bind device authorization to desktop policy; never use mobile UI state as evidence of authorization.
- Maintain durable transaction IDs, audit trails and idempotency across gateway restarts.
- Enforce TLS certificate validation; private trusted tunnel preferred over public port exposure.
- Use notification payloads containing no file paths or project secrets.
- Add server-side per-device rate limits, kill switch and alert abuse detection.
- Disable dangerous controls under degraded native monitoring and containment.
- Redact path details, secrets, prompts, output content and source excerpts by default.
- Test offline/reconnect behavior, stale approval races, credential revocation and replay.
- Vercel may serve only as a relay; it must not authorize or execute desktop operations.
