# MicroMiniChat product specification

This document defines the approved Tor-native product requirements.
Implementation details are documented in ARCHITECTURE.md, PROTOCOL.md and SECURITY.md.

One verified two-person conversation, no accounts/contact lists/groups/server storage.
Embedded Tor client plus persistent v3 onion service on each installation; mutual
TLS 1.3, explicit bilateral fingerprint verification, stable peer route and key pins.
No direct Internet application path, application backend, push or fallback transport.

Ten-minute single-use invitations via custom URI, Android Sharesheet and bounded
paste/import. Import never initiates a connection. Changed identities/routes block
communication; confirmed resets preserve local history. Legacy identity/trust migration
requires explicit route binding and never silently replaces trust.

SQLite history, unique IDs/sequences, durable receipts before ACK, deduplication,
actual-flush Sent and matching-ACK Delivered. Local offline queue, bounded strict
UTF-8 messages (16 KiB), safe retry IDs. SAF-only explicit file acceptance, known
size up to 16 GiB, 16 KiB streaming chunks, SHA-256, completion ACK, cancel/cleanup.
No file resume or automatic execution.

Native Java 17 Views, terminal palette and log rows, accessible touch targets,
system font scaling/insets, retained draft, English/Ukrainian Android resources.
Minimal settings for language, verification, diagnostics, disconnect and confirmed
history/trust/identity operations. No QR dependency was justified for this preview.

API 26+ compile target; API 35 arm64 runtime evidence. Foreground connectivity with
a two-minute best-effort sharing/SAF grace; no continuous background or terminated
app delivery. Required four ABI builds; runtime/physical qualification gaps and
measurements are recorded in docs/VALIDATION.md and docs/PHYSICAL_TEST_PLAN.md.
