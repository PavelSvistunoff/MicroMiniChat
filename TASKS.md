# Implementation Milestones

The user authorized completion through M12, a main-branch commit, and release 0.0.1.
On 2026-11-01 they explicitly deferred physical-device acceptance so implementation
could continue. Available automated/emulator gates still apply. Statuses below do
not claim physical-network or independent security-audit acceptance.

For each milestone: inspect code/dependencies, implement, add tests, run checks, fix
defects, review security/complexity, update documentation and report limitations.

## M01 — Android Project Foundation

**Status:** Accepted — build/host checks and API 35 emulator launch/recreation pass

**Scope:** Create native Views/Java Android shell, pin build tooling and establish checks.

**Dependencies:** None

**Implementation tasks:** Add manifest with no unnecessary permissions, disable backups, ignore local configuration; add launch/recreation integration test and security contract tests.

**Acceptance criteria:** Debug and unsigned release APK build; host tests and lint pass; generated instrumentation APK builds. Device launch/recreation is recorded separately and is not an M01 gate.

**Unit tests:** Host contract tests verify permission/backup/export boundaries; Gradle JVM tests where logic exists (none yet).

**Integration tests:** Build instrumentation APK; run launch/recreation on API 26 and current device when available.

**Known limitations:** No transport or business logic; no hardware result may be inferred from a successful build.

## M02 — WebRTC Feasibility Prototype

**Status:** Accepted under explicit hardware deferral — original pinned Maven AAR passes emulator tests; alternative publisher bundle rejected after native incompatibility

**Scope:** Validate one pinned Android WebRTC artifact before full application implementation.

**Dependencies:** M01 accepted

**Implementation tasks:** Review provenance/licenses/upstream revision and ABIs; implement isolated synthetic-data probe, ordered reliable DataChannel and non-trickle raw SDP harness; configurable explicit STUN; bounded timeout/disposal; measure baseline versus probe APK/native bytes and dependency tree.

**Acceptance criteria:** Native library loads; binary roundtrip and cleanup pass on Android; two-device same-Wi-Fi and supported internet path demonstrated with manual SDP; actual APK delta recorded; document failures. No real chat implementation.

**Unit tests:** Test probe input limits, state/error paths, timeout and bounded queue handling.

**Integration tests:** In-process two-peer synthetic roundtrip, disconnect/repeat; two real devices via manual SDP with consented STUN; record hardware/ABI and selected candidate type without IP addresses.

**Known limitations:** No peer authentication; synthetic data only. Emulator/in-process tests do not establish internet feasibility; hardware missing keeps gate open.

## M03 — ICE/STUN Connectivity

**Status:** Accepted with hardware deferred — 4 policy tests, 4 emulator tests and lint pass

**Scope:** Product ICE adapter with direct-only connectivity.

**Dependencies:** M02 accepted

**Implementation tasks:** Configurable STUN allowlist, full gather, deadlines, checks and coarse failure states; no TURN fallback.

**Acceptance criteria:** Supported-network connection succeeds; restrictive networks fail clearly; no silent third-party contacts.

**Unit tests:** Test STUN validation, gather deadlines, failure/state transitions.

**Integration tests:** Run Wi-Fi/mobile/IPv6/blocked UDP matrix with real devices.

**Known limitations:** STUN cannot solve all NAT/firewall conditions.

## M04 — Manual Signaling

**Status:** Accepted with hardware deferred — versioned codec/fuzz tests and native signaling roundtrip pass; explicit clipboard/Sharesheet pairing implemented

**Scope:** Independent versioned signaling codec and explicit exchange UI.

**Dependencies:** M03 accepted

**Implementation tasks:** Implement bounded offer/answer envelope, strict parsing, clipboard sensitive flag, Sharesheet and matching-session validation; defer QR.

**Acceptance criteria:** Two exchanges establish connection; malformed, oversize, stale and unsupported payloads reject safely.

**Unit tests:** Boundary sizes, fuzz inputs, field/type/version errors and role/session mismatch.

**Integration tests:** Cross-device copy/paste and Sharesheet offer/answer; no signaling server.

**Known limitations:** SDP exposes addresses; external sharing channel is outside app control.

## M05 — Peer Identity and Authentication

**Status:** Accepted with hardware deferred — persistent encrypted identity/trust and adversarial DTLS mismatch tests pass (7 emulator tests); standard certificate pinning design

**Scope:** Persistent verified identity bound to current DTLS endpoint.

