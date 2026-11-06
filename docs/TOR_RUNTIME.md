# Embedded Tor runtime

## Bundled executable and provenance

Guardian Project Maven artifact info.guardianproject:tor-android:0.4.9.14@aar,
12,976,254 bytes, SHA-256
`28ff2bb6d4d42eabe57183dedd7a97fce77e8ce4b6f5af6fa03993b3ca054edd`.
It supplies libtor.so for arm64-v8a, armeabi-v7a, x86 and x86_64 (30,075,596
uncompressed native bytes total). Gradle verifies the artifact and extracts only
jni libraries at build time. Its Java 24/SDK 37.1 adapter is not imported.
JDK 17/SDK 35 remain the application toolchain. No runtime executable downloads.

Android legacy JNI packaging extracts the executable into nativeLibraryDir.
ProcessBuilder runs it as a fresh child on each start. Loading Tor JNI repeatedly
in-process caused a reproducible native mutex abort during qualification; that
approach was removed. See TOR_BLOCKER.md for the resolved investigation.
This child shares the app UID: crash/lifecycle containment, not a separate sandbox.

Tor state and persistent v3 keys live under private no-backup storage. Missing
established secret keys fail closed. Tor keys are not claimed hardware-backed.
Virtual onion port 443 maps only to the app's ephemeral loopback TCP listener.
SOCKS/control bind 127.0.0.1:auto; control requires the private 32-byte cookie.
HTTP proxy is disabled, output discarded, no addresses/content/configuration UI.
TAKEOWNERSHIP plus the owning parent PID constrain orphan lifetime. Close releases
control and reaps the child asynchronously; after five seconds termination escalates,
then after another two seconds forced termination. Normal exit code 0 is tested.

Bootstrap polls actual status every second with a five-minute initial deadline.
Onion readiness requires a matching HS_DESC UPLOADED event, not hostname creation
or bootstrap alone. Remote streams use explicit loopback SOCKS5 domain requests
for checksum-valid v3 onions; no DNS/system proxy/direct fallback.

## Qualification and tradeoffs

API 35 arm64 emulator: actual start/shutdown/restart, descriptor publication with
stable keys, two independent Tor clients, mutual TLS and text/files/reconnect PASS.
API 26, other ABIs, physical networks and battery remain NOT VERIFIED. Four ABI
builds are produced; this is not a claim of runtime execution on all architectures.
Tor dominates native size/memory and adds bootstrap/latency costs. API-native TLS
on API 26 lacks the selected TLS 1.3 contract; pinned Conscrypt Android 2.8.0 provides
it. Bouncy Castle bcprov 1.86 supplies SHA3 address checks; jtorctl 0.4.5.7 supplies
standard control protocol. No custom crypto primitive or handshake is introduced.

## Secure update procedure

The inspected Android AAR .14 is not claimed to be the newest Tor core. Core
.15/.16 announcements exist; review security applicability before distribution.
A checksum ensures the selected bytes, not reproducible source-to-binary provenance.

1. Review Tor/Guardian security notices, release signatures and dependency advisories.
   Resolve exact release coordinates, Guardian source revision and recursive submodule
   SHAs; verify upstream signing-key fingerprints independently.
2. Acquire the exact AAR from Maven Central, record hash/native inventory/licenses and
   source-to-binary evidence. Inspect executable compatibility on every declared ABI.
   Reject missing provenance rather than silently changing artifact/version.
3. If rebuilding, isolate upstream external/Makefile with pinned NDK/host tools and
   submodules; explicitly enable all four ABIs (upstream defaults can omit x86).
   Record patches/environment; build independently twice and compare native hashes.
   Do not run destructive upstream fetch/reset commands in this repository.
4. Review child arguments, control-cookie permissions, logs, ownership and executable
   extraction. Keep the Java adapter excluded unless separately qualified for JDK17.
5. Update Gradle coordinates, explicit AAR digest, verification metadata and notices
   together. Review every new checksum; never regenerate blindly for unreviewed bytes.
6. Run host tests/lint, minified release packaging, native lifecycle/bootstrap/failure,
   persistent onion, TLS mismatch/downgrade, real two-client text/files/reconnect and
   lifecycle suites. Execute API26/35 and all required ABI runtime checks when hardware
   exists. Record APK sizes, PSS, performance and physical gaps before adoption.

Sources: https://github.com/guardianproject/tor-android ;
https://repo.maven.apache.org/maven2/info/guardianproject/tor-android/maven-metadata.xml ;
https://lists.torproject.org/mailman3/hyperkitty/list/tor-announce%40lists.torproject.org/latest ;
https://blog.torproject.org/sunsetting-tor-048/ .
Licenses/provenance are retained in third_party/tor.
