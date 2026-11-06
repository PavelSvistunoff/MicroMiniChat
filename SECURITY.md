# Tor-native security model

Protect message/file confidentiality and integrity in transit, persistent peer
continuity, durable delivery/replay semantics and app-private identity material.
This is an implementation review, not an independent cryptographic/native audit
or an anonymity guarantee. Physical-device/network acceptance remains deferred.

## Transport and authentication

All remote application streams use an explicit SOCKS5 request to the bundled
Tor's loopback listener. Remote targets must pass canonical v3 address, version
and SHA3 checksum validation. There is no DNS, system proxy, direct transport or
external signaling fallback. Tor itself necessarily connects to the Tor network.

Pinned Conscrypt 2.8.0 provides TLS 1.3 only, mutual P-256 certificate proof, fresh
session keys and ALPN mmc-tor/2. Session tickets are disabled and no early product
data or TLS downgrade is configured. Outgoing peers pin the invitation/verified
certificate; known incoming peers pin durable trust. A first incoming self-signed
certificate must be valid, self-verifying and P-256, but remains only a candidate:
a live capability and explicit human verification on both ends gate all product
frames. Self-signature alone grants no messaging permission. Compare both full
fingerprints independently; an invitation alone cannot authenticate its sender.

Legacy AES-GCM-wrapped PEM is read without rewriting it or changing fingerprints;
original validity remains enforced. New signing keys are nonexportable Android
Keystore P-256 keys. Hardware backing is not assumed. Conscrypt prehashes the
handshake transcript and requests raw ECDSA, requiring DIGEST_NONE key permission.
Private legacy material exists in process memory during use; zeroization of Java
Strings and protection from a compromised endpoint are not promised.

Peer fingerprint and onion route commit atomically after human approval. Changed
identities/routes are blocked. Legacy trust without a route can create/join a Tor
invitation and requires explicit route verification. Identity reset is confirmed,
rotates local TLS/onion material, preserves history and does not silently reset
remote trust. Earlier history remains visible with an archived-pairing label;
its partition is never used to authorize the current peer.

## Invitations, replay and resource limits

Invitations carry a 256-bit random capability, public certificate fingerprint,
canonical onion route, version and ten-minute expiry; no private keys. Only a
SHA-256 capability hash is persisted. Consumption removes it durably before
success. Wrong, cancelled, expired and replayed capabilities fail. Both wall-clock
and monotonic deadlines constrain active invitations; restart cancels pending
pairing. A lost response after consumption may require a fresh invitation.
Custom URI handlers/sharing applications are external: importing never connects
or grants trust; only explicit Connect does. Clipboard export marks sensitive data.

Pairing epochs and runtime/link identity checks reject late callbacks after cancel,
replacement and shutdown. Duplicate simultaneous connections deterministically
select the lower-route initiated stream. Pending message IDs/receipts survive
arbitration. TLS prevents network authentication replay; durable ID/sequence/hash
receipts constrain application retries. An authenticated malicious recipient can
still lie about its own storage or copy received material.

At most two unauthenticated workers, no waiting worker queue, backlog four,
60-second TLS/greeting deadline and 120-second SOCKS bound. Stream lengths are
validated before allocation (65,536-byte body maximum), channel/version/session
checks are strict, queues are capped at 64 KiB control/128 KiB file plus one frame,
and coordinator/file input queues at 256 KiB. More than 512 records/second closes
the stream. Heartbeats/read/write/unverified deadlines close stalled peers.
Network I/O, TLS/Keystore operations and file/database processing run off the main
thread. UI invitation review uses only the fixed-size bounded parser/checksum.

## Storage, files and runtime control

SQLite is UID-private, not separately encrypted against an unlocked/compromised OS.
Receipt/content/sequence commits precede ACK; writes/queueing alone never mean
Delivered. Failed storage closes the stream. History deletion preserves minimal
receipts/counters, preventing retransmission from recreating deleted messages.
Backup/cloud/device transfer are excluded. Flash forensic erasure is not promised.

SAF only, explicit source/destination consent, safe filenames/MIME, known size up
to 16 GiB, bounded chunks, SHA-256 verification and completion ACK. No automatic
opening/execution. Cancel/failure closes streams and attempts partial deletion;
providers may block or refuse cleanup, requiring manual action. Interrupted files
restart from zero with renewed consent. Provider workers have a process-wide cap.

Tor state/onion keys are in private no-backup storage. A missing established onion
secret fails closed rather than generating a replacement. Tor-managed keys are not
claimed hardware-backed. The checksum-pinned executable runs in a child process;
SOCKS/control bind only loopback and control requires its private 32-byte cookie.
TAKEOWNERSHIP and parent PID constrain orphan lifetime. Output is discarded. Each
restart creates fresh native globals; JNI is not loaded into the product process.
The child shares the application UID; this is lifecycle/crash containment, not a
separate security sandbox. The app logs no text, addresses, invitations, keys or file contents. Diagnostics
use fixed state/error codes. Sensitive UI screenshots are restricted by FLAG_SECURE.

The only exported product component is the reviewed Activity; no exported control
service/provider or boot/push receiver exists. The test document provider is debug
only and protected by MANAGE_DOCUMENTS; release packaging must exclude it.

## Supply chain, limitations and reporting

Runtime coordinates/hashes are pinned and notices retained. Tor Android .14 is the
published artifact inspected; newer Tor core .15/.16 announcements and upstream
native crash reports require ongoing applicability review. Hash pinning is not a
proof of binary provenance/reproducible builds. The pinned Conscrypt AAR records
BoringSSL revision 427ec40cc8edc545253289232e885708c409e5ea; its exact upstream
license and the Conscrypt/Netty/Harmony/jtorctl notices are bundled.
See docs/TOR_RUNTIME.md for secure
updates/source-build qualification. Conscrypt/Bouncy Castle are maintained standard
implementations; no new crypto primitives or authentication handshake were invented.

Tor guards/network operators can see timing, volume and endpoint connections;
stable onion/certificate identities are correlatable. Tor may be blocked or slow.
Global traffic correlation, malicious keyboards/screenshots, rooted OS, coercion,
recipient copying and endpoint compromise are outside protection. The foreground
policy is finite and no terminated-app delivery is promised. Report issues privately
through a channel agreed with the maintainer; no response-time SLA is configured.
