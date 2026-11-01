# MicroMiniChat

A minimal Android messenger for exactly two people. Text and files travel over
WebRTC DataChannels; pairing uses copy/paste or Android Sharesheet. No accounts,
phone numbers, application backend, Firebase, analytics, push, advertising or TURN.

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

1. Keep both apps open. Open **Pair / verify**. Leave STUN blank for host candidates,
   or explicitly enter a public endpoint you consent to use, such as
   `stun:stun.l.google.com:19302`. No STUN operator is contacted by default.
2. A creates an offer and copies/shares the completed payload to B. B pastes/applies
   it, then copies/shares the generated answer back. A applies that answer.
3. Independently compare the **full peer fingerprint** with the fingerprint shown
   on the other person's device. Each person confirms the comparison before sending.
   Do not rely on a fingerprint sent only through the same untrusted signaling path.
4. Send plain text, or attach a file. The recipient explicitly saves an incoming file
   through Android's picker. Delivered means a remote application persistence ACK;
   file completion requires verified SHA-256 and a completion ACK.

STUN is discovery, not a relay. Some NAT/CGNAT/firewall combinations cannot connect
without TURN; this app has no relay fallback. SDP reveals addresses, so treat it as
sensitive. Known relay candidates are rejected. QR is not supported: full SDP may
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