**Dependencies:** M04 accepted

**Implementation tasks:** Select reviewed established authentication construction and test vectors before coding; Keystore signing identity, canonical public fingerprint, independent comparison, durable trust and identity-change block; forbid custom handshake.

**Acceptance criteria:** Unverified peer cannot exchange product data; verified reconnect retains trust; changed key and replay/MITM attempts reject; design reviewed.

**Unit tests:** Known-answer vectors, replay/session binding, altered fingerprint/signature, trust transitions and key failure.

**Integration tests:** Two devices verify fingerprints, reconnect, simulate identity replacement and reinstall; test Keystore behavior.

**Known limitations:** No suitable reviewed construction means blocked; compromised endpoints and traffic metadata remain exposed.

## M06 — Minimal Chat UI

**Status:** Accepted — primary chat controls, pairing/settings and unverified-send gating pass launch/recreation checks and lint

**Scope:** One primary native chat screen plus minimal pairing/settings.

**Dependencies:** M05 accepted

**Implementation tasks:** Status, list, text input, send/attach; verified-state gating, accessibility and error display; no decorations/social UI.

**Acceptance criteria:** Required controls work without UI-thread blocking; no send to unverified peers.

**Unit tests:** View/coordinator state mapping and input limits.

**Integration tests:** Rotation/recreation, keyboard, accessibility, font scaling and pairing flow.

**Known limitations:** No delivery or file success claims before later milestones.

## M07 — Reliable Messaging Protocol

**Status:** Accepted with hardware deferred — versioned framing, ordered acknowledgements and injected commit/lost-ACK failure tests pass

**Scope:** Versioned bounded framing and truthful delivery states.

**Dependencies:** M06 accepted

**Implementation tasks:** Version negotiation, SecureRandom IDs, ordering, dedupe, ACK handling and pending/sending/delivered/failed; wire persistence interface requiring durable commit before ACK.

**Acceptance criteria:** Only explicit remote persistence ACK marks Delivered; duplicates do not display twice; bounded parsing and retry paths work.

**Unit tests:** Parser fuzz/boundaries, versions, ID reuse, out-of-order frames, invalid ACK and disconnect races.

**Integration tests:** Two-device fault injection: dropped ACK, repeated message, reconnect and slow receiver; use durable test repository until M08.

**Known limitations:** No offline delivery; production durable storage completed at M08.

## M08 — Local Message Storage

**Status:** Accepted with hardware deferred — 10 emulator tests pass, including verified chat delivery, database reopen/dedupe, history deletion and trusted reconnect

**Scope:** SQLite persistence and deletion.

**Dependencies:** M07 accepted

**Implementation tasks:** Transactional history/dedupe/sequence, delivery and transfer metadata, preferences and trust; migrations; explicit history deletion and trust reset.

**Acceptance criteria:** Restart preserves state; ACK follows committed receipt; deletion obeys retention/replay policy; no key material in database.

**Unit tests:** Transactions, uniqueness, migrations, rollback, deletion and trust retention.

**Integration tests:** Kill receiver around commit/ACK; restart both apps and retry; backup exclusion and history deletion.

**Known limitations:** App-private storage is not protection from compromised OS; flash secure erasure not promised.

## M09 — Chunked File Transfer

**Status:** Accepted with hardware/provider coverage deferred — 13 emulator tests pass, including 2 MiB streaming plus text, cancellation cleanup and corrupted-hash rejection

**Scope:** SAF-selected arbitrary files with bounded streaming.

**Dependencies:** M08 accepted

**Implementation tasks:** Separate file channel, prioritization/backpressure, incremental SHA-256, offsets, progress/cancel, destination consent and partial-output cleanup; define quota/unknown-size policy.

**Acceptance criteria:** Large transfer uses bounded memory; text remains responsive; size/hash verified before success; cancellation/failure never marks partial file complete.

**Unit tests:** Chunk/offset overflow, size/hash mismatch, queue thresholds, cancellation races, malformed names and quotas.

**Integration tests:** Different SAF providers, nonseekable files, disk full, revoke grants, disconnect mid-file; verify final SHA-256.

**Known limitations:** Resume may restart from zero; provider can refuse deletion of partial output.

## M10 — Connection Lifecycle and Recovery

**Status:** Accepted with hardware deferred — 14 emulator tests pass; network-change callback simulation rejects old answers and reconnects with trust retained

**Scope:** Explicit reconnect and lifecycle-safe cleanup.

