# Tor migration plan

Authoritative requirements: `TOR_MIGRATION_SPEC.md`. Work branch:
`feature/tor-migration`. No publication or identity/data deletion is authorized.

## M01 audit — confirmed

The initial checkout was `main` at the existing phone-pairing preview. All seven
required root documents and AGENTS.md were read. The repository contains one app,
a synthetic probe, JVM/instrumentation tests, release tooling, historical evidence
and signed historical APKs. `.DS_Store` is unrelated untracked user state.

WebRTC coupling occurs in `WebRtcTransport`, `ChatSession`, `IdentityStore`,
`PairingTls`, `Signal`, `IceConfig`, `PhonePairing`, MainActivity, native tests,
probe, dependency verification, shrinker rules, notices and smoke tools. Both
modules declare `io.github.webrtc-sdk:android:150.7871.04`. Remove these only after
the replacement works. Historical APKs/evidence do not demonstrate Tor behavior.

Reuse `Frame`, `ReliableProtocol`, `MessageDatabase`, `FileOffer`, `FileTransfers`,
SAF consent and receipt semantics. Database schema v1 stores peer fingerprints as
partition keys; preserve these and all history. Existing wrapped PEM identities
can potentially supply TLS certificates, but expired certificates must fail
closed. Existing trust must never be implicitly bound to a new onion address.
Require explicit migration verification without deleting history.

ChatApp owns the coordinator across activity recreation; Android callbacks run
through a HandlerThread. File I/O has a separate bounded worker pool. Background
grace is finite; no terminated-app delivery or continuous service is implemented.

## Runtime investigation

Guardian Project publishes `info.guardianproject:tor-android:0.4.9.14` on Maven
Central. Its actual POM and source JAR were downloaded for inspection, rather than
assuming coordinates. Upstream: https://github.com/guardianproject/tor-android .
The POM declares BSD-3-Clause and dependencies on localbroadcastmanager 1.1.0,
jtorctl 0.4.5.7 and Kotlin stdlib 2.2.10. The service wraps bundled JNI libtor,
uses a private Unix control socket, and supports a private torrc. It also logs
exception text and sends status broadcasts; these require explicit review before
adoption. Current master is not evidence of compatibility of the published AAR.
Binary ABIs, metadata, provenance, native loading, restart and actual bootstrap
remain unverified at this audit step.

## Dependency order and implementation decisions to validate

1. M02: inspect published AAR, verify checksums/signatures/source provenance,
   select runtime integration; test asynchronous start/stop/failure without
   leaking diagnostics. Block adoption on unverifiable binary or incompatible API.
2. M03: persistent protected service directory, local listener, readiness from
   descriptor publication (not merely hostname creation); missing keys fail closed.
3. M04–M05: bounded versioned onion invitation, capability expiration/consumption;
   standard mutual TLS with pinned persistent certificates. Evaluate TLS 1.3 API
   availability on API 26 rather than inventing a new handshake. Persist explicit
   trust and route atomically. No plaintext or trust-all fallback.
4. M06: length-delimited multiplexed stream with bounded prioritized queues,
   deadlines, cancellation and serialized callbacks. Retain application frames.
5. M07–M08: reconnect retries with original durable message IDs; retain SAF
   streaming and stop-and-wait chunk ACKs. Tor latency may require longer measured
   deadlines; never weaken commit-before-ACK or hash verification.
6. M09–M10: deterministic endpoint arbitration, generation fencing, jittered
   exponential retry, network callback and lifecycle cleanup. Evaluate eligible
   foreground service type against current Android documentation.
7. M11–M12: native terminal screen, compact entries, retained draft, centralized
   palette/dimensions, English and Ukrainian resources with stable protocol codes.
8. M13–M14: adversarial parser/authentication/lifecycle/storage tests, manifest,
   dependency and bypass review; distinguish deterministic fakes from real Tor.
9. M15–M16: execute only available device scenarios, record actual timing/PSS/APK
   and throughput; all unavailable hardware/network results NOT VERIFIED.
10. M17: replace current runtime instructions, retire obsolete probe/dependencies,
    preserve historical notices/artifacts, publish build instructions and evidence.

Each milestone requires implementation, relevant validation, review, evidence and
its own commit. This file is an audit/plan, not a completion claim for M02–M17.
