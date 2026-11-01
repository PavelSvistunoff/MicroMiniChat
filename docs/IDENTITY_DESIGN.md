# Identity design decision for 0.0.1

Use a persistent ECDSA WebRTC DTLS certificate as the peer identity. Compare the
SHA-256 DER-certificate fingerprint through an independently authenticated channel
before trusting a first peer. Persist that exact fingerprint, reject changed keys,
and configure libwebrtc with the same local certificate on later connections.
The standard DTLS handshake proves private-key possession and libwebrtc validates
SDP fingerprints against the certificate actually used. This avoids a new signing
handshake and application-layer content encryption.

Relevant established specifications are RFC 8122 (certificate fingerprints), RFC
8842 (DTLS/SDP) and RFC 8827 (WebRTC security). All applicable fingerprint attributes
must agree on one SHA-256 certificate identity; reject extra media sections or
conflicting fingerprints. A fresh offer/answer session ID is echoed inside the
DTLS-authenticated HELLO exchange, while durable message IDs handle application
retries. A copied old SDP alone cannot prove possession of the private key.

The Android WebRTC API accepts PEM material rather than a Keystore signing handle.
Generate the identity with WebRTC's established certificate generator. Encrypt its
PEM private material at rest using AES-256-GCM with a nonexportable Android Keystore
wrapping key and fresh random nonce. There is no plaintext key on disk. This is
storage encryption, not an extra content-encryption layer. Native/Java memory
contains unwrapped identity material while used; the identity itself is not claimed
to be hardware-bound. Keystore loss/corruption must fail closed and require explicit
identity reset, never silently replace a trusted identity. Certificate expiry or
reset changes the fingerprint and requires independent reverification.

No message/file frame may be processed before the current session has completed
DTLS and explicit peer verification (or matched previously verified trust).
Fingerprint comparison is a human operation; clicking trust without comparing
does not authenticate the intended person. Compromised devices and metadata remain
outside protection. These design claims require automated adversarial and device
checks; they are not an independent security audit.


## Certificate lifetime correction in 0.0.3-preview

Emulator measurement confirmed that the pinned WebRTC native generator consumes
milliseconds: passing 3600 produced about three seconds of remaining X.509 validity,
not one hour. The native [generator contract](https://webrtc.googlesource.com/src/+/refs/heads/main/rtc_base/rtc_certificate_generator.h)
uses milliseconds. New pairing certificates now request one hour and new persistent
identity certificates request 365 days using explicit TimeUnit conversions; instrumented
tests check the actual parsed notAfter timestamps. Previous 0.0.1/0.0.2 identity
generation requested 31,536,000 milliseconds (about 8.76 hours), despite the original
one-year documentation. Existing saved certificates retain their actual expiry and
are never silently replaced. After expiry, an explicit identity reset is required;
it deletes local history/metadata and requires independent peer verification again.
