# Validation evidence

TOR_MIGRATION_SPEC.md governs the current product. Sections describing pre-Tor
milestones below are historical evidence, not current supported runtime behavior.
The final Tor qualification summary and measurements are at the end of this file.
Physical phone/network acceptance remains NOT VERIFIED.

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

## Tor M08 — streamed files

Two API 35 arm64 tests PASS (combined build/test 1m10s). Real Tor sessions stream
2 MiB through SAF with explicit acceptance and verified completion ACK; concurrent
text is durably delivered; source/destination SHA-256 match. A second transfer is
cancelled and its destination becomes inaccessible after cleanup. The retained
protocol corruption test independently alters the final hash, rejects completion
and deletes the partial destination. It is a protocol fault-injection test, not a
second real-network result. Transfer workers/input queues and stop-and-wait chunk
ACKs remain bounded; provider-blocked I/O and cleanup refusal remain explicit limits.

## Tor M09 — automatic reconnection

31 JVM tests PASS, including deterministic connection arbitration, jitter/backoff
cap/reset and strict SOCKS5 canonical-domain routing with denied-proxy rejection.
TorReconnectTest PASS on API 35 arm64: verified pairing, peer/Tor shutdown, local
queueing while offline, peer restart, automatic mutual authentication without a
new invitation, and exactly one remotely stored/delivered queued message. Combined
build/test run 34 seconds. Network callbacks and a single exponentially backed-off
retry timer are generation-fenced; retries cap at five minutes. Identity pin
replacement stays blocked. Two simultaneous attempts choose the same lower-route
initiated stream; pending IDs/receipts survive replacement.

An earlier attempt timed out during initial onion connection before exercising
reconnection. The test deadline now covers multiple bounded Tor connection attempts;
that failed attempt is not counted as a reconnection pass. The explicit SOCKS5
adapter avoids DNS, system proxy/authenticator behavior and direct fallback.

## Tor M10 — foreground and Android lifecycle

31 JVM tests, Android lint and TorLifecycleTest PASS on API 35 arm64 (combined
build/test 44 seconds). A shortened deterministic grace verifies a brief app switch
retains a real Tor session/draft; expiry closes networking; foreground return
reauthenticates automatically; explicit disconnect remains stopped on return.
Production grace remains two minutes, checked again after suspension. Network
callbacks are unregistered on shutdown; sockets/workers/native child are closed.
Foreground service categories/restrictions were reviewed against current Android
Developers documentation; no indefinite background service or notification is
claimed. See ANDROID_LIFECYCLE.md. Physical Doze/OEM/battery behavior is NOT VERIFIED.

## Tor M11 — product terminal UI

The product MainActivity/ChatApp now select the Tor-native coordinator. The screen
uses native Views, centralized terminal colors/dimensions/theme, selectable literal
log rows, localized-ready timestamps/status resources, a retained multiline draft,
SAF actions, compact verification/status, Sharesheet and explicit deep-link review.
IP/STUN/TURN/SDP controls are removed from the product flow. Unused legacy source
is retained only until M17 cleanup; it is not selected as a fallback.

Debug build, Android lint and three API 35 UI/manifest tests PASS: no extra dangerous
permissions/backup, primary controls and usable history space, recreation, explicit
invitation import without connection and preserved draft. Theme API-27 attributes
use a versioned resource qualifier so API 26 remains build-compatible. No QR/camera
runtime dependency has been added; private bounded invitation sharing/paste is the
implemented pairing exchange. Physical-device ergonomics remain NOT VERIFIED.

## Tor M12 — Ukrainian and English

Debug build, lint, eight Python host contracts and LocalizationTest PASS. Every
translatable English UI string has a Ukrainian resource with matching format
arguments; Ukrainian one/few/many/other plurals are checked. API 35 native controls
render Ukrainian, bootstrap formatting retains percentages, and English remains
fallback. Resource configuration is exercised with 1.5 font scale; physical
large-font/keyboard ergonomics remain NOT VERIFIED. Android 13+ uses platform
per-app locales; API 26–32 use a private preference/configuration context. Labels,
errors, delivery/file states and locale-aware timestamps/size formatting are
resource-based; wire identifiers and stored state codes remain language-independent.

