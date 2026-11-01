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

Upgrading from earlier previews: see the [certificate lifetime correction](docs/IDENTITY_DESIGN.md).
Existing identities keep their original expiry; resetting an expired identity deletes local history.

The current `0.0.3-preview` can host pairing on the creator’s phone. It requires a
publicly reachable IP/TCP port; it does not bypass mobile CGNAT. See the
[phone-hosting setup](docs/PHONE_PAIRING.md) before testing across different networks.

1. Creator: **Pair / verify → Connection settings**. Check the advertised phone IP
   and TCP port (default 44333); arrange port forwarding/firewall access if needed.
   Configure STUN/TURN for WebRTC where required.
2. Creator: **Start chat → Share invitation**. The invitation includes the reachable
   address and temporary TLS pin/token; keep this phone open.
3. Friend: open/share the link into MicroMiniChat and tap **Connect with pasted text**,
   or choose **Join chat → Paste & connect**. The offer and answer are exchanged
   automatically; no reply needs to be sent through another messenger.
4. Compare the full persistent fingerprints independently and confirm on both phones,
   then return to chat. A fingerprint supplied only with the invitation is insufficient.
5. Send text or files. Delivered means a remote persistence ACK; file completion
   requires verified SHA-256 and a completion ACK.

If the creator cannot host, uncheck **Host pairing on this phone** for manual
invitation/reply exchange. Original 0.0.1 and 0.0.2 APKs retain their earlier UI.

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
