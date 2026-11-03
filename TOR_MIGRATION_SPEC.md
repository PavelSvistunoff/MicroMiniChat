# MicroMiniChat — Complete Product Specification and Implementation Plan

**Repository:** https://github.com/PavelSvistunoff/MicroMiniChat

**Project:** MicroMiniChat  
**Target platform:** Android  
**Implementation language:** Java 17  
**Architecture:** Embedded Tor v3 Onion Services  
**UI style:** Minimalist Linux/macOS terminal  
**Status:** Approved specification for complete implementation

---

# 1. Project Vision

MicroMiniChat is an extremely lightweight, private Android messenger designed exclusively for communication between two people.

The application must work over the Internet across different Wi-Fi networks, mobile networks, NAT, and CGNAT without requiring users to configure networking, install additional applications, register accounts, or operate servers.

The application must use the Tor network as its only Internet communication transport.

The user experience must remain exceptionally simple:

1. Install the application.
2. Create a chat invitation.
3. Share the invitation with another person.
4. The other person accepts it.
5. Both users verify their identities.
6. Exchange encrypted text messages and files.

Everything related to Tor, networking, routing, and encryption must be handled automatically.

The interface must resemble a modern Linux/macOS terminal while remaining comfortable to use on Android.

# 2. Non-Negotiable Product Requirements

## 2.1 Core functionality

- Exactly one paired conversation between two people.
- Text messaging.
- File sending and receiving.
- Encrypted and authenticated communication.
- Persistent local message history.
- Message delivery acknowledgments.
- Automatic reconnection to a previously verified peer.
- Connection status indicators.
- Invitation sharing through Android Sharesheet.
- QR-code pairing when practical.
- Minimal settings.

## 2.2 Strict exclusions

Do not implement:

- User accounts or registration.
- Usernames or public profiles.
- Contact lists.
- Group chats.
- Public chat rooms.
- Emoji picker or decorative emoji.
- Stickers or reactions.
- Advertising.
- Analytics or telemetry.
- Cloud message storage.
- Push notification services.
- Google Firebase.
- WebRTC.
- ICE, STUN, TURN or SDP.
- ngrok.
- Supabase.
- Cloudflare signaling or relay services.
- A self-hosted backend.
- Manual port forwarding.
- Manual IP address configuration.
- Dependence on Orbot or another installed application.

Do not introduce any excluded functionality as an implementation shortcut.

## 2.3 General principles

- Privacy by default.
- Local-first data storage.
- No application-specific external infrastructure.
- Minimal dependencies.
- Minimal APK size where feasible.
- Minimal memory consumption.
- Battery-conscious operation.
- No plaintext application traffic outside Tor.
- No silent changes to peer identity.
- No unnecessary screens or features.

# 3. Mandatory Architecture Migration

The existing WebRTC-based implementation is obsolete.

Replace the network architecture completely rather than maintaining WebRTC as a fallback.

Inspect the current implementation before modifying it.

Preserve useful, transport-independent components wherever possible, including:

- MessageDatabase.
- ReliableProtocol.
- FileTransfers.
- Delivery acknowledgment logic.
- Deduplication.
- File hashing.
- Local history.
- Android Storage Access Framework integration.

Rewrite components tightly coupled to WebRTC or DTLS identity.

Remove obsolete signaling, SDP, ICE, STUN, TURN, and phone-hosted pairing code after their replacements are functional.

Remove the WebRTC runtime dependency and obsolete probe module when no longer needed.

# 4. Target Network Architecture

Each Android installation must operate as both:

- A Tor client.
- A persistent v3 Onion Service.

Each installation owns a persistent onion service identity.

Both devices establish outgoing connections to the Tor network. Neither requires a publicly reachable IP address or router configuration.

The application protocol runs over an authenticated encrypted TCP connection carried through Tor.

Target structure:

Android UI
→ ChatSession
→ Reliable Messaging and File Protocol
→ Secure Authenticated Transport
→ Framed TCP Connection
→ Embedded Tor
→ Tor Onion Network
→ Remote Device

No separate signaling service is required.

Both peers may initiate a connection. Implement deterministic connection arbitration to prevent duplicate simultaneous sessions.

# 5. Milestone M01 — Repository Audit and Migration Plan

Before implementation:

