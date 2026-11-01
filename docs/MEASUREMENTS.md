# Measurements and Reproduction

## Actual observations

See RELEASE_APK_METRICS.json for signed per-ABI/universal APK byte counts, SHA-256
hashes and native entry compressed/uncompressed bytes. Baseline M01/M02 metric JSONs
are historical prototype measurements, not final app sizes. WebRTC native code is the
largest contribution; AndroidX/JUnit are absent from the app runtime graph.

QA_RESOURCE_METRICS.json records API 35 arm64 debug instrumentation with two connected
peers in one process: 42,138 KiB PSS and 29 ms CPU during a 10,002 ms idle sample;
UID RX and TX counters each increased by 2,774 bytes. Do not extrapolate battery life,
WAN overhead or one-phone release RAM from this emulator sample. Release startup/native
UI observations are in RELEASE_SMOKE_RESULTS.json; the two-process signed UI smoke passed.
Physical battery, thermal, startup-repeat and 16 KB runtime measurements are absent.

## Reproduction

Build the same variant/ABI/configuration; record JDK/SDK/device/OS/ABI and power state.
Use `python3 tools/apk_metrics.py path/to.apk` and Gradle runtime dependency reports.
Compare like-for-like APKs; shrinker deltas include application changes, not just the
library. `tools/package_release.py` verifies signatures, package/permission boundaries
and ZIP alignment before staging artifacts/checksums.

PerformanceTest samples Process.getElapsedCpuTime, Debug.getPss and UID TrafficStats
over a fixed connected-idle interval. It writes only aggregate counters, never private
payloads. Device-test runners may uninstall apps afterward; retrieve the artifact before
cleanup or run that test directly through adb. For actual phone performance, repeat
controlled cold starts (`am start -W`), PSS (`dumpsys meminfo`), idle/transfer CPU/traffic
and battery intervals against an idle baseline. Record failures and variance.

## Signed 0.0.1 artifacts

| ABI | APK bytes |
| --- | ---: |
| arm64-v8a | 12,563,657 |
| armeabi-v7a | 7,089,431 |
| x86 | 13,095,947 |
| x86_64 | 16,442,454 |
| universal | 48,325,984 |
