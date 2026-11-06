# MicroMiniChat Tor protocol v2

Invitation URI: `microminichat://pair/v2#` followed by canonical unpadded URL-safe
Base64 of exactly 138 bytes: magic/version 0x4d544902 (4), canonical v3 onion
address including `.onion` (62 ASCII), SHA-256 certificate fingerprint (32), random
capability (32), expiration in epoch milliseconds (8), all network byte order.
The lifetime is ten minutes. Input is bounded to 512 characters. Parsing does no
network access and never grants trust. Invalid address checksums/versions,
noncanonical encoding, expired or implausibly distant expiration are rejected.

## Tor transport v2

The transport uses TLS 1.3 and ALPN mmc-tor/2 over an explicitly configured loopback
SOCKS proxy. Only checksum-valid canonical .onion domains are dialed; DNS is never
used for remote routing and there is no direct fallback. Mutual certificate proof
precedes the pairing greeting. The creator requires a live capability for initial
pairing; reconnect requires both persisted peer certificate and onion route.

Inside TLS, the initiator sends magic 0x4d544802, mode (1 pairing / 2 reconnect),
a fresh session UUID (16), its onion route (62 ASCII) and capability (32; zeros for
reconnect). The responder verifies authorization and replies with the same magic
and its route (62). Invitation certificate pin and reply route must both match.
A consumed capability is durably removed before success is returned.

Each stream sends preface 0x4d544302, then records: length (4) and channel (1) plus
body. Maximum body is 65,536 bytes; negative/zero/oversized lengths are rejected
before allocation. Channels: 0 application control, 1 file chunk, 2 verified marker
(empty), 3 heartbeat (empty). Product frames are accepted only after both local
and remote verification markers. Existing application Frame v1 records/session
UUIDs and durable ACK semantics are retained. Control queue is capped at 64 KiB,
file queue at 128 KiB, plus one bounded in-flight record. Control has priority;
stop-and-wait file chunks prevent large file enqueues from starving text.

Read timeout is 90 seconds; a 30-second idle heartbeat detects dead peers. A
60-second write watchdog closes a stuck socket. Unverified streams expire after
five minutes. Handshake workers are capped at two with no waiting queue, incoming
backlog four, greeting/TLS timeout 60 seconds, outgoing Tor connect bound 120 seconds.
No product success state is emitted before mutual authentication/verification.

## Application records and durable semantics

Every binary record has a 50-byte header, network byte order:

| Field | Bytes |
| --- | ---: |
| Magic `0x4d4d4346` | 4 |
| Version (=1), type | 1 each |
| Session UUID, message/transfer UUID | 16 each |
| Nonnegative sequence/offset | 8 |
| Exact payload length | 4 |

Maximum frame 65,536 bytes, text 16,384 UTF-8 bytes, file chunk 16,384 bytes.
Reject malformed UTF-8, invalid/unknown/reserved types/versions/lengths and another
session ID. No arbitrary errors or user payloads are reflected into logs.

Types: HELLO=1, TEXT=2, ACK=3, FILE_OFFER=4, FILE_ACCEPT=5, FILE_CHUNK=6,
FILE_CANCEL=7, FILE_END=8, FILE_COMPLETE=9; 10 reserved/rejected; FILE_CHUNK_ACK=11.
HELLO has sequence 0 and a four-byte supported-version bitmask (bit 1 selects v1).
Exchange HELLO before application data. After local verification, version negotiation expires
within 60 seconds. The outer stream supplies idle heartbeats.

## Text delivery and replay

The reliable ordered control channel carries text and ACKs. Sender allocates random
ID and monotonic per-peer sequence transactionally with Pending storage, then marks
Sending before enqueue. Sent follows the actual stream-flush callback and does
not mean Delivered. Only one text is awaiting ACK. Receiver validates monotonic
order, stores content plus unique ID/sequence/hash receipt and counter in one SQLite
transaction, and ACKs only after successful commit. A duplicate must match original
sequence/content hash; it is ACKed without another display row. Deleted history is
not recreated by a duplicate. Sequences may have gaps after deliberate local deletion.

ACK is empty and repeats ID/sequence. Only an ACK matching the outstanding message
marks Delivered. Queueing or socket flush alone does not. Failed commit sends no ACK; disconnect,
enqueue failure or 60-second acknowledgement timeout leaves Failed. Retry keeps the
same ID/sequence. Earlier failures block later unconfirmed sequences until explicit
retry/reconnect. Durable receipts and session context survive/restrict retries.
No read receipts, offline server queue or delivery while the peer is terminated.

## Files

A multiplexed file channel carries CHUNK only; metadata, acceptance,
chunk ACK, final hash and cancel use control. FILE_OFFER uses a random transfer ID,
size (8), name UTF-8 length (2)/bytes and MIME length (2)/bytes. Metadata maximum
1,100 bytes; each string maximum 512 bytes, MIME normalized to at most 128 ASCII
characters, display filename sanitized/truncated to 120 code points. Source must
have known size between 0 and 16 GiB. Recipient explicitly creates a SAF destination.

ACCEPT is empty, sequence 0. CHUNK sequence is exact expected byte offset; reject
zero/oversize chunks, gaps, overrun and overflow. ACK sequence is cumulative bytes
written and has no body. Sender waits for this application ACK before reading the
next chunk, providing bounded buffering even with a slow destination. Stream enqueue
also respects 64 KiB control/128 KiB file queue thresholds. Control records have priority over the next file chunk.

END sequence is total size; body is 32-byte incremental SHA-256. Receiver requires
exact size and hash, flushes/closes output, then sends COMPLETE with the same size/hash.
Sender marks complete only after that confirmation. A mutable source is not a guaranteed
point-in-time snapshot; size changes are rejected and the hash validates streamed bytes.

CANCEL is empty. Close streams and attempt deletion of partial output; if the provider
refuses, report manual cleanup. Offer expiry is five minutes, sender ACK/completion
timeout 30 seconds, receiver inactivity 60 seconds (provider-blocked I/O can delay these).
One incoming/outgoing transfer is supported per session. Interrupted transfers restart
from zero with new selection/consent; resume and unknown-size spooling are not promised.
