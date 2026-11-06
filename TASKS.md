# Tor migration milestones

TOR_MIGRATION_SPEC.md defines the approved Tor product requirements. Work is on
feature/tor-migration. Software milestones are implemented, reviewed, validated and
committed. Physical acceptance is explicitly deferred and never inferred from emulators.

| Milestone | Implementation and evidence |
| --- | --- |
| M01 Audit | Reuse/persistence/trust/lifecycle audit and concrete dependency-ordered plan in docs/TOR_MIGRATION_PLAN.md. |
| M02 Embedded runtime | Checksum-pinned four-ABI Tor executable; private authenticated loopback control, actual progress/failure, owned child restart/clean exit. Native tests PASS API35 arm64. |
| M03 Persistent onion | Protected no-backup v3 keys, missing-key guard, checksum validation, actual descriptor publication and stable restart address. PASS on real Tor. |
| M04 Invitations | Bounded versioned single-use ten-minute capabilities; explicit share/import/connect/cancel. Codec/store/UI tests PASS. QR omitted with documented dependency/privacy tradeoff. |
| M05 Authentication | Mutual Conscrypt TLS1.3/ALPN, certificate/route pins, bilateral verification, Keystore signing and exact legacy wrapped PEM continuity. Pin/downgrade/migration tests PASS. |
| M06 Stream transport | Strict bounded framing, priority multiplexing, backpressure, actual-write callbacks, heartbeat/deadlines/cancel. Hostile input and real two-client tests PASS. |
| M07 Reliable text | Durable SQLite receive-before-ACK, preserved IDs/sequences/receipts, dedupe, pending/sending/sent/delivered/failed, offline queue. Durable real-Tor text/reopen tests PASS. |
| M08 Files | Consented SAF streaming, 16KiB chunks, SHA256/completion ACK, progress/cancel/partial cleanup. Real Tor 2MiB+concurrent text and corrupt-hash tests PASS. |
| M09 Reconnect | Persisted verified route, mutual reauthentication, lower-route connection arbitration, network callbacks, jittered bounded backoff, pending replay. Real-Tor restart regression recorded in VALIDATION.md. |
| M10 Lifecycle | Application-owned coordinator, retained draft/recreation, checked two-minute foreground grace, explicit disconnect, process ownership/cleanup. Lifecycle tests PASS; no ineligible permanent FGS. |
| M11 Terminal UI | Native terminal palette/logs, literal selectable content, insets/IME/SAF, full verification and minimal confirmed settings. Native UI/manifest tests PASS. |
| M12 Localization | Complete English/Ukrainian resources, plurals/format contracts, locale timestamps and platform per-app language. Host and actual Ukrainian/large-font tests PASS. |
| M13 Security | Reviewed pins/keys/capabilities/control/bypass/queues/exports/logs/backup/storage, cancellation epochs and retained archived history. Threat model in SECURITY.md; adversarial storage tests PASS. |
| M14 Automation | Tor-native JVM, host and Android suites replace obsolete tests; mismatch/replay/expiry/framing/disconnect/backpressure/ACK/database/file/lifecycle/migration coverage. See exact runs in VALIDATION.md. |
| M15 Physical | All available hardware inventoried: API35 arm64 emulators; no physical phones. Fourteen-scenario checklist prepared in docs/PHYSICAL_TEST_PLAN.md. All physical results NOT VERIFIED; acceptance deferred. |
| M16 Optimize | Removed unused components and shrinker rules; signed four-ABI/universal builds, additive transfer timestamps, bounded merged log, measured performance, real IME/touch fix and two-foreground-emulator signed UI acceptance PASS. See final evidence. |
| M17 Documentation | Tor-only README/spec/architecture/protocol/security/build/pairing/lifecycle/update guidance and removed retired guides; final evidence consolidated and documentation reviewed; software migration complete, physical acceptance deferred. |

No backend deployed, APK published, release created or existing user data reset.
Historical GPL/third-party notices and immutable release/0.0.1 APKs are retained.
Remaining runtime qualifications: physical phones/networks/battery, API26 and non-arm64
ABIs, prolonged OEM/Doze sessions and independent security/reproducible-native audit.

Documentation follow-up: all repository Markdown now describes the current Tor
architecture without retired transport/setup references. Removed superseded guides,
consolidated validation evidence and checked relative links; runtime code unchanged.
