# MicroMiniChat Specification — 0.0.2 Preview (development)

## Scope

Exactly two participants per direct session, no accounts or phone numbers. A single
native Android chat screen exchanges plain text and arbitrary file types. There is
no backend, cloud database, tracking, push service, social UI, rich media feature,
voice/video, advertising or AI. WebRTC's shared binary contains unused media code;
the app creates no media tracks and requests no camera/microphone permissions.

The user authorized completion through M12 and release 0.0.1, then explicitly deferred
physical-device acceptance. Automated/emulator gates remain required. The release is
a preview; unperformed network/security/performance checks must remain visible.

## Implemented behavior

- WebRTC DTLS/SCTP, ICE, explicit STUN or host-only discovery, and opt-in authenticated TURN (source update). Non-trickle
  manual offer/answer exchange with bounded versioned signaling, clipboard sensitivity
  hints, Sharesheet export/import and strict validation. No default external service.
- Persistent ECDSA DTLS certificate identity; AES-GCM encryption at rest using a
  nonexportable Keystore wrapping key. Full SHA-256 fingerprint comparison, explicit
  first trust, persistent continuity and unexpected-identity blocking.
- Versioned bounded frames, HELLO negotiation, random identifiers, monotonic ordering,
  durable dedupe and remote persistence ACKs. Pending/Sending/Delivered/Failed states;
  retry uses original IDs. No read receipts or offline-delivery promise.
- SQLite message/history/delivery/receipt/transfer metadata. UI reads 200-message
  pages. Delete history or delete all metadata/identity; no forensic-erasure promise.
- SAF file selection and explicit destination consent, known-size quota 16 GiB,
  16 KiB streaming chunks, application backpressure, progress/cancel, incremental
  SHA-256 and verified completion ACK. File I/O runs separately from text processing.
- Foreground-oriented lifecycle; default-network callbacks invalidate the session,
  and the user reconnects with fresh signaling. Short app switches have at most a
  best-effort two-minute grace. No service/notification/boot/push infrastructure.

## Limits and deferred acceptance

No universal NAT/firewall traversal. UDP-blocked/restrictive networks may fail
legitimately. SDP exposes addresses and traffic metadata remains visible to network
operators; STUN operators see discovery traffic. TURN operators see routing metadata,
not DTLS plaintext. Relay hosting is external and must be configured explicitly. Clipboard/sharing apps are external.
Unknown-size sources are rejected; partial transfers restart from zero. Providers
can fail/block or refuse deletion; process death may leave a partial document requiring
manual cleanup. Persistent keys exist unwrapped in process memory while used.

The twelve real-device network cases, older Android versions, OEM battery policies,
16 KB runtime behavior and physical battery measurements remain untested. No independent
cryptographic/native-code audit or reproducible-binary proof is claimed. Actual
artifact sizes and emulator resource observations are in `docs/`.