## Tor M13 — security review and boundary fixes

Reviewed transport bypass/control protection, key/identity persistence, invitations,
TLS trust, cancellation/arbitration generations, storage/ACK failures, framing,
queues, files, logging, exported components, backup and dependency provenance.
SECURITY.md now describes only the Tor-native product and its actual threat model.

31 JVM tests, eight host checks, lint and six API 35 tests PASS: missing established
onion keys fail closed without regeneration; SQLite write failure creates no receipt;
old/new trust partitions remain visible; deleting history preserves replay receipts;
durable capability/trust checks and real Tor messaging regressions pass. Late
pairing callbacks carry a cancellation epoch, invitations also use monotonic expiry,
resource cleanup precedes database operations, and stream record rate is bounded.
Archived pairing labels are localized and do not authorize new peers. Legacy trust
with no onion route can create an invitation for explicit migration verification.
This is a focused implementation review, not an independent native/crypto audit.

## Tor M14 — adversarial automated regression

The full API 35 arm64 suite ran 22 tests: 21 passed, one large-font UI test skipped
because its emulator detector missed uppercase SDK. Corrected that detector and
ran the five focused TLS/legacy-migration, large-font, native runtime and persistent
onion checks: all five PASS, zero skips (24 seconds). Native child shutdown now
exits normally (exit code 0) on both runtime and onion restart tests.

19 current JVM tests and lint PASS in the full regression. Added pre-verification
data rejection, bounded slow-reader/disconnect callbacks and HELLO timeout checks.
Retired obsolete WebRTC-only tests after their Tor counterparts passed. A test-only
Bouncy Castle certificate fixture preserves exact legacy wrapped identity bytes
without depending on WebRTC. Duplicate fixture JAR metadata was resolved by excluding
only the OSGi manifest and merging license notices. Final regression after removing
the obsolete production dependency is recorded separately below.

## Tor M15 — available hardware and physical checklist

2026-11-06 adb inventory exposes only emulator-5554, sdk_phone64_arm64 /
Android SDK built for arm64, API 35. No physical phone is connected. All available
software scenarios have real-Tor emulator coverage; this does not establish
multi-phone Wi-Fi, carrier/CGNAT, radio handover, OEM sleep or battery behavior.
The fourteen physical scenarios and measurement template are in PHYSICAL_TEST_PLAN.md.
Every physical result is NOT VERIFIED; acceptance remains deferred as authorized.

## Tor M16 — Tor-only optimization and regression

Removed obsolete WebRTC transport/identity/signaling/phone-pairing source, its runtime
dependency and shrinker rules, and the probe module. The application-owned coordinator
is now ChatSession. Historical notices and release/0.0.1 artifacts remain untouched.
The preview version is 0.1.0-tor-preview/code4.

A first Tor-only full run completed 22 Android tests: 21 PASS, one initial onion
SOCKS/TLS setup failed after 128.9 seconds. The reconnect test itself passed after
483.7 seconds including initial pairing/restart, demonstrating significant real-network
variance. This run is not counted as a clean pass. The lower-level harness now retries
only bounded SOCKS setup failures; authentication errors/partial authenticated
connections remain fatal. Safe fixed diagnostics distinguish SOCKS failures without
logging routes or capabilities. A new final run includes the additive schema migration,
missing-runtime fail-closed check and performance measurement test.

Schema2 adds transfer creation time without rewriting old rows/receipts; unknown
legacy times stay zero. Transfer progress updates preserve timestamps. Native UI
merges bounded text/file rows chronologically, retains cleanup warnings and marks
interrupted transfers honestly. Language bundle splitting is disabled to keep both
locales available. Runtime queues/crypto limits are unchanged.

The synthetic empty terminal preview at 1.5 font scale was retrieved from the test's
View.draw output and visually reviewed: actions/composer/history space remain usable.
It is local ignored build output, not a capture of user conversation data. Arm64 Tor
and Conscrypt ELF PT_LOAD alignments are all 16384 bytes; no 16KiB-device runtime
acceptance is inferred. Final APK hashes and measurements follow below.

### Measured real-Tor sample

