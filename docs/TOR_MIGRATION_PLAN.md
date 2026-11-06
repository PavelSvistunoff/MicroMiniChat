# Tor implementation plan and outcome

Product requirements: [SPEC.md](../SPEC.md).
Work branch: feature/tor-migration. Publication and user identity/data deletion
are outside this authorization.

## Audit and preserved boundaries

The required root documents and repository instructions were read before changing
architecture. Preserve Frame, ReliableProtocol, MessageDatabase, FileOffer,
FileTransfers, SAF consent, durable ACKs, receipts and deduplication. Existing
history partitions and wrapped certificates remain local; new route bindings
require explicit verification. Expired/missing established identities fail closed.

ChatApp owns one serialized coordinator across Activity recreation. Native network
and file workers stay off the main thread. Background grace is finite; no continuous
service or terminated-app delivery is promised. Dependencies are minimal, pinned
and accompanied by notices. Binary hashes do not prove reproducible native builds.

## Milestone dependency order

1. Audit dependencies, persistence, trust, lifecycle and runtime provenance.
2. Qualify the bundled Tor executable and persistent v3 onion publication/restart.
3. Implement bounded single-use invitations and mutual pinned TLS1.3.
4. Add bounded framed multiplexing, deadlines, cancellation and backpressure.
5. Preserve durable text IDs/ACK/deduplication and consented hashed SAF streaming.
6. Persist verified routes, arbitrate simultaneous sessions and reconnect with jitter.
7. Implement foreground lifecycle, native terminal Views and English/Ukrainian resources.
8. Review security, hostile input, storage failures and Android/export boundaries.
9. Run real-Tor software validation and all available hardware checks; record gaps.
10. Measure APK/performance, remove unused components and finish current documentation.

## Confirmed outcome and remaining qualification

Software milestones M01–M17 are implemented and committed. The runtime is an owned
child process rather than an in-process JNI bridge; actual native lifecycle and
onion publication passed. Four ABI/universal builds, reliable text/files, terminal
UI and signed two-emulator pairing/reconnect are recorded in [VALIDATION.md](VALIDATION.md).

Physical acceptance remains deferred. API26/non-arm64 runtime, physical networks,
OEM sleep/battery and independent native/security qualification are NOT VERIFIED.
See [TASKS.md](../TASKS.md), [runtime updates](TOR_RUNTIME.md) and
[physical checklist](PHYSICAL_TEST_PLAN.md). No publication occurred.