1. Inspect the entire repository.
2. Read README.md, SPEC.md, ARCHITECTURE.md, PROTOCOL.md, SECURITY.md, TASKS.md and AGENTS.md.
3. Identify all WebRTC dependencies and assumptions.
4. Identify reusable protocol and persistence components.
5. Identify identity and trust migration risks.
6. Identify Android lifecycle constraints.
7. Evaluate maintained Tor runtime integration options.
8. Produce a concrete implementation plan.

Create:

`docs/TOR_MIGRATION_PLAN.md`

The plan must distinguish confirmed facts from assumptions.

Continue into implementation after documenting the plan.

# 6. Milestone M02 — Embedded Tor Runtime

Integrate a maintained, verifiable Tor runtime directly into the Android application.

Requirements:

- Tor is bundled with the application.
- No Orbot installation.
- No runtime download of executable binaries.
- No user-visible Tor configuration.
- Support Android API 26+ where feasible.
- Support the required Android ABIs.
- Start Tor asynchronously.
- Detect bootstrap progress.
- Detect bootstrap failures.
- Support shutdown and restart.
- Recover from network interruptions.
- Store Tor state in app-private storage.
- Restrict SOCKS and control interfaces to local access.
- Protect the Tor control interface.
- Prevent sensitive data from entering logs.
- Ensure application traffic cannot silently bypass Tor.

Select the runtime based on:

- Current maintenance status.
- Android compatibility.
- Security update process.
- Binary provenance.
- Licensing.
- APK size.
- Memory footprint.

Do not invent library versions or dependency coordinates.

If no suitable maintained distribution is available, document the blocker and implement a reproducible, auditable integration/build strategy rather than silently using an unverified binary.

# 7. Milestone M03 — Persistent Onion Service

Every installation must create a persistent v3 Onion Service.

Requirements:

- Generate onion identity securely on first launch.
- Persist the identity across restarts.
- Never regenerate identity silently.
- Store private material only in app-private protected storage.
- Use Android Keystore where appropriate for application-level secrets.
- Avoid claiming that Tor-managed onion keys are hardware-backed unless actually verified.
- Publish a local loopback TCP listener through Tor.
- Detect onion service readiness.
- Validate the resulting onion address.
- Restore service state after application restart.
- Support explicit identity reset with user confirmation.

The onion address is a stable routing identifier.

Do not expose the full address unnecessarily in the normal chat UI.

# 8. Milestone M04 — Pairing and Invitations

Replace the existing phone-hosted pairing protocol.

Implement a versioned Tor invitation protocol.

An invitation must include:

- Protocol version.
- Valid v3 onion address.
- Public peer identity verification material.
- Cryptographically random pairing capability.
- Expiration information.

The invitation must never contain private keys.

Implement:

- Create Chat.
- Share Invitation.
- Join Chat.
- Import Invitation.
- QR-code display and scanning if a secure, lightweight implementation is practical.
- Pairing cancellation.
- Invitation expiration.
- Single-use invitation consumption.
- Explicit peer verification.

Reject:

- Malformed invitations.
- Invalid onion addresses.
- Unsupported versions.
- Expired invitations.
- Replayed invitations.
- Unauthorized peers.
- Unexpected identity changes.
- Oversized input.

A pairing invitation is a sensitive capability. Avoid placing secrets in analytics, logs, or external URL requests.

Use Android App Links or a suitable custom URI scheme without introducing a hosted web backend.

# 9. Milestone M05 — Authentication and Encryption

Replace the current DTLS-dependent identity mechanism.

Implement mutual authentication and application-layer encryption using a well-established protocol and maintained cryptographic implementation.

Acceptable design candidates include:

- TLS 1.3 with appropriately verified persistent peer identities.
- A standard Noise protocol pattern implemented by a vetted library.

Select one approach, justify it, and document the security model.

Requirements:

- Authenticate both participants.
- Verify possession of private identity keys.
- Bind the authenticated identity to the accepted pairing invitation.
- Protect confidentiality and integrity.
- Use fresh session keys.
- Reject replayed authentication attempts.
- Detect peer identity changes.
- Prevent silent trust replacement.
- Protect against downgrade attacks.
- Limit unauthenticated resource consumption.
- Use secure random generation.
- Avoid custom cryptographic primitives.

Preserve existing verified peer identity data when safe and technically possible.

