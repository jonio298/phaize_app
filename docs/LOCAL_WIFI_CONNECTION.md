# Connect PhAize Mobile directly to Windows over Wi-Fi

This branch removes any requirement for a third-party relay or a separately launched Node gateway in **local mode**.

## Start on Windows

Use the matching desktop branch in `jonio298/phaize`: `feature/local-only-secure-mobile`. Open PhAize desktop → Settings → Mobile & Local Network → Start server → Pair a new phone.

Windows shows a QR code containing `phaize://pair?host=<local-ip>&port=8791&fp=<sha256>&secret=<random-one-use-secret>`. The QR is sensitive; it expires after two minutes and can only be used once. It is never sent to a cloud service.

## On Android

Install the debug APK from this branch's successful GitHub Actions workflow artifact. The app will open its pairing screen when Android recognizes the PhAize QR link. Otherwise copy the pairing link and paste it into Settings → Pair directly with PhAize.

Connect both devices to the same trusted Wi-Fi, confirm Windows firewall allows the PhAize desktop on the **Private** network only, and tap Pair securely.

The app validates the URL refers to a private LAN IPv4 address, requires port 8791, pins the desktop TLS certificate SHA-256 fingerprint, and exchanges the one-use invite for a revocable, read-only bearer token. Tokens are encrypted at rest with Android Keystore.

## Expected behavior

- Home: real native observed agents and a bounded activity sample.
- Agents, Files, History: read-only live desktop data. Paths are redacted to filenames.
- Approvals and agent controls: **intentionally disabled**, not represented as an empty approval queue.
- Connection failure: offline warning; no stale live agent cards.
- 8-second polling; no cloud push, no Vercel relay, no public network listening.

## Security limitations

This is an MVP pending Windows Rust compilation, hardware pairing tests and an independent security review. Existing older Node gateway source remains in the repository for reference but is **not used by local mode**; it can be removed in a later cleanup.

To revoke a lost phone, use PhAize desktop Settings → Mobile & Local Network → Paired Phones → Revoke. Removing the app's local connection alone does **not** revoke the desktop credential.

See the corresponding desktop documentation `docs/MOBILE_LOCAL_NETWORK.md` for privacy and key-storage limitations.
