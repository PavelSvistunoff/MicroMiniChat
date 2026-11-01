# Architecture and Decisions

## Modules and ownership

`:app` is the messenger; `:probe` is the original synthetic WebRTC experiment.
Java 17/native Views avoid Compose/Kotlin runtime dependencies. SQLite and Android
Keystore/SAF use platform APIs. The only app runtime library is the pinned WebRTC AAR.
AndroidX runner/JUnit are test-only.

```text
MainActivity → ChatSession (one serialized coordinator)
                   ├─ Signal: independent bounded offer/answer codec
                   ├─ IdentityStore + TrustStore
                   ├─ ReliableProtocol → MessageDatabase
                   ├─ FileTransfers (independent streaming worker)
                   └─ WebRtcTransport → ICE / DTLS / SCTP / two DataChannels
```

`ChatApp` owns one session across activity recreation. UI updates use the main thread;
SQLite/protocol state use the coordinator. Native callbacks are bounded before entering
that queue and guarded against stale connections. User file I/O uses a separate worker,
so provider writes do not block text. A process-wide cap prevents unbounded file workers
if external providers stall. No private payload is sent to diagnostic logging.

## Decisions and tradeoffs

- **WebRTC:** established transport crypto, congestion control and ICE; native size
  dominates the APK. Runtime/native tests determine feasibility before adoption.
  `150.7871.04` Maven AAR passes emulator tests. An alternative publisher bundle failed
  native compatibility and was rejected; it is not a hidden replacement dependency.
- **ICE with opt-in TURN:** no signaling server or default external traffic. Users
  explicitly configure STUN and authenticated TURN URLs. TURN credentials remain in
  memory, never in signaling/preferences. Direct mode rejects advertised relays.
  Relay-only mode requires allocation; TCP/TLS endpoints support restrictive client
  networks. This user-approved change introduces relay hosting/traffic costs; see
  `docs/TURN.md`. It does not add offline delivery or bypass all firewalls.
- **Non-trickle signaling:** export after gathering completes, including candidates.
  Two manual payloads; failure/timeouts require a new exchange. Single-code QR is not
  assumed practical for SDP size.
- **Standard certificate pinning:** persist a DTLS ECDSA certificate, compare its
  SHA-256 DER fingerprint independently, then pin it. DTLS checks the live certificate.
  This avoids a custom signing handshake and extra application content encryption.
  PEM storage is encrypted by a Keystore AES-GCM key; the signing identity itself
  is not claimed to be hardware-bound. See `docs/IDENTITY_DESIGN.md`.
- **Durable delivery:** receiver commits receipt/content/sequence in one SQLite
  transaction before ACK. Sender does not equate SCTP enqueue with delivery.
- **Two reliable ordered channels:** control/text has a separate native queue from
  files. File chunks use stop-and-wait application ACKs after provider writes,
  keeping memory bounded at the cost of throughput proportional to network RTT.
- **Minimal UI:** one chat screen, pairing dialog and necessary settings. No contact
  list or profile UI. Paging keeps displayed history bounded.

## Background behavior

No foreground service is declared. Android's service categories were reviewed:
`remoteMessaging` describes continuity across a user's devices, while `connectedDevice`
redirects remote messaging to its corresponding category. Neither is selected simply
to keep sockets alive. Finite transfer `dataSync` would have eligibility/start restrictions
and Android 15+ time limits; no such service is implemented in this preview.
See [service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
and [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

A best-effort 120-second background grace accommodates sharing and pickers. Expiry
is checked again on foreground return, so a suspended timer cannot extend a session.
Android can kill/freeze the process earlier. Loss, network changes and termination
require explicit pairing; there is no automatic incoming connection after death.
