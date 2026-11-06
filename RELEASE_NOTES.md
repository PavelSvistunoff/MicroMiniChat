# MicroMiniChat 0.1.0-tor-preview

Complete Tor-only Android migration: bundled persistent v3 onion services, mutual
TLS1.3, single-use private invitations, bilateral identity verification, automatic
verified-peer reconnect, durable text ACK/deduplication and consented SAF file streaming.
Native terminal UI with English/Ukrainian resources and retained drafts.
Only the bundled Tor transport is supported.

Preview qualification is in docs/VALIDATION.md. Real Tor emulator checks are distinct
from physical-phone/mobile/CGNAT/OEM battery acceptance, which remains NOT VERIFIED.
Keep both apps open. Tor may be blocked or slow, files restart from zero after
interruption, and providers may refuse partial cleanup. No push or terminated-app
delivery, independent security audit, or universal connectivity claim.

Existing history/trust/identity are retained where safe; legacy peers require explicit
Tor route verification. Install upgrades with the same APK signer. Historical signed
0.0.1 artifacts remain immutable and use the superseded architecture.

## Downloads and installation

Five APKs are attached: arm64-v8a, armeabi-v7a, x86, x86_64 and universal.
Use the matching ABI, or universal if unsure. Android8/API26 is the compile minimum;
executed runtime qualification covers API35 arm64 only. Install updates with the
same signing identity; do not uninstall an existing installation to bypass a signer
mismatch, because that removes private identity/history. SHA256SUMS and SOURCE.json
bind the downloadable assets to the source and established signing certificate.

## Qualification

Fresh signed release build, 19 JVM tests, eight host checks, ten-APK manifest/native/
alignment audits and all five release signatures pass. Lint: zero errors and three
reviewed warnings. The exact newly signed arm64 APK passed native UI pairing,
bilateral verification, keyboard-visible Connect/Send, durable ACK, history after
force-stop and automatic authenticated reconnect across two foreground emulators.
The earlier 25-test full Android run and subsequent two focused UI tests are in
[validation](docs/VALIDATION.md); a new 26-test full run is not claimed.

Bundled Tor Android is 0.4.9.14, the currently published Maven Central artifact.
Newer core releases exist; this preview does not claim the newest core or an
independent security audit. Runtime update qualification is documented in
[runtime updates](docs/TOR_RUNTIME.md).

Physical Wi-Fi/mobile/CGNAT, battery/OEM/Doze, API26 and non-arm64 runtime remain
NOT VERIFIED. This is a prerelease, not general physical-device acceptance.
