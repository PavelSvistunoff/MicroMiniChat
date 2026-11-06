# Tor invitation protocol v2

Invitation URI: `microminichat://pair/v2#` followed by canonical unpadded URL-safe
Base64 of exactly 138 bytes: magic/version 0x4d544902 (4), canonical v3 onion
address including `.onion` (62 ASCII), SHA-256 certificate fingerprint (32), random
capability (32), expiration in epoch milliseconds (8), all network byte order.
The lifetime is ten minutes. Input is bounded to 512 characters. Parsing does no
network access and never grants trust. Invalid address checksums/versions,
noncanonical encoding, expired or implausibly distant expiration are rejected.

Create stores only a SHA-256 capability hash and expiry in private preferences.
Consumption removes them in a durable commit before success. Wrong/expired/cancelled
and previously consumed capabilities fail. A process restart cancels pending
pairing; a new invitation is required. Invitation possession permits one pairing
attempt, not identity verification or message access. Keep it private when sharing.

Public certificate fingerprints and routes are committed together only after
explicit human verification. Existing trust without an onion route requires this
explicit binding. Unexpected identity replacement is blocked; local history is
retained. End-to-end TLS and product UI integration follow in M05/M06/M11.