If old DTLS identity material cannot be migrated securely, require explicit re-verification. Do not silently replace trust records or delete chat history.

# 10. Milestone M06 — TCP Transport and Framing

Replace WebRTC DataChannels with an asynchronous framed TCP transport over Tor.

Requirements:

- Strict message framing.
- Versioned protocol.
- Maximum frame size limits.
- Bounded input and output queues.
- Backpressure.
- Read/write timeouts.
- Graceful shutdown.
- Robust cancellation.
- Detection of broken connections.
- No unbounded buffering.
- No blocking network operations on the Android main thread.
- Safe handling of malformed and hostile input.

The previous implementation used separate control and file DataChannels.

Replace this with either:

- Explicit multiplexing over one TCP connection, or
- Separate authenticated Tor TCP streams with documented lifecycle rules.

Ensure large file transfers do not starve text messages.

Prefer the simpler design that meets responsiveness and security requirements.

# 11. Milestone M07 — Reliable Text Messaging

Preserve or improve the existing reliable messaging semantics.

Requirements:

- Persistent local message storage.
- Unique message identifiers.
- Monotonic per-peer sequencing where appropriate.
- Delivery acknowledgment only after durable receiver storage.
- Deduplication.
- Retry of failed messages.
- Correct handling of reconnects.
- No false delivered status.
- No duplicate display after retransmission.
- Bounded message size.
- Strict UTF-8 validation.
- Clear pending, sent, delivered and failed states.

Maintain existing history across application upgrades.

Do not introduce server-side offline storage.

If the recipient is offline, messages may remain queued locally until both peers reconnect.

# 12. Milestone M08 — File Transfers

Support file sending and receiving through Tor.

Requirements:

- Android Storage Access Framework.
- User-selected source files.
- Explicit recipient acceptance.
- Streaming transfers.
- Bounded memory consumption.
- Progress indication.
- File size display.
- Transfer cancellation.
- SHA-256 integrity verification.
- Safe filename handling.
- Proper cleanup of incomplete transfers.
- Reliable completion acknowledgment.
- No automatic execution of received files.
- No storage of files on third-party servers.

Preserve the existing transfer protocol where practical.

Large files must not freeze the UI or block text communication.

Interrupted transfers may restart from zero unless a secure, tested resume mechanism is implemented.

# 13. Milestone M09 — Automatic Reconnection

Previously paired devices must reconnect without exchanging a new invitation.

Requirements:

- Persist verified peer onion address and identity.
- Detect connectivity changes.
- Restart Tor when appropriate.
- Retry with exponential backoff and jitter.
- Avoid aggressive battery-draining reconnect loops.
- Prevent duplicate simultaneous sessions.
- Authenticate the peer again after reconnection.
- Resume pending message delivery safely.
- Report connection failures accurately.

Required connection states:

- Offline.
- Starting Tor.
- Connecting to Tor.
- Onion service ready.
- Connecting to peer.
- Authenticating.
- Connected.
- Reconnecting.
- Failed.

Do not display a successful connection state before authentication completes.

# 14. Milestone M10 — Android Lifecycle

Handle Android lifecycle behavior explicitly.

Requirements:

- Keep UI responsive during Tor bootstrap.
- Avoid leaking threads, sockets, services, or file handles.
- Preserve application state across activity recreation.
- Handle process termination safely.
- Restore Tor state and peer configuration on next launch.
- Support clean application shutdown.
- Minimize battery consumption.

Evaluate a foreground service for user-authorized continuous connectivity.

Use a foreground service only if the selected service type and runtime behavior comply with current Android restrictions.

If background operation requires a persistent notification, provide a minimal notification with clear controls.

Do not promise continuous connectivity or delivery while the app is terminated unless that behavior is implemented and verified.

# 15. Milestone M11 — Complete Terminal UI Redesign

Redesign the entire Android interface.

Visual inspiration:

- Linux terminal.
- macOS Terminal.
- Modern dark command-line tools.

The result must be a functional Android messenger, not a fake command-line emulator.

## 15.1 Design system

Default palette:

- Background: #0D1117.
- Surface: #161B22.
- Primary text: #E6EDF3.
- Secondary text: #8B949E.
- Terminal green: #7CFC8A.
- Muted amber: #E3B96B.
- Error red: #F85149.

Typography:

- Monospace for messages, timestamps and technical status.
- Readable sizes.
- Accessible contrast.
- Respect system font scaling.