TOR_PERFORMANCE.json records one API35 arm64 debug instrumentation run with two
independent Tor clients in one warmed application UID. Both bootstrap 5,248 ms;
both descriptor publication 7,860 ms; join-to-bilateral-verification/HELLO 5,500 ms;
start-to-authenticated 13,360 ms. The fixture confirms identities automatically;
these timings exclude human comparison time. The 2,097,152-byte transfer completed
in 107,951 ms (19,426 bytes/s) with concurrent text durably acknowledged.

Connected idle: app PSS 67,879 KiB and both Tor children together 52,059 KiB.
Active transfer: app 68,336 KiB and both children 52,039 KiB. Samples use
Debug.MemoryInfo and dumpsys meminfo PID respectively; they are not per-phone or
minified-release estimates. No GC was forced. One stop-and-wait 16KiB file chunk
keeps buffering small and control responsive, at an observable throughput cost
under Tor RTT. No speculative windowing/security relaxation was introduced.
Idle battery/radio/OEM memory behavior remains NOT VERIFIED. This single fast
bootstrap sample does not erase the slow/retried connection run recorded above.

Retained exact upstream Conscrypt NOTICE plus Netty/Harmony notices, BoringSSL
license at revision 427ec40cc8edc545253289232e885708c409e5ea (read from the pinned
AAR's conscrypt.properties), and jtorctl 0.4.5.7 BSD license in assets/license UI.

### Final automated regression

Full Tor-only command: assembleDebug, assembleRelease, testDebugUnitTest, lintDebug
and connectedDebugAndroidTest. PASS, 10m17s. Android XML: 25 tests, zero failures,
errors or skips; JVM XML: 19 tests, zero failures/errors/skips. Eight Python host
checks PASS. This includes genuine Tor runtime/onion publication, mutual TLS,
text ACK/history, file hash/cancel, verified restart/reconnect, lifecycle and UI,
plus deterministic hostile-input/backpressure/storage-failure boundaries. The
separate low-level transport test succeeded on the final run in 21.9 seconds.

Final notice-only asset/license-screen additions were rebuilt in debug and signed,
minified/resource-shrunk release; lint remains zero errors, three warnings. Reviewed
warnings: API33 localeConfig ignored on older APIs with a separate fallback; bundle
locale-delivery detector despite APK-bundled languages/disabled language splits;
intentional exact-certificate X509 trust manager (validity/self-signature/curve/pin
and TLS1.3 downgrade rejection covered by tests). No broad trust warnings suppressed.

### Final local APK inventory

TOR_APK_METRICS.json records all ten fresh Gradle output-metadata-selected APKs,
SHA256, byte sizes and exact native inventories. Every APK passed manifest/version/
permission, bundled notice, no-WebRTC-dex and ZIP alignment checks. The only native
libraries are libtor.so and libconscrypt_jni.so for the selected ABI(s). Release
manifests have no debug provider/debuggable flag. All five release signatures were
verified using apksigner and the established certificate SHA256
2e6381e1b46492b37b3f13c8ba337fac36ae49a119cfc8f9d69a928f6168df9b.

| ABI | Debug bytes | Signed minified release bytes |
| --- | ---: | ---: |
| arm64-v8a | 7,506,488 | 4,533,246 |
| armeabi-v7a | 7,385,440 | 4,451,278 |
| x86 | 7,764,840 | 4,754,582 |
| x86_64 | 7,928,062 | 4,886,444 |
| universal | 21,750,998 | 17,655,476 |

These are local preview artifacts under ignored build outputs, not published APKs
or replacements for historical release/0.0.1. All ABIs built; only arm64 runtime
executed. Minification saves about 2.97 MB for arm64 while preserving native TLS/Tor.

The initial signed UI harness attempt reached invitation readiness but could not
select a popup label because it required the label itself to be clickable. Fixed
the parent-row hit-testing assumption. A second attempt timed out during Tor setup
at the harness's 110-second wait. The revised harness bounds waits to 360 seconds
and periodically brings both activities foreground within the unchanged production
grace, modeling two open phones on one emulator; native/authentication gates are
unchanged. Neither failed harness attempt is recorded as a UI acceptance pass.

A streamed adb install also stalled before a later attempt began; the harness now
uses non-streamed installation. A subsequent cross-UID authentication wait still
timed out despite foreground switches and no crash reports. This is not a signed
UI pass. Added an in-memory check that pasted invitation fingerprint bytes match
the current creator's full displayed identity, without logging either value, to
separate clipboard automation from actual Tor/TLS behavior.

### Final release diagnostic investigation

Added localized, fixed SOCKS/TLS/greeting stage and exception-class codes to the
opt-in diagnostics screen. No payloads, routes, capabilities or key material are
included. The clipboard invitation matched the creator's current fingerprint.
A release diagnostic reported SocketTimeoutException; this alone does not locate
which connection stage timed out. Three foreground-policy assumptions in the first
single-emulator harness were not accepted as physical-phone evidence.

A second disposable API35 arm64 emulator was created for two ordinary signed app
installations. Both were confirmed awake and actually resumed via fixed boolean
checks of activity/power state. The first two-emulator UI wait did not complete and
was stopped during diagnostic collection. Added exact invitation input/closed-dialog
checks before treating later timeouts as network failures. Final acceptance result
is recorded separately; none of these failed attempts is counted as passing.

The exact-input guard located a pre-network UI failure: Connect did not dismiss
the long invitation dialog. The dialog content had unbounded wrapping and lacked
its own IME resize policy. Bounded the field to four visible lines after input-type
configuration, wrapped custom content in a ScrollView and enabled dialog resize.
Two focused PairingUiTest checks PASS (12s), including a real visible keyboard and
actual injected touch on Connect above the IME, followed by dialog dismissal.
This regression uses native events rather than performClick, which had missed the
interaction. The private invitation import remains explicit and validation unchanged.
Final signed UI acceptance is rerun after this correction.

### Signed release UI acceptance and cold start — PASS

TOR_UI_SMOKE.json records the corrected ordinary signed/minified/resource-shrunk
release on two separate API35 arm64 Android emulator installations. Both apps stay
foreground; production background policy is unchanged. Actual Android Sharesheet,
exact private invitation entry and explicit Connect with the keyboard visible,
full bilateral fingerprint comparison, verification, keyboard-visible Send,
durable text ACK, receiver display, force-stop/reopen history and automatic
mutually authenticated reconnect all PASS. Bundled license UI also opens.
No invitation, route, key or message content is logged by the harness.

The long-invitation keyboard issue was fixed rather than reclassifying failed
runs as network success. The full earlier suite is 25/25 PASS; two focused UI
checks after the fix PASS, including the newly added real IME/touch regression.
Current 19 JVM tests, eight host contracts, debug/signed release builds and lint
PASS after the final fix. Lint remains zero errors, three reviewed warnings.
This is not a claim that a new 26-test full run or physical acceptance was executed.

Signed minified release cold-process UI am start -W TotalTime: 110, 98 and 98 ms
(median98 ms). Force-stop between launches; existing synthetic identity/history,
warm installation/disk caches. Tor starts asynchronously, so these numbers do not
measure bootstrap or network readiness. TOR_PERFORMANCE.json retains the separate
real-Tor network/memory/transfer sample and its exact environment.

All ten final APKs were re-audited after the fix, including all five established
release signatures. SHA256/bytes are in TOR_APK_METRICS.json; local downloadable
checksums are app/build/TOR_SHA256SUMS. APKs stay ignored/local and are not published.

### Remaining acceptance limits

All physical checklist scenarios remain NOT VERIFIED. API26 and non-arm64 runtime,
real Wi-Fi/mobile/CGNAT/handover, long OEM/Doze sessions, physical large files and
idle battery/radio consumption require hardware. Tor availability/latency varies;
keep both apps foreground. No push, permanent background execution, terminated-app
incoming delivery, file resume, QR scanner or anonymity/independent-audit guarantee.
Native source-to-binary reproducibility and newer Tor core fixes remain upstream
qualification/update work, not an invented successful audit. See SECURITY.md,
TOR_RUNTIME.md, ANDROID_LIFECYCLE.md and PHYSICAL_TEST_PLAN.md.
