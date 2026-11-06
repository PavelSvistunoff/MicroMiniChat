> HISTORICAL WEBRTC PREVIEW EVIDENCE. Unsupported by the Tor product. See README.md and docs/VALIDATION.md for current behavior.

# M02 Probe Procedure and Limits

## Scope

A separate application, package `io.github.pavelsvistunoff.microminichat.probe`.
This is an unauthenticated feasibility experiment, not a messenger. The foundation
app does not depend on WebRTC. Only fixed 16-byte request/response patterns are
accepted; no user text or file data can be sent. At most one channel is accepted.

## Manual two-device run

1. Install the appropriate `probe-ABI-debug.apk`, or universal debug APK, on both
   consenting devices. Note device OS/ABI and network categories in the matrix.
2. Keep both probe activities foreground/visible. Enter an explicit STUN endpoint
   if needed. Blank means no STUN, only local host candidate discovery.
3. A creates offer. Wait for full ICE gathering, then copy local SDP. Move it to B
   through an independently trusted mechanism without backgrounding either probe.
4. B pastes offer, creates answer, waits for full local SDP, and returns it to A.
   A applies answer. The offer/answer raw SDP is sensitive; do not save it in logs.
5. After both report OPEN_UNAUTHENTICATED, send the synthetic probe and verify the
   roundtrip indication. It is neither a persisted-message ACK nor human authentication.
6. Disconnect, repeat, test a restrictive network and record real outcomes. No TURN
   fallback exists. Unknown native state errors are shown as fixed codes only.

Leaving the activity (including full-screen app switching) closes the peer and
clears its SDP fields. Exchanging raw SDP therefore needs another visible device,
split-screen, or an external mechanism while both probes stay visible. Clipboard
contents are not automatically erased: avoid retaining SDP in clipboard history.
Sharesheet and lifecycle-safe product pairing remain later milestones.

## Automated tests

`./gradlew :probe:testDebugUnitTest` checks SDP byte limits, malformed/media inputs,
STUN-only endpoint rules and buffer overflow boundaries. It does not execute native
WebRTC. `:probe:connectedDebugAndroidTest` uses two native PeerConnections on one
Android device, tests synthetic roundtrip twice with disposal, and rejects malformed
offers. It requires a device/emulator. In-process success would not prove internet
connectivity. Current status: two instrumentation tests passed on an API 35 arm64
AOSP emulator, including two repeated native roundtrips/disposal and malformed
input rejection. No physical-device/internet test has been performed.

## Bounds and failure behavior

SDP is capped at 256 KiB before native parsing and is never exported before full
gathering. Gathering expires after 30 seconds; manual pairing after 120 seconds;
answer application connection attempt after 30 seconds; synthetic response after
10 seconds. Disconnect grace is 10 seconds. New pairing creates a new peer. A single
synthetic request may be outstanding; native buffered bytes are capped at 64 KiB
for enqueue, and at most 32 incoming fixed frames are queued. Overflow/malformed
frames terminate the probe. Text/files, trust, reconnect recovery and background
service behavior are deliberately outside M02.

## Dependency decision

WebRTC prebuilt adoption remains conditional. Native bytes dominate the measured
APK. The standard binary bundles media implementation even though the app adds no
tracks and requests no camera/microphone permissions. A data-only upstream build
might reduce size but adds a native maintenance/build burden; do not adopt an
unreviewed smaller fork for size alone. See WEBRTC_DEPENDENCY.md for evidence and
open provenance/NOTICE questions. M03 cannot start before M02 acceptance.
