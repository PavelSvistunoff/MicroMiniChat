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
retained. TLS 1.3 and the native UI enforce these gates before any application data.

## User procedure and migration

Install matching signed APKs without uninstalling existing data. Keep both apps
foreground. Create Chat waits for actual onion publication, then Share Invitation
uses Android Sharesheet; Copy Invitation is in the overflow menu and marks clipboard
content sensitive. Join Chat accepts paste/custom links and requires Connect.
Compare both complete fingerprints independently in Verify identity, then confirm
on both phones. A shared invitation alone is not identity verification.

Pending invitations expire after ten minutes or cancellation/restart. The creator
also enforces a monotonic deadline; valid wall clock is needed for certificate and
invitation validity. If the first response is lost after single-use consumption,
create a new invitation. No private keys are shared; treat the capability as secret.
QR display/scanning was deferred because secure share/paste needs no camera or new
scanner dependency. Unknown/old protocol URIs are rejected.

A verified route reconnects automatically with mutual certificate proof; both peers
must be available. Existing legacy certificates retain their fingerprint/validity;
legacy trust without a route requires explicit Tor verification. Missing/expired keys
fail closed and require a deliberate user decision. Settings reset confirmations
are separate: trust reset permits a new peer; identity reset rotates local TLS/onion
keys but retains history/remote trust. The other phone must explicitly reverify a
changed identity. Archived history stays readable and grants no authorization.

After an intentional local identity reset, the other phone must explicitly reset
its peer trust, create a new invitation, and have the reset installation join it.
The reset installation can retain its still-correct trust in the other phone;
both people must compare/confirm the new fingerprints again. An old invitation
or silently accepting the changed certificate is never a recovery shortcut.
