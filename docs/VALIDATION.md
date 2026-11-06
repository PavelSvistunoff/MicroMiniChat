# Tor validation evidence

Implementation/qualification commit: 4cb3492; documentation completion: bc18534.
Physical acceptance remains deferred. These results distinguish actual execution
from unavailable hardware and do not imply universal connectivity or an audit.

A first Tor-only 22-test run had one initial connection setup failure after 128.9s;
21 passed. The reconnect case passed after 483.7s including initial pairing/restart.
That failed run is not counted as a clean pass. The final full regression below
passed, followed by focused real keyboard/touch checks and signed native UI acceptance.

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
bootstrap sample does not erase other slow/retried connection runs.

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
permission, bundled notice, approved executable content and ZIP alignment checks. The only native
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


## Documentation cleanup

Current guides describe only the Tor product. Retired setup/dependency guides and
superseded Markdown evidence were removed; immutable signed artifacts and required
license notices remain preserved. The migration specification retains its approved
Tor architecture, trust/history preservation and validation requirements.

No backend deployment, APK publication, release/tag creation or user data/identity
reset occurred. Historical signed artifacts remain untouched. The unrelated staged
.DS_Store remains excluded from development commits.

Documentation-only follow-up: on explicit user request, removed retired implementation
references across all repository Markdown, including obsolete wording in the migration
specification. Its Tor-only architecture, security gates, history preservation and
acceptance requirements remain intact. No runtime or artifact changed.