**Dependencies:** M09 accepted

**Implementation tasks:** Network callbacks, serialized session states, graceful close, pending-send failure, ICE failure and user-initiated new signaling; restore persisted state after death.

**Acceptance criteria:** Network switch/loss does not silently claim online; retry preserves IDs; no resource leaks after repeated sessions.

**Unit tests:** Transition/race tests, late callbacks, shutdown idempotence and retry ID invariants.

**Integration tests:** Switch Wi-Fi/mobile, airplane mode, sleep and repeated reconnect on hardware.

**Known limitations:** New manual signaling may be required; no automatic incoming connection after termination.

## M11 — Android Background Behavior

**Status:** Accepted with physical/OEM testing deferred — 15 emulator tests pass; interactive-only operation and bounded background grace, no foreground service/push or terminated-app incoming promise

**Scope:** Investigate legal platform execution model and document observed behavior.

**Dependencies:** M10 accepted

**Implementation tasks:** Review current FGS types against actual operations; finite transfer dataSync only if eligible, notification permission/start restrictions/timeouts; otherwise foreground-only; no push/boot keepalive.

**Acceptance criteria:** Declared type justified with official documentation; background/sleep/process behavior tested and accurately described; no indefinite-run promise.

**Unit tests:** Permission denial, timeout/stop, foreground gating and restoration.

**Integration tests:** API 26/33/34/35+ hardware, Doze/battery restrictions, backgrounding, force-stop and kill.

**Known limitations:** OS/OEM may stop process; remoteMessaging continuity category may not fit; background incoming delivery not guaranteed.

## M12 — Security, Performance, QA and Release

**Status:** Accepted for 0.0.1 preview with physical qualification deferred — signed APKs verified, 16 app JVM/4 probe JVM/6 host/16 emulator tests pass, and two-process signed-release pairing/chat/force-stop smoke passes. Public GitHub publication requires authentication.

**Scope:** Review, measure and release only demonstrated capabilities.

**Dependencies:** M11 accepted

**Implementation tasks:** Review threat model/crypto/native supply chain; fuzz parsers, licenses, dependency verification; measure APK/ABI delta, startup/PSS/CPU/battery/traffic; complete network matrix and release signing instructions.

**Acceptance criteria:** Automated checks pass; signed release can be built with external keys; secure chat/files proven on supported real networks; all limitations/evidence recorded.

**Unit tests:** Full regression, adversarial inputs, migration and security vectors.

**Integration tests:** Full twelve-scenario matrix, release APK device install, file integrity and lifecycle; label untested rows.

**Known limitations:** No universal NAT traversal or security guarantee against compromised endpoints; missing hardware prevents full acceptance.

## Evidence

See [validation log](docs/VALIDATION.md), [network matrix](docs/NETWORK_TEST_MATRIX.md)
and [measurement procedure](docs/MEASUREMENTS.md). Neither unexecuted tests nor
planned measurements count as passing results.


## M13 — Opt-in TURN Internet Connectivity

**Status:** Host/build/lint and 18 emulator tests pass; real relay acceptance awaits deployment.

**Scope:** User-approved authenticated TURN for networks where direct ICE fails.

**Dependencies:** Existing ICE/DTLS/manual signaling; externally operated TURN endpoint.

**Implementation tasks:** Add bounded TURN URL/credential validation; native ICE server
configuration; UDP/TCP/TLS URL support; relay-only policy; explicit relay consent;
in-memory credentials and masked UI; self-hosting instructions. Preserve 0.0.1 artifacts.

**Acceptance criteria:** Host tests/build/lint pass; native direct regression passes;
two phones on separate internet networks exchange verified text/files through TURN.
The last criterion remains open until a real relay and devices are available.

**Unit tests:** TURN scheme/port/transport/credential boundaries, relay-only requirements,
relay candidate codec roundtrip and unknown-type rejection.

**Integration tests:** Existing emulator native pairing/identity/message/file regression;
real relay-only UDP/TCP/TLS, invalid credentials/certificates and network-loss tests pending.

**Known limitations:** One endpoint per pairing, no automatic credential refresh or
server provisioning, credentials re-entered after dialog dismissal; original APKs lack
TURN support. No universal connectivity or remote internet test result is claimed.


## M14 — Guided Pairing

**Status:** Implemented; 21 emulator tests, host/build/lint and signed offer UI smoke pass.

**Scope:** Simplify the existing two-message manual exchange without a signaling backend.

