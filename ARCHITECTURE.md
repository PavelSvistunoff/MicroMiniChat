# Tor-native architecture

MainActivity → application-owned ChatSession → ReliableProtocol / FileTransfers →
FramedLink → mutual TLS 1.3 / TorConnector → loopback SOCKS5 → bundled Tor child →
remote onion service. There is no alternate transport or application backend.

## Ownership and threads

ChatApp retains a single coordinator across Activity recreation. ChatSession's
HandlerThread serializes session state, SQLite and protocol callbacks. MainActivity
observes snapshots on the main thread; network and storage do not block native Views.
Generations, pairing epochs and link-instance checks reject stale callbacks.
TorConnector binds a loopback listener, accepts at most two handshake workers with
no waiting queue and routes outgoing canonical onion names through explicit SOCKS5.
FramedLink has independent reader/writer threads, bounded priority queues, deadlines
and bilateral verification gates. Lower-onion initiated connections win deterministic
arbitration. Network callbacks trigger bounded jittered exponential reconnects.

TorRuntime runs the verified executable from Android's extracted native directory
in a fresh owned child process. Cookie-authenticated loopback control, TAKEOWNERSHIP
and owning PID constrain orphan lifetime. Actual descriptor UPLOADED gates readiness.
No JNI reinitialization, Android service, runtime binary download or public listener.
See docs/TOR_RUNTIME.md for persistence, source provenance and updates.

## Storage and trust

Production retains history-main.db, identity-main and trust-main names. Existing
SQLite messages, receipts, counters, transfer records and wrapped identity stay local.
Schema2 adds only transfer creation timestamps; legacy unknown timestamps stay zero.
New transfer updates preserve creation time so the bounded UI merges text/file rows
chronologically without inventing dates for old records.
TLS reads legacy AES-GCM-wrapped PEM unchanged; new signing keys use Android Keystore.
Missing/expired established identities fail closed. Onion keys are Tor-managed in
no-backup storage. Route and certificate trust commit together only on explicit
verification. Archived pairing history is visible but cannot authorize a peer.
History deletion preserves minimal dedupe receipts/counters. Reset operations are
confirmed, separate from normal lifecycle and never performed during upgrades.

ReliableProtocol allocates durable IDs/sequences, flushes bounded text and marks
Delivered only after the receiver has committed and returned a matching ACK.
FileTransfers owns independent bounded SAF workers with one in-flight chunk per
direction, integrity/completion ACK and partial cleanup. Provider stalls are isolated
from UI/text but can delay cancellation; process-wide worker slots bound damage.

## UI and lifecycle

Native terminal Views, selectable literal message text, bounded history pages,
English/Ukrainian resources, locale timestamps and retained composer. No command
execution or automatic link/file opening. Foreground-only networking with a checked
two-minute background grace; return reauthenticates. No permanent service/push.
See SECURITY.md, PROTOCOL.md and docs/ANDROID_LIFECYCLE.md for boundaries.