Visual rules:

- No conventional chat bubbles.
- No emoji decorations.
- No glassmorphism.
- No unnecessary gradients.
- No excessive animations.
- No oversized cards.
- No desktop-style sidebar on mobile.
- Compact spacing.
- Subtle separators.
- Comfortable touch targets.

## 15.2 Main chat screen

Use a single-screen layout.

Header:

- MicroMiniChat title.
- Compact Tor status.
- Verified peer indicator.
- Essential overflow menu.

Message history:

Display chronological messages in terminal-log style.

Example:

[14:27] you: Привіт!

[14:28] peer: Привіт, з'єднання працює.

[14:29] you: Надсилаю файл.

[14:30] file: document.pdf — 2.4 MB

[14:30] status: delivered

The labels and statuses must be localized.

Use distinct restrained accent colors for local and remote messages.

Do not render user message content as executable terminal commands.

## 15.3 Message composer

At the bottom:

- Terminal prompt symbol `>`.
- Multiline message input.
- File attachment action.
- Send action.

Requirements:

- Proper keyboard handling.
- Android window insets support.
- Text selection and copying.
- Long-message wrapping.
- No input loss during activity recreation.
- No unnecessary send animations.

## 15.4 Pairing interface

Use terminal-inspired status messages.

Examples:

[tor] starting...

[tor] bootstrapping 45%

[tor] onion service ready

[pair] waiting for peer...

[secure] verifying identity...

[secure] peer verified

These lines must correspond to real application states.

Do not simulate successful connections.

Pairing actions:

- Create Chat.
- Share Invitation.
- Join Chat.
- Cancel.

Hide low-level Tor details from ordinary users.

## 15.5 File transfer UI

Render file transfers as compact terminal-style entries.

Show:

- Filename.
- File size.
- Transfer progress.
- Transfer state.
- Cancel action.
- Completion/failure indicator.

## 15.6 Settings

Keep settings minimal.

Include only genuinely useful options, such as:

- Language.
- Theme, if implemented.
- Peer identity verification.
- Connection diagnostics.
- Explicit disconnect.
- Identity reset with confirmation.
- Local history management.

Do not expose SOCKS, ports, Tor configuration, STUN, TURN or SDP.

## 15.7 UI implementation

- Native Android Views.
- Java 17.
- No Jetpack Compose.
- Avoid large UI frameworks.
- Reusable theme resources.
- Centralized dimensions and colors.
- Efficient rendering of long histories.
- Support small Android screens.
- Support portrait orientation.
- Correct accessibility semantics.

# 16. Milestone M12 — Localization

Implement proper Android resource-based localization.

Required languages:

- Ukrainian.
- English.

Requirements:

- All user-visible strings in resources.
- No hardcoded UI strings.
- Correct plurals.
- Localized errors and statuses.
- Locale-aware timestamps.
- Correct handling of long translated strings.
- Stable technical protocol identifiers independent of UI language.

English may serve as the fallback language.

Architecture must allow additional languages later.

# 17. Milestone M13 — Security Hardening

Perform a focused security review.

Review:

- Onion private key storage.
- Identity persistence.
- Invitation capability security.
- Invitation replay prevention.
- Peer authentication.
- Connection hijacking.
- Framing attacks.
- Oversized payloads.
- Memory exhaustion.
- File path and filename handling.
- Malicious file metadata.
- Logging of secrets.
- Android exported components.
- Deep-link handling.
- Local database protection.
- Backup behavior.
- Tor control interface access.
- Dependency provenance.
- Transport bypass risks.

Document the threat model in SECURITY.md.

Do not claim anonymity guarantees beyond what the actual design provides.

# 18. Milestone M14 — Automated Testing

Create comprehensive tests for:

- Tor runtime lifecycle.
- Onion identity persistence.
- Invitation encoding and parsing.
- Invalid invitation rejection.
- Expiration.
- Replay protection.
- Peer authentication.
- Identity mismatch.
- Transport framing.
- Oversized frames.
- TCP disconnects.
- Backpressure.
- Simultaneous connection attempts.
- Reconnection races.
- Message ACK handling.
- Deduplication.
- Database failures.
- File transfer integrity.
- File cancellation.
- Storage migration.
- Android lifecycle.
- UI state transitions.

