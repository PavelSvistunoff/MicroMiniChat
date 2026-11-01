# Optional TURN for internet connections

## Status and tradeoff

The user approved relay support after a real two-network pairing reported
`FAILED_ICE_FAILED`. Source now supports an explicitly configured authenticated
TURN endpoint, TCP/TLS URLs and relay-only testing. No relay is provisioned or
contacted by default. The checked-in 0.0.1 APKs predate this change and do not support it.
Manual signaling and fingerprint verification still apply. TURN forwards encrypted
DTLS packets; it does not store offline messages or authenticate the human peer.
Its operator observes addresses, timing and volume, and can disrupt service.
Bandwidth/hosting costs and server administration are additional requirements.

## Configure both phones

Obtain a TURN endpoint and credentials from your own server or chosen provider.
In **Pair / verify → Connection settings**, enter the TURN URL, username and password on both phones:

- `turn:relay.example.org:3478?transport=udp` for ordinary UDP access.
- `turn:relay.example.org:3478?transport=tcp` when client UDP is blocked.
- `turns:relay.example.org:5349?transport=tcp` for TLS with a valid server certificate.

Only one TURN URL is supported per pairing. Try TLS/TCP for restrictive networks;
a dedicated TLS listener on port 443 can help where 5349 is blocked, but captive
portals/proxies may still prevent connections. Never disable certificate validation.
Select **Relay only** to require a TURN allocation; otherwise ICE can use direct
candidates or TURN. Create fresh offer/answer payloads after changing settings.
Older 0.0.1 clients reject relay candidates; update both phones for relay pairing.

Credentials are masked, excluded from saved UI state and held only in memory during
pairing/connection; they are not stored in preferences, messages or signaling.
Re-enter them after dismissing pairing or restarting. Prefer expiring, per-user
credentials; do not embed a provider master secret or shared deployment password
in an APK. Enter secrets locally, never in chat, issues or version control.
When direct mode is selected, signaling advertising relay candidates is rejected.

## Self-hosted coturn checklist

Use a maintained coturn package on a dedicated public host. This repository does
not deploy a server. Follow the upstream [configuration reference](https://github.com/coturn/coturn/blob/master/examples/etc/turnserver.conf)
for your installed version and environment.

1. Allocate a public IP and DNS name; obtain a valid TLS certificate/key. Configure
   `listening-port`, `tls-listening-port`, `realm`, `fingerprint`, `cert` and `pkey`.
   Use `external-ip=PUBLIC/PRIVATE` if the host itself is behind static NAT, with
   corresponding forwarding. Prefer a directly addressed host for the first test.
2. Require authentication with `lt-cred-mech` and strong per-user accounts created
   locally using `turnadmin`, or a properly operated expiring-credential mechanism.
   Never enable `no-auth`. Keep the config/database and TLS private key restricted.
3. Open the chosen UDP/TCP listener ports and a bounded UDP allocation range using
   `min-port`/`max-port`. Forward that range if needed. TURN over TCP/TLS describes
   the client-to-server leg; WebRTC still needs the server's UDP relay sockets.
4. Deny relay access to loopback, private/LAN, link-local, multicast and cloud metadata
   ranges for IPv4 and IPv6 using coturn peer restrictions and host firewall policy.
   Review IPv4-mapped IPv6 bypasses. Disable administrative access from the internet,
   impose allocation/bandwidth quotas and avoid recording sensitive session addresses.
5. Test authenticated allocation from outside the server network. Test both phones
   on different networks with **Relay only**, then exchange messages and a file.
   Record actual hardware, network class, result and timing without credentials/IPs.

A configured URL alone is not evidence of a working server. Bad credentials, expired
credentials/certificates, blocked allocation ports or an unreachable server can fail.
There is no automatic credential refresh or hosted credential service in this app.

## Validation still required

Real two-phone, relay-only UDP/TCP/TLS tests; invalid/expired credentials; unreachable
server; certificate failure; cancellation/file integrity through relay; network changes
and restrictive mobile NAT. No such result is claimed until a real relay is available.
The Android API mapping follows upstream [PeerConnection](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/PeerConnection.java).
