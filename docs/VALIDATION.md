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


## 0.0.3-preview phone-hosted pairing

Latest scope: creator hosts the temporary TLS signaling listener; no public signaling
service was added. Invitation links contain numeric IP/port, certificate pin and access
token. Join remains explicit; offer/answer return automatically over pinned TLS.

23 app JVM tests, 32 API 35 arm64 emulator tests and six Python host checks pass;
Android lint passes. Coverage includes native automatic pairing with durable text ACK,
TLS pin/token rejection, incomplete-authentication recovery, oversized frames, wrong
sessions, cancellation/network changes, deep-link review and actual certificate expiry.
No emulator result establishes public internet reachability or two-phone interoperability.

The expiry test exposed a real units defect: 3600 requested from the native certificate
generator produced approximately three seconds, not one hour. Passing explicit
milliseconds now produces the intended hour. Persistent identity generation also now
requests 365 days correctly; existing identities are retained until their actual expiry.
The incomplete-authentication recovery test and full regression suite pass after this
fix. Absolute TCP shutdown deadlines also bound incomplete TLS/authentication reads.
See IDENTITY_DESIGN.md for older-build expiry and explicit-reset implications.

Five signed local preview APKs were produced with R8; signer, permissions, debug-fixture
exclusion and 64-bit alignment checks passed. PHONE_PREVIEW_APKS.json records sizes and
hashes. Original 0.0.1 artifact checksums remain unchanged. The preview is not a published
GitHub release. No new physical-network, TURN allocation or battery result is claimed.


Signed two-UID phone-pairing smoke passed: creator starts a TLS listener; the invitation
moves through the real clipboard into the other app; offer and answer exchange without
manual reply sharing; full persistent fingerprints match across both views; a text is
persisted/acknowledged across UIDs; force-stop retains history without automatic reconnect;
a fresh listener starts after restart. PHONE_PAIRING_SMOKE.json records the result.
Cached-app freezing was temporarily disabled solely to model two foreground phones and
restored afterward. This remains one emulator with two signed app UIDs, not two internet
networks. The separate QA package was removed and the main package stopped afterward.

## Tor migration — M01

Read TOR_MIGRATION_SPEC.md and AGENTS.md completely, inspected app/probe source,
root product/security/protocol documents and tooling. Created dedicated
`feature/tor-migration` branch (checkout initially on main).
`python3 -m unittest discover -s tools -p 'test_*.py'`: 6 PASS.
These are existing host contracts, not Tor runtime/network validation.
Runtime selection remains under investigation; no Tor APK or physical result yet.

## Tor migration — M02 runtime integration

Candidate runtime: Guardian Project tor-android 0.4.9.14, four bundled ABIs,
SHA-256 verified before native extraction. Recompile the published Java bridge to
retain Java 17/SDK 35 compatibility; no runtime executable downloads. Source
adapter suppresses exception details, uses a private control socket, keeps native
ownership until the native run returns and shuts down control off the main thread.
See docs/TOR_RUNTIME.md for provenance/update/build strategy and limitations.

- Debug APK build: PASS.
- App JVM suite: 24 PASS (23 existing + bootstrap parser boundary test).
- Android lint: PASS.
- Instrumentation APK build: PASS.
- TorRuntimeIntegrationTest: PASS on API 35 arm64 AVD; two actual native runtime
  starts, actual bootstrap progress events, local SOCKS availability, shutdown and
  restart. This is not a complete bootstrap or cross-device onion test.
- SDK/platform/build-tools/cache directories initially lacked required files;
  restored official SDK tools (archive SHA-256 checked against Android Developers)
  and SDK 35/build-tools/emulator image in temporary storage. No toolchain committed.
- Physical devices/network matrix, all other ABIs and API 26: NOT VERIFIED.

TorRuntime is intentionally not yet selected by ChatSession: the following
milestones replace onion routing, pairing, identity and transport as one product.

## Tor migration — M03 blocked; M02 qualification reopened