Use deterministic fakes where real Tor network access is unsuitable for unit tests.

Do not replace real-network validation with mocks.

# 19. Milestone M15 — Physical Device Validation

Prepare and execute all tests supported by available hardware.

Required test scenarios:

1. Two phones on the same Wi-Fi.
2. Two phones on different Wi-Fi networks.
3. Wi-Fi to mobile data.
4. Mobile data to mobile data.
5. CGNAT environments.
6. Tor bootstrap failure.
7. Temporary Tor disconnection.
8. Android app restart.
9. Device network change.
10. Long-running chat session.
11. Large file transfer.
12. Interrupted file transfer.
13. Reconnection after peer restart.
14. Peer identity mismatch.

Record:

- Device model.
- Android version.
- Network scenario.
- Tor bootstrap duration.
- Pairing duration.
- Message delivery results.
- Transfer throughput.
- Memory usage.
- Battery observations.
- Failures and recovery behavior.

Mark tests not actually executed as NOT VERIFIED.

Never invent test results.

# 20. Milestone M16 — Performance and APK Optimization

Measure:

- APK size per ABI.
- Universal APK size.
- Cold-start duration.
- Tor bootstrap duration.
- Time to first authenticated connection.
- Idle memory usage.
- Active memory usage.
- Idle battery consumption.
- File-transfer throughput.

Optimize without weakening security.

Remove unused dependencies, resources and obsolete WebRTC code.

Keep release builds minified and resource-shrunk where safe.

Document measurable tradeoffs.

# 21. Milestone M17 — Documentation and Cleanup

Update:

- README.md
- README.uk.md, if present.
- SPEC.md
- ARCHITECTURE.md
- PROTOCOL.md
- SECURITY.md
- TASKS.md
- AGENTS.md
- docs/VALIDATION.md

Create:

- docs/TOR_MIGRATION_PLAN.md
- docs/TOR_RUNTIME.md
- docs/PAIRING.md
- docs/ANDROID_LIFECYCLE.md
- docs/PHYSICAL_TEST_PLAN.md

Remove outdated instructions referring to WebRTC, STUN, TURN, SDP, public-IP pairing and port forwarding as supported runtime functionality.

Update AGENTS.md to explicitly record that the user approved the complete Tor migration and terminal UI redesign.

Document how to update the embedded Tor runtime securely.

# 22. Implementation Workflow for Codex

Work in a dedicated Git feature branch.

Execute milestones in dependency order.

For every milestone:

1. Inspect relevant existing code.
2. Implement the feature.
3. Add or update tests.
4. Run available tests.
5. Fix failures.
6. Review security and concurrency.
7. Update documentation.
8. Commit the milestone.

Continue until all milestones are completed or a concrete external blocker prevents further progress.

Do not stop after planning.

Do not ask for confirmation between ordinary implementation milestones.

Do not delete user data or reset identity without explicit approval.

Do not deploy infrastructure, purchase services, publish APKs or create releases without authorization.

Do not falsely report successful tests.

# 23. Final Acceptance Criteria

The implementation is complete when:

- Embedded Tor starts without Orbot.
- Each installation has a persistent v3 Onion Service.
- Two phones can pair using a shared invitation.
- Pairing requires no public IP, port forwarding or network settings.
- Both peers authenticate successfully.
- Encrypted text messaging works.
- Encrypted file transfers work.
- Delivery acknowledgments are correct.
- Message history survives restarts.
- Verified peers reconnect without repeating pairing.
- No WebRTC runtime dependency remains.
- No external signaling or TURN infrastructure is required.
- The Android interface uses the terminal design.
- Ukrainian and English localization work.
- Automated tests pass.
- APK builds successfully.
- Real-device results are documented honestly.
- Known Android and Tor limitations are documented.

# 24. Final Deliverables

Provide:

1. Complete implemented source code.
2. Updated architecture and security documentation.
3. Updated test suite.
4. Buildable debug APK.
5. Release build instructions.
6. Pairing and installation instructions.
7. Physical-device test checklist.
8. APK size and performance measurements.
9. List of known limitations.
10. Summary of commits and validation results.

**Final instruction:** Implement the complete specification. Prioritize correct Tor networking, security, and reliable messaging before visual polish. The final result must be a usable Tor-native Android messenger, not a hybrid prototype or documentation-only exercise.