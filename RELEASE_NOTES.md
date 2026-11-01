# MicroMiniChat 0.0.1 preview

First functional Android preview for two-person direct text/file messaging:

- WebRTC DTLS/SCTP with ICE and explicitly configured STUN; no backend or TURN.
- Copy/paste and Android Sharesheet offer/answer pairing.
- Persistent encrypted-at-rest identity, independent fingerprint verification and
  changed-peer blocking.
- Remote-persistence delivery ACKs, ordered retries/deduplication and SQLite history.
- SAF 16 KiB streaming, progress/cancel, SHA-256 verification and completion ACKs.
- Explicit reconnect and bounded background grace, without push/foreground services.

**Preview qualification:** automated and API 35 arm64 emulator checks pass.
Real-device/internet-network, CGNAT/IPv6/UDP-blocked and OEM battery behavior are
untested; the user explicitly deferred hardware acceptance. No independent security
audit or universal-connectivity guarantee. Keep both apps open, compare fingerprints
through an independent channel, and expect direct connections to fail on some networks.

Files require known size and are limited to 16 GiB. Interrupted transfers restart from
zero after selection/acceptance. Providers may delay cancellation or refuse partial-file
cleanup; process death may leave an incomplete document. No automatic incoming or
offline delivery after app termination. Local history is app-private SQLite, not an
extra encrypted database. Certificates expire after one year and require reverification.

Choose arm64-v8a for most modern phones, otherwise use the matching ABI or universal APK.
APK checksums are in SHA256SUMS. Source and detailed validation are in the repository.
