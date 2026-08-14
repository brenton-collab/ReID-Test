# Relay Capture stable signing setup

Relay v1.1 supports a permanent Android release signing key. The private keystore must never be committed to this public repository.

## One-time setup

1. Generate a keystore locally with Java `keytool`:

```bash
keytool -genkeypair -v -keystore relay-release.jks -alias relay -keyalg RSA -keysize 4096 -validity 10000
```

2. Base64-encode the keystore as a single line.

macOS/Linux:
```bash
base64 -w 0 relay-release.jks
```

PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("relay-release.jks"))
```

3. In GitHub repository Settings → Secrets and variables → Actions, add these repository secrets:

- `RELAY_KEYSTORE_B64` — the base64 text from step 2
- `RELAY_KEYSTORE_PASSWORD` — keystore password
- `RELAY_KEY_ALIAS` — normally `relay`
- `RELAY_KEY_PASSWORD` — key password

4. Re-run the `Build Relay Capture APK` workflow. When `RELAY_KEYSTORE_B64` is present, CI builds a signed release APK. Without it, CI intentionally falls back to a debug APK for compile testing.

## Important

Back up `relay-release.jks` and its passwords somewhere secure. Future Relay updates must use the same signing key to install over an existing release-signed Relay installation.

The first release-signed build will require uninstalling any currently installed debug-signed Relay build once. After that, release-signed versions will update in place as long as the same keystore remains in use.
