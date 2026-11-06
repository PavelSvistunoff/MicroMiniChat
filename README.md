# MicroMiniChat

A two-person Android messenger using bundled Tor v3 onion services and mutual
TLS 1.3. No account, backend, public IP configuration, forwarding or extra Tor app.
Native terminal-style interface; English and Ukrainian; local message history,
durable delivery acknowledgments, offline local queue and consented file streaming.

**0.1.0-tor-preview.** Tor is the only product transport. Physical-phone/network
acceptance remains NOT VERIFIED; see [validation](docs/VALIDATION.md) and the
[physical checklist](docs/PHYSICAL_TEST_PLAN.md). Historical 0.0.1 APKs are a different,
obsolete architecture and are not Tor builds.

## Install and pair

Use Android 8/API 26 or later; runtime qualification currently covers API 35 arm64.
Install the matching ABI APK (or universal APK) from your local build; use the signed
release preview for ordinary testing and debug APKs for development. Updates
over an existing installation require the same APK signing key; do not uninstall
an existing installation merely to bypass a signer mismatch, because uninstalling
removes private history and identity.

1. Keep both apps open with Internet access; Tor bootstrap/publication can take minutes.
2. Choose **Create Chat**, then **Share Invitation**. Share privately; it expires in ten minutes.
3. The other person chooses **Join Chat**, imports/pastes the invitation and explicitly chooses **Connect**.
4. Open **Verify peer identity** on each phone. Independently compare both full fingerprints
   through a trusted channel and confirm matching fingerprints on both devices.
5. Send text or choose **Attach file**. The recipient chooses a SAF destination before receiving.

Verified peers reconnect automatically without another invitation. Pending messages
remain only on the sender until both peers are available. Delivered means a durable
receiver commit, not that a human read it. Interrupted files restart from zero.
See [pairing](docs/PAIRING.md), [security](SECURITY.md) and [lifecycle](docs/ANDROID_LIFECYCLE.md).

## Build and validate

JDK 17, Android SDK 35 and build-tools 35.0.0; configure ANDROID_HOME or local.properties.
Use the checksum-pinned wrapper. Dependencies require network access on first build.

```sh
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

The connected suite needs a disposable Internet-connected emulator/device and uses
real Tor, synthetic content and isolated identities. The emulator UI test temporarily
changes/restores font scaling. Builds produce four ABI APKs plus a universal APK
under app/build/outputs/apk/{debug,release}/. Ordinary release builds are minified,
resource-shrunk and unsigned unless a local signer is configured. See
[release instructions](docs/RELEASING.md). Build artifacts are not published by these commands.

## Limits

Tor may be blocked or slow; no bridges or connectivity guarantee are provided.
Networking stops after a best-effort two-minute background grace; there is no push,
permanent background service or terminated-app incoming delivery. Android may stop
it earlier. No QR scanner is bundled: bounded private share/paste avoids an extra
camera permission/library. SAF providers may refuse partial-file deletion; heed the
cleanup warning. Local SQLite is app-private, not independently encrypted against
an unlocked or compromised OS. Tor is not a promise against traffic correlation or
endpoint compromise. API 26 and the other bundled ABIs require runtime qualification.

GPL-3.0; bundled dependency notices are in third_party/tor and the app license screen.
See [architecture](ARCHITECTURE.md), [protocol](PROTOCOL.md),
[runtime updates](docs/TOR_RUNTIME.md) and [milestones](TASKS.md).
