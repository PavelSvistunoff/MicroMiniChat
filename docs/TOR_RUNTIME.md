# Embedded Tor integration

**Qualification blocked:** actual onion-service testing reproduces a native
mutex abort. The earlier startup-only smoke does not qualify this distribution
for product use. See TOR_BLOCKER.md. This code is not selected by ChatSession.

## Candidate and provenance

Guardian Project's published `info.guardianproject:tor-android:0.4.9.14` AAR is
12976254 bytes. It bundles
arm64-v8a, armeabi-v7a, x86 and x86_64 libtor.so. Native uncompressed bytes total
30,075,596. Maven AAR SHA-256:
`28ff2bb6d4d42eabe57183dedd7a97fce77e8ce4b6f5af6fa03993b3ca054edd`.

The AAR requires compile SDK 37.1 and contains Java class major 68 (Java 24).
The checked-in source adapter recompiles the published source bridge using this
repository's Java 17/SDK 35 toolchain; only checksum-verified native libraries are
extracted from the AAR at build time. The installed app downloads no executable.
See `third_party/tor/README.md` and `TOR_LICENSE.txt` for attribution.

`TorRuntime` binds a nonexported local service asynchronously, disables the HTTP
proxy, restricts SOCKS to loopback, suppresses Tor logs and parses actual bootstrap
progress through the private Unix control socket. Control authentication relies
on the Android UID-private directory, not a world-accessible TCP control port.
Bootstrap timeout is three minutes. This candidate is not wired into ChatSession
until native lifecycle validation passes. Native startup, shutdown and restart passed on the API 35 arm64 emulator.
Full bootstrap, connectivity recovery, memory, battery, APK delta and physical
networking are NOT VERIFIED at M02.

## Secure updates and source-build strategy

Review upstream security announcements before adopting a version. As of this
investigation Maven Central metadata lists .14; Tor Project has subsequently
released .15 and .16. Review applicability of fixes; do not claim .14 is latest.
Do not downgrade to the end-of-life 0.4.8 series to work around build compatibility.

1. Resolve the exact Guardian Project source revision and all recursive submodule
   commits, never a moving master. Retain the source archive and dependency notices.
2. Verify upstream Tor release signatures using independently verified release-key
   fingerprints. Local artifact SHA-256 pinning alone is not a signature or proof
   of source-to-binary reproducibility.
3. Use the upstream `external/Makefile` native build in an isolated environment
   with pinned NDK/host toolchain. Record versions, environment, patches and every
   submodule SHA. Build all four declared ABIs explicitly (upstream now excludes
   x86 from its defaults). Never run upstream `fetch -c` inside this application
   repository: that command resets and cleans its checkout.
4. Compile the bridge from reviewed source with Java 17 and SDK 35; retain JNI
   class/field names. Review service locking, shutdown races, logs, manifest and
   private socket permissions. Avoid silently stripping AAR compatibility checks.
5. Build twice independently; compare native hashes and investigate differences.
   No reproducible-build result is claimed before this is executed.
6. Update Gradle's explicit AAR SHA-256, dependency verification and bridge source
   together. Run native startup/bootstrap/failure/shutdown/restart tests on API 26
   and 35 and required ABIs. Measure APK size/PSS. Update evidence before adoption.

Primary sources:
- https://github.com/guardianproject/tor-android
- https://repo.maven.apache.org/maven2/info/guardianproject/tor-android/maven-metadata.xml
- https://lists.torproject.org/mailman3/hyperkitty/list/tor-announce%40lists.torproject.org/latest
- https://blog.torproject.org/sunsetting-tor-048/

## Persistent service candidate

Tor manages the v3 secret/public keys in the UID-private no-backup onion-v3
folder. A committed creation marker makes a missing previously established secret
key an error. OnionAddress checks canonical Base32, version 3 and the SHA3-256
checksum using Bouncy Castle bcprov-jdk18on 1.86 (confirmed published coordinates;
Android's provider lacks SHA3). The library is checksum-pinned; its license is
bundled. No provider is globally replaced. Keys are not claimed hardware-backed.

The candidate maps virtual port 443 to a supplied loopback listener. It enables
networking after installing actual HS_DESC listeners. Only the matching UPLOADED
event reports publication; neither hostname creation nor bootstrap alone does.
The reset method requires callers to obtain confirmation and refuses while Tor
is running. Product UI wiring/reset confirmation remain pending M11.
