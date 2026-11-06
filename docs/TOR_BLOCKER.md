# Resolved runtime qualification blocker

The resumed migration replaced in-process JNI invocation with the upstream
bundled executable running as an owned child process. The Java 24 bridge is no
longer compiled or used. Native globals are initialized once per process and each
restart launches a new process. Cookie-authenticated loopback control, TAKEOWNERSHIP
and OwningControllerProcess bind its lifetime to the application. Logs go to
/dev/null. The published AAR hash/ABIs are unchanged.

OnionIntegrationTest now PASSES on API 35 arm64: actual descriptor publication,
shutdown, new child process, publication and unchanged onion identity. This removes
the implementation blocker. The previous observations below remain historical;
the native mutex root cause itself is not claimed fixed upstream.

---

# Historical native Tor qualification blocker

## Status

Migration is incomplete. M01 is complete; M02 qualification is reopened and M03
is blocked. M04–M17 have not been implemented. The candidate runtime is deliberately
not selected by ChatSession. No usable Tor messenger or Tor release is delivered.
No existing history or identities were reset and no infrastructure was deployed.

## Reproduction and observations

Environment: API 35 arm64 Android emulator, default AOSP system image revision 2,
JDK 17, SDK/build-tools 35, checksum-pinned Gradle 8.11.1. Bundled native candidate:
Guardian Project info.guardianproject:tor-android:0.4.9.14.

The published AAR requires SDK 37.1 and Java 24 class files. The source adapter
compiles its published Java JNI bridge using Java 17 and packages its original,
checksum-verified native libraries. It is a candidate integration, not a claim
of source-to-binary reproducibility or an independent native audit.

Initial TorRuntimeIntegrationTest passed startup, SOCKS availability, native
shutdown and restart on API 35 arm64. It observes bootstrap progress, not complete
bootstrap/onion service operation. That result did not reveal a later native abort.

OnionIntegrationTest adds a real persistent onion service, validates its hostname,
waits for actual descriptor publication, restarts and compares the routing identity.
The first run correctly failed because Android lacks SHA3-256. Using the maintained,
pinned Bouncy Castle implementation fixed that address-validation error; the JVM
checksum test and lint then passed.

The real native onion test subsequently crashed the application process. Android
FORTIFY reports `pthread_mutex_lock called on a destroyed mutex`, terminating with
SIGABRT. The native stack includes pthread_cond_wait and tor_cond_wait in libtor.
A second run with the bridge's preliminary --verify-config invocation removed
also crashed. That attempted workaround was reverted because it did not solve the
problem. Do not infer an established native root cause from the backtrace.

No keys, network addresses, invitation capabilities or raw native dumps are saved
in this report. Full onion publication and persistence across a successful restart
have NOT been established. A process crash is a failed test, never a timeout pass.

Reproduce with:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.pavelsvistunoff.microminichat.OnionIntegrationTest
```

## Work required to unblock

Obtain/build a verifiable Android native Tor distribution that passes this test
and native lifecycle tests. The source-build/update qualification procedure is in
TOR_RUNTIME.md. It requires a pinned source/submodule/toolchain manifest, reviewed
JNI/lifecycle adapter, native symbols for diagnosis, all declared ABIs, and actual
onion publication plus clean restart. Process isolation may contain a native crash
but does not itself qualify onion networking or fix the underlying failure.

Maven Central metadata checked during this work lists .14 as the newest Android
artifact. The Tor Project has released .15/.16 since then; fix applicability must
be reviewed when selecting a replacement. No version was invented and an older
end-of-life runtime was not substituted. No alternate binary has been qualified.

Continue from the failing integration gate before removing WebRTC or wiring new
pairing/authentication into the product. The remaining implementation roadmap is
TOR_MIGRATION_PLAN.md; its pending items are not implemented features.
