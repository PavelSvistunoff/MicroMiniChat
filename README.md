# MicroMiniChat

A minimal Android messenger for exactly two people. Text and files travel over
WebRTC DataChannels; pairing uses copy/paste or Android Sharesheet. No accounts,
phone numbers, application backend, Firebase, analytics, push or advertising. Optional user-configured TURN relay support is in development.

## 0.0.1 preview

The implementation includes verified persistent peer identity, acknowledged text
messages, SQLite history, SAF streaming file transfers, cancellation, SHA-256
verification and explicit reconnection. Automated and emulator results are recorded
in [validation](docs/VALIDATION.md). **Physical-device and internet-network acceptance
was explicitly deferred. This is an early preview, not a universal-connectivity or
independent security-audit claim.** See [release notes](RELEASE_NOTES.md).

Signed APKs and checksums are tracked in [release/0.0.1/](release/0.0.1/). Published builds belong on
the repository's [Releases page](https://github.com/PavelSvistunoff/MicroMiniChat/releases).
Prefer the arm64-v8a APK for modern phones; universal includes all four ABIs.
Android 8/API 26 or later is the development minimum; runtime testing currently
covers an API 35 arm64 emulator only. No Play Store eligibility is claimed.

## Pair and use

For restrictive internet networks, see [TURN setup](docs/TURN.md). The checked-in
0.0.1 APKs do not include the new TURN source changes.

These steps describe the updated `0.0.2-preview` UI; original 0.0.1 uses manual
offer/answer labels.

1. Keep both apps open and tap **Pair / verify**. Expand **Connection settings**
   to configure STUN or your authenticated TURN server before connecting. Settings
   are hidden by default; no external server is selected automatically.
2. On phone A, tap **Start chat**, then **Share invitation** (or **Copy invitation**).
   Send it to your friend through a trusted messenger.
3. On phone B, share that text into MicroMiniChat, or tap **Join chat** and
   **Paste & connect**. Shared text is prefilled; tap **Connect with pasted text**
   to accept it. Tap **Share reply** and send the reply back to A.
4. On phone A, open the shared reply and tap **Connect with pasted text**, or use
   **Paste & connect**. Each phone shows its next step; raw exported SDP is hidden.
5. Once connected, tap **Compare fingerprints**. Independently compare the full
   fingerprints shown on the two devices, then confirm they match on each phone.
   Never trust fingerprints supplied only through the same untrusted signaling path.
6. Return to chat to send text or attach files. Delivered means a remote persistence
   ACK; file completion requires verified SHA-256 and a completion ACK.

STUN is discovery, not a relay. Some NAT/CGNAT/firewall combinations cannot connect
without TURN; configure your own authenticated TURN endpoint in the updated app. SDP reveals addresses, so treat it as
sensitive. Direct mode rejects relay candidates; TURN is explicit opt-in. QR is not supported: full SDP may
be too large for one code.

Keep the app foreground. Short switches for sharing/pickers have a best-effort
two-minute grace; Android may stop the process sooner. After loss/expiry, pair again
with fresh signaling. A terminated app cannot receive connections or offline messages.

Settings provide retry, bounded history paging, history deletion, trust/identity reset
and third-party notices. History deletion retains minimal replay receipts; deleting
**all** metadata also resets identity and requires verification again. Files with
unknown size or larger than 16 GiB are rejected. Interrupted transfers restart from
zero after selection/acceptance; external providers may delay cleanup.

## Build and test

Use JDK 17, Android SDK 35/build-tools 35.0.0, and `ANDROID_HOME` or untracked
`local.properties`. Gradle 8.11.1 and AGP 8.9.2 are pinned. Runtime dependency:
`io.github.webrtc-sdk:android:150.7871.04`; Gradle SHA-256 verification is checked in.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # attached emulator/device
./gradlew :probe:testDebugUnitTest
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :app:assembleRelease            # unsigned without signing environment
```

See [releasing](docs/RELEASING.md) for signed builds; never commit signing secrets.
`:probe` remains a synthetic, unauthenticated transport harness, not the messenger.

Read [SPEC](SPEC.md), [architecture](ARCHITECTURE.md), [security](SECURITY.md),
[protocol](PROTOCOL.md), [milestones](TASKS.md) and [contributor guide](AGENTS.md).
Project source is [GPL-3.0](LICENSE); third-party notices are retained under
`third_party/` and the WebRTC notices are embedded in the app.
