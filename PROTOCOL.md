# MicroMiniChat Protocol v1

## Manual signaling

`MMC1:` followed by unpadded URL-safe Base64 of a big-endian envelope:
magic `0x4d4d4301` (4 bytes), role offer=1/answer=2 (1), session UUID (16),
SDP UTF-8 byte length (4), then SDP. Maximum text 256 KiB; SDP maximum 180 KiB.
Reject trailing bytes, malformed UTF-8/Base64, unsupported magic/version/role,
invalid lengths, more than 128 candidates, unknown candidate types, multiple/non-data
media sections and missing/conflicting/non-SHA-256 DTLS fingerprints.

The offerer creates a cryptographically random UUID. An answer must match a live
pending offer and can be applied once within ten minutes. Gather all ICE candidates
before export; gathering timeout is failure. A network change requires fresh pairing.
SDP is sensitive because it contains addresses. QR is not implemented.

The updated codec permits standard relay candidates; the session rejects them unless
TURN was explicitly enabled before pairing. TURN credentials are never serialized.
No envelope change is needed, but original 0.0.1 clients reject relay candidates.

## Authentication and framing

The human verifies and pins a persistent certificate fingerprint. libwebrtc DTLS
proves private-key possession and matches the SDP certificate. Only then is the
application protocol started. No separate cryptographic handshake or content cipher.

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
within five minutes; no application heartbeat runs after idle readiness.

## Text delivery and replay

The reliable ordered control channel carries text and ACKs. Sender allocates random
ID and monotonic per-peer sequence transactionally with Pending storage, then marks
Sending before enqueue. Only one text is awaiting ACK. Receiver validates monotonic
order, stores content plus unique ID/sequence/hash receipt and counter in one SQLite
transaction, and ACKs only after successful commit. A duplicate must match original
sequence/content hash; it is ACKed without another display row. Deleted history is
not recreated by a duplicate. Sequences may have gaps after deliberate local deletion.

ACK is empty and repeats ID/sequence. Only an ACK matching the outstanding message
marks Delivered. SCTP enqueue alone does not. Failed commit sends no ACK; disconnect,
enqueue failure or 15-second acknowledgement timeout leaves Failed. Retry keeps the
same ID/sequence. Earlier failures block later unconfirmed sequences until explicit
retry/reconnect. Durable receipts and session context survive/restrict retries.
No read receipts, offline server queue or delivery while the peer is terminated.

## Files

A separate reliable ordered file channel carries CHUNK only; metadata, acceptance,
chunk ACK, final hash and cancel use control. FILE_OFFER uses a random transfer ID,
size (8), name UTF-8 length (2)/bytes and MIME length (2)/bytes. Metadata maximum
1,100 bytes; each string maximum 512 bytes, MIME normalized to at most 128 ASCII
characters, display filename sanitized/truncated to 120 code points. Source must
have known size between 0 and 16 GiB. Recipient explicitly creates a SAF destination.

ACCEPT is empty, sequence 0. CHUNK sequence is exact expected byte offset; reject
zero/oversize chunks, gaps, overrun and overflow. ACK sequence is cumulative bytes
written and has no body. Sender waits for this application ACK before reading the
next chunk, providing bounded buffering even with a slow destination. Native enqueue
also respects 64 KiB control/128 KiB file queue thresholds. Text I/O remains separate.

END sequence is total size; body is 32-byte incremental SHA-256. Receiver requires
exact size and hash, flushes/closes output, then sends COMPLETE with the same size/hash.
Sender marks complete only after that confirmation. A mutable source is not a guaranteed
point-in-time snapshot; size changes are rejected and the hash validates streamed bytes.

CANCEL is empty. Close streams and attempt deletion of partial output; if the provider
refuses, report manual cleanup. Offer expiry is five minutes, sender ACK/completion
timeout 30 seconds, receiver inactivity 60 seconds (provider-blocked I/O can delay these).
One incoming/outgoing transfer is supported per session. Interrupted transfers restart
from zero with new selection/consent; resume and unknown-size spooling are not promised.
