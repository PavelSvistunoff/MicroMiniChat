# Tor measurements and reproduction

Current artifact evidence: [TOR_APK_METRICS.json](TOR_APK_METRICS.json).
Network/memory/transfer sample: [TOR_PERFORMANCE.json](TOR_PERFORMANCE.json).
Signed native UI acceptance: [TOR_UI_SMOKE.json](TOR_UI_SMOKE.json).
Methods, failed attempts, final outcomes and qualification limits are in
[VALIDATION.md](VALIDATION.md).

Use tools/apk_metrics.py for byte counts, hashes and native entries, and
tools/check_tor_apks.py for current-output manifests, native inventory, notices,
alignment and optional expected-signer verification. Use the JDK17/SDK35 pinned
build and record the exact APK hash, variant, API, ABI and measurement conditions.

The real-Tor sample uses two clients in one warmed debug process: timings exclude
human comparison and PSS includes both native children separately. It predates the
final Join-dialog keyboard correction and is not a minified-release benchmark.
Cold-process release UI launch samples retain an existing identity/history and
exclude asynchronous Tor readiness. Do not extrapolate battery life, physical
throughput or per-phone memory from these samples. Physical battery, OEM/Doze,
API26 and non-arm64 runtime qualification remain NOT VERIFIED.

See [release instructions](RELEASING.md) and [physical checklist](PHYSICAL_TEST_PLAN.md)
for reproducible commands and the remaining acceptance procedure.