**Dependencies:** M04 manual signaling, M05 verification and M13 optional TURN.

**Implementation tasks:** Start/Join choices, contextual step guidance, Share/Copy
invitation/reply buttons, Paste & connect, collapsed connection settings, fingerprint
controls only after connection. Validate Sharesheet input and require explicit Connect.

**Acceptance criteria:** Both roles have clear actions; raw outgoing SDP is hidden;
no automatic network access/trust from incoming shares; no default external service.

**Unit tests:** Existing bounded signaling/configuration tests remain required.

**Integration tests:** Guided role selection, collapsed settings, explicit Sharesheet
acceptance and native offer-to-share UI on the API 35 emulator; existing transport,
identity, persistence and file-transfer regressions.

**Known limitations:** Still requires invitation and reply through another messenger;
TURN configuration/credentials remain necessary on restrictive networks. No hosted
signaling, short invitation codes or single-SDP QR assumption. Real-phone UX and
internet relay acceptance remain pending.


## M15 — Phone-hosted Pairing

**Status:** Implemented; 23 app JVM / 32 emulator tests, host/lint, signed build and two-UID signed automatic-pairing smoke pass. Real internet acceptance pending.

**Scope:** Latest explicit user choice: creator hosts temporary pairing using its reachable IP.

**Dependencies:** M04 signaling, M05 identity verification, M10 lifecycle, optional M13 TURN.

**Implementation tasks:** Detected/overridden numeric IP and port; temporary pinned TLS
listener/client; one shared invitation and automatic answer return; bounded framed
parsing, capability authentication, single use, deadlines and cancellation; explicit
Sharesheet/deep-link acceptance; preserve manual fallback and persistent verification.

**Acceptance criteria:** TLS exchange and complete native sessions succeed in emulator;
wrong pins/tokens/session/oversized input fail safely; listeners close on cancellation;
build/unit/lint checks pass. Two physical phones over public IPv4/IPv6 remain an open gate.

**Unit tests:** IPv4/IPv6 invitation roundtrip, malformed/canonical/size bounds and token immutability.

**Integration tests:** TLS pin/token rejection, incomplete-authentication deadline, oversized
frame, wrong-session answer, listener closure, network changes, automatic native pairing
and durable text ACK, deep-link review without automatic connection; existing regressions.

**Known limitations:** No automatic port forwarding, public-IP discovery service, public
signaling backend or CGNAT bypass. TURN does not expose the pairing listener. Both apps
must remain open; custom links may need copy/paste. Internet hardware testing is pending.

## Tor migration (authoritative replacement roadmap)

The user approved TOR_MIGRATION_SPEC.md and the full terminal UI redesign.
Older milestones above describe historical WebRTC previews, not Tor acceptance.

- Tor M01: audit and concrete plan complete; see docs/TOR_MIGRATION_PLAN.md.
- Tor M02: PASS — bundled owned child process, authenticated private control,
  actual native start/shutdown/restart validated on API 35 arm64.
- Tor M03: PASS — actual onion descriptor publication twice with stable identity;
  no-backup keys, strict address checksum and missing-key guard implemented.
- Tor M04: invitation codec/capability/trust primitives PASS (27 JVM and 2 emulator
  checks); end-to-end pairing UI/transport integration follows M05/M06/M11.
- Tor M05: PASS — mutual TLS 1.3, persistent Keystore keys and legacy PEM
  continuity; two API 35 arm64 authentication/migration tests pass.
- Tor M06: PASS — bounded multiplexed stream and real two-client onion/TLS
  integration including bilateral verification gating; JVM/lint pass.
- Tor M07: PASS — durable text ACK, Sent/write distinction, preserved retry IDs
  and database/history reopen through real Tor-native sessions.
- Tor M08: PASS — real Tor 2 MiB SAF transfer plus text, completion ACK/hash,
  cancellation cleanup and independent corrupt-hash rejection.
- Tor M09: PASS — verified automatic reconnect and offline queue draining over
  actual Tor; deterministic arbitration/backoff and strict SOCKS5 host tests pass.
- Tor M10: PASS — foreground grace, expiry, verified return and explicit
  disconnect behavior through real Tor; lifecycle tests/JVM/lint pass.
- Tor M11–M17: pending. No Tor-native messenger acceptance is claimed.

See docs/TOR_BLOCKER.md for the resolved runtime issue and historical evidence. Existing historical milestones and APKs do not satisfy this migration.
