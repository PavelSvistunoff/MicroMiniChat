# Validation — 0.0.1 Preview

## Qualification

The user explicitly deferred physical-device acceptance after M02 and authorized
completing the app, committing main and preparing release 0.0.1. This permits further
implementation, not fabricated hardware results. All twelve physical-network rows
remain untested. Independent crypto/native-code review, OEM battery behavior and
physical battery measurements are not complete. Publish only as an early preview.

## Environment and executed checks

macOS arm64, temporary Temurin JDK 17.0.21+9, Gradle 8.11.1, AGP 8.9.2,
Android SDK 35/build-tools 35.0.0. Emulator 37.2.13, AOSP API 35 arm64.
The SDK license/install command was explicitly approved. No SDK/credentials are in Git.

- App JVM tests cover transport policies, signaling roundtrip and 1,000 hostile
  signaling inputs, fingerprint ambiguity/relay rejection, frame/UTF-8 bounds and
  1,000 hostile frames, commit failure, lost ACK/dedupe retry, ordering, file metadata/
  offsets and background deadline rules.
- Six Python host contract/measurement tests and four original probe JVM tests pass.
- Sixteen app instrumentation tests pass: launch/recreation/control gating and visible
  history area; native two-channel roundtrip/disposal; wrong DTLS fingerprint rejection;
  encrypted identity persistence/tamper/missing-key rejection; explicit trust/changed-key
  blocking; SQLite reopen/dedupe/history deletion and interrupted-send recovery;
  complete verified chat/persistence/reconnect; stale answer/network-change callback
  simulation; background-grace expiry; file streaming/cancel/hash corruption; and a
  connected-idle resource sample.
- File evidence includes 2 MiB streamed through a protected debug-only DocumentsProvider,
  concurrent acknowledged text, final byte/hash comparison, partial deletion on cancel
  and rejection/deletion for a corrupted final SHA-256. This is a fixture provider,
  not proof of every vendor/cloud provider. No test provider is included in release.
- Debug builds, Android lint and signed optimized release builds pass. Release native
  callbacks are kept explicitly. APKs verify with APK Signature Scheme v2; manifest
  has version 0.0.1/code 1 and only INTERNET/ACCESS_NETWORK_STATE permissions.
- Normal Gradle builds enforce checked-in SHA-256 dependency metadata. The runtime
  graph contains only WebRTC. Known TURN relay candidates are rejected; no STUN
  endpoint is contacted by default.

## Milestone evidence

M01 foundation checks passed before M02. M02 standard Maven WebRTC native tests and
size checks passed. M03 product adapter passed policy/native checks. M04 codec/pairing
passed parsing/native checks. M05 identity/trust and DTLS-mismatch tests passed.
M06 native UI passed launch/recreation/send-gating checks. M07 fault-injected commit/
ACK/retry tests passed before M08 SQLite and end-to-end session tests. M09 streaming,
concurrent-text, cancel and corrupted-hash tests passed. M10 simulated network change,
stale-answer rejection and trusted reconnect passed. M11 background policy and emulator
expiry/reconnect passed. M12 prepares signed artifacts, resource evidence and release
checks. Hardware qualification remains deferred for all affected milestones.

## Measurements and release checks

[APK sizes/hashes/native entry contributions](RELEASE_APK_METRICS.json) and
[connected-idle emulator counters](QA_RESOURCE_METRICS.json) are actual observations.
The latter has two peers in one debug/instrumented process: 10,002 ms wall sample,
29 ms process CPU, 42,138 KiB PSS and 2,774 bytes each UID RX/TX delta. These are not
one-phone release/battery/WAN benchmarks. Battery and older-OS measurements are absent.

Release-mode two-process UI/force-stop smoke **passed**, recorded in
RELEASE_SMOKE_RESULTS.json: independent app UIDs generated non-trickle offers/answers,
completed DTLS and explicit matching-fingerprint verification, exchanged a message
with remote persistence ACK, and preserved identity/history across actual force-stop.
The restarted app did not reconnect automatically. Its disposable emulator disables cached-app freezing only
for transport QA, restores the setting afterward, and never logs SDP/text/keys. This
models two foreground phones; it is not a background-delivery or internet test.

## Defects found and corrected

SDK/toolchain setup, legacy android.test classpath, unsupported DataChannel.protocol()
API, modern backup exclusions, stale callback guards, bounded ingress/file worker
count, ACK/state races, wrong-certificate failure detection, verification-dialog
fingerprint race, history-delete late transfer callbacks, network recovery and
background timer suspension handling were corrected during implementation.