Implemented the candidate persistent no-backup onion identity, missing-key guard,
strict v3 version/checksum address validation, loopback service mapping, and actual
HS_DESC UPLOADED publication gate. Added JVM address-validation vectors and a real
onion publication/restart integration test. Android's missing platform SHA3 was
fixed using verified published bcprov-jdk18on 1.86; checksum metadata/license added.

**FAIL:** OnionIntegrationTest crashes the process with a native FORTIFY destroyed
mutex abort (SIGABRT), including tor_cond_wait in the native stack. Reproduced with
the initial source adapter and with the preliminary native verify-config pass
removed. The ineffective workaround was reverted. Root cause is not established.
See TOR_BLOCKER.md. The initial M02 lifecycle smoke remains historical evidence,
not successful qualification for persistent onion services.

M04–M17 are pending. Physical-device scenarios and performance acceptance are
NOT VERIFIED. No production Tor session, pairing, mutual TLS or terminal UI is
claimed. Current experimental debug artifacts still select the existing session.

Final blocked-candidate host recheck: debug/instrumentation builds and lint PASS;
25 JVM tests PASS, 6 Python tests PASS. APK sizes/hashes are recorded in
TOR_CANDIDATE_ARTIFACTS.md. The native onion test remains FAILED.

## Resumed M02/M03 — runtime blocker resolved

Run the bundled libtor.so executable in a separate owned process instead of
in-process JNI. Use fresh process state on every restart, loopback cookie control,
TAKEOWNERSHIP and owning PID, suppressed output, bounded asynchronous cleanup.
Real OnionIntegrationTest PASS on API 35 arm64: descriptor publication, child
shutdown/restart and unchanged onion identity. BUILD SUCCESSFUL in 19 seconds.
This supersedes the blocked qualification above; physical testing remains pending.

## Tor M04 — invitations and durable pairing primitives

27 JVM tests PASS, including invitation canonical roundtrip, immutable capability,
version/size/encoding/expiration rejection. Two API 35 emulator tests PASS for
wrong-capability rejection, durable single-use consumption across reopen,
expiration/cancellation and atomic route trust with identity-replacement blocking.
Human verification/UI and transport binding are integrated in subsequent milestones.

## Tor M05 — persistent mutual TLS

Two API 35 arm64 TLS tests PASS: mutual TLS 1.3/ALPN, new key continuity across
reopen, wrong server/client pins rejected, and migration preserves the legacy
certificate fingerprint and exact encrypted file bytes. The initial Keystore
signing-policy mismatch was fixed by authorizing DIGEST_NONE for Conscrypt's
standard prehashed ECDSA operation. Conscrypt 2.8.0 is pinned from its actual
published AAR with retained license. No TLS downgrade/fallback is configured.

## Tor M06 — real onion/TLS framed transport

PASS: TorTransportIntegrationTest operates two independent embedded Tor clients,
persistent onion directories, actual descriptor publication, invitation capability,
mutual TLS and framed synthetic text through the Tor network. Product sending is
refused before verification; one-sided verification stays unready; two-sided
verification permits the frame. This is an emulator/one-host Tor-network test,
not a physical cross-network result. JVM suite (28 tests) and Android lint PASS.
Total combined build/test execution: 1m16s, not a bootstrap-only measurement.

## Tor M07 — durable session messaging

PASS: 28 JVM tests and TorSessionTest on API 35 arm64. The new coordinator pairs
through two real Tor clients, requires bilateral verification, sends text, observes
a durable remote ACK with one display row, and reopens the same database/history.
The stream now calls write completion only after flushing a TLS record: Sent is
separate from Sending; Delivered still requires the receiver's durable receipt.
Recovery treats interrupted Sending/Sent rows as Failed; retry retains IDs/sequences.
Tor ACK deadline is 60 seconds; the existing default protocol tests retain 15 seconds.
Schema v1/history filenames and fingerprint partitions are unchanged.

An initial real-network session attempt hit its connection deadline; the rerun
passed in a 21-second combined build/test run. This is not universal connectivity
or a bootstrap benchmark. Automatic backoff/recovery follows M09.