A publisher-bundle alternative was investigated for notices. It required JDK 21,
used an inconsistent default native filename and then failed native compatibility;
it was rejected. The app uses the original tested checksum-pinned Maven artifact,
JDK 17 and the publisher's comprehensive retained license collection. No reproducible
source-to-binary proof or independent vulnerability audit is claimed.

## Unperformed cases

All physical-network scenarios in NETWORK_TEST_MATRIX.md: two phones on same/different
Wi-Fi, Wi-Fi/mobile, mobile/mobile, CGNAT/restrictive NAT, IPv6 and UDP-blocked networks.
Network switching tests currently simulate the coordinator event, not a carrier handover.
Process/background fixture tests do not establish OEM Doze or battery behavior.
Older Android runtimes, 16 KB runtime devices, unknown/blocked cloud providers and large
16 GiB transfers are untested. Interrupted transfers restart with new selection and
may require manual partial-document cleanup. A terminated app cannot receive messages.

## Publication

Signed APKs/checksums are approved for Git tracking under release/0.0.1; the signer remains private
under .release-signing. Main commit/tag and GitHub publication are separate final steps.
GitHub access must be authenticated before remote push/asset publication; no token is
stored or printed by the release helper. See RELEASING.md and RELEASE_NOTES.md.

Release-only defect found by installed-APK smoke: org.jni_zero support classes were not covered by the initial JNI keep rule, causing native abort on offer creation after shrinking. Added explicit JNI Zero keeps and annotation/class metadata; the rebuilt signed APK then passed the full two-process release smoke. Only the publisher-omitted, unused JniZeroJni dynamic-loader bridge warning is narrowly excluded; other missing classes still fail the build.

Final automated totals: 16 app JVM tests, 4 probe JVM tests, 6 Python host tests,
and 16 app emulator tests, all passing. RELEASE_TEST_RESULTS.json records JUnit
suite counts without raw logs. Signed-release UI smoke is additional evidence,
not included in those JUnit counts. APK and public signer verification passed for
four ABI APKs and universal, with SHA256SUMS prepared.

Final packaging excludes stale precommit VCS metadata. Every other archive entry,
including all DEX/native/resources/assets, was SHA-256 compared byte-for-byte to
the signed APK that passed release smoke; no runtime payload changed. Final APKs
were signature/permission/package/alignment verified again and rehashed.


## 0.0.2-preview TURN source update (2026-11-01)

The user approved optional relay support after a screenshot reported ICE failure
between real phones on different networks. This does not establish the failure's cause.

- 20 app JVM tests pass, including four TURN configuration boundary tests.
- 18 API 35 arm64 emulator tests pass, including refusal of unconsented relay
  signaling and relay-only failure against an unavailable synthetic loopback endpoint.
- Six Python host contracts pass; Android debug lint and signed release/R8 build pass.
- Five local signed version-code-2 APKs are under ignored `release/0.0.2-preview/`.
  Existing signer, permissions, debug-fixture exclusion and 64-bit alignment checked;
  sizes/checksums are in TURN_PREVIEW_APKS.json. DEX differs from 0.0.1 and includes
  the new TURN UI/failure handling. Original 0.0.1 artifact checksums remain unchanged.
- The first emulator attempt hit a previous release/debug signature conflict before
  running tests; the disposable test installation was replaced and the rerun passed.

Successful TURN allocation, TCP/TLS relay transfer, real two-network connectivity,
server TLS/credential failure cases and full two-peer signed-preview UI smoke remain
untested. No relay is deployed and no TURN hostname/credentials were supplied.
The 0.0.1 signed UI smoke above applies only to the original release.


## Guided pairing UI update (0.0.2-preview)

Start/Join choices replace raw offer/answer controls. Share/Copy actions hide outgoing
SDP, incoming Sharesheet text is validated/prefilled for explicit acceptance, connection
settings are collapsed and fingerprints appear after connection. No transport protocol,
external default server, credential persistence or trust shortcut was introduced.

Validation: 20 app JVM tests, 21 API 35 arm64 emulator tests (three new UI interactions),
six Python host checks and Android lint pass. Signed release/R8 builds pass. Five local
preview APKs were repackaged with matching signer, permissions, fixture exclusions and
64-bit alignment checks; TURN_PREVIEW_APKS.json contains current sizes/checksums.
A fresh installed signed arm64 preview passed launch → Start chat → native offer →
Share invitation UI smoke. This is not a two-device internet or relay test. The original
0.0.1 signed artifacts remain unchanged; the preview is not published to GitHub.

The existing release_smoke.py harness remains specific to the original 0.0.1 UI;
it has not been migrated for full two-peer guided-preview UI automation. Real-phone
usability, relay allocation and real internet-network acceptance remain pending.
