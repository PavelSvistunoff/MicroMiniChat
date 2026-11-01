# Network Test Matrix

No physical-device/network matrix tests have been performed. An in-process pair
of native peers passed synthetic roundtrip/repeated-disposal tests on an API 35
arm64 AOSP emulator. This is emulator evidence only and satisfies none of the
real-device network rows below. Never retain SDP, IPs or message contents in reports.

| Scenario | Environment | Actual result | Expected interpretation |
| --- | --- | --- | --- |
| 1. Two devices on the same Wi-Fi | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 2. Different Wi-Fi networks | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 3. Wi-Fi to mobile data | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 4. Mobile data to mobile data | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 5. One device behind CGNAT | Untested; real hardware required | Not run | Direct failure may be an architectural limitation |
| 6. Both devices behind restrictive NAT | Untested; real hardware required | Not run | Direct failure may be an architectural limitation |
| 7. IPv6 connectivity | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 8. UDP-blocked network | Untested; real hardware required | Not run | Direct failure may be an architectural limitation |
| 9. Network switching during active session | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 10. Temporary connectivity loss | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 11. Application backgrounding | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |
| 12. Application process termination | Untested; real hardware required | Not run | Observe behavior; no guaranteed reachability/background execution |

For each run record date, build/commit, device model/OS/ABI, network categories,
STUN endpoint consent, gather/connect timings, coarse selected candidate type,
steps, result and sanitized failure code. Distinguish hardware, emulator and
simulation; do not mark unknown NAT topology as confirmed CGNAT. For M02 use only
synthetic payloads. Process termination must not imply automatic incoming delivery.

## Additional emulator evidence (not physical-network rows)

API 35 arm64: native two-peer repeated roundtrip/disposal, verified chat and durable
ACK/reconnect, simulated default-network change, explicit stale-answer rejection,
100 ms test-policy background expiry/reconnect, and SAF fixture streaming/cancel/
corrupted-hash rejection pass. Production background grace is 120 seconds. The short
test duration exercises the same policy without claiming a real battery/sleep test.
Release two-UID smoke, if recorded, is still one emulator and uses no public STUN.


## TURN source update

After the reported real-device `FAILED_ICE_FAILED` on different networks, the user
approved TURN. Add relay-only UDP, TCP and TLS passes on two independent networks,
invalid/expired credentials, TLS failure and unreachable-server cases. All remain
**untested**: no deployed TURN endpoint/credentials or attached physical phones are
available. The screenshot records failure only; its underlying cause is not proven.


## Phone-hosted pairing source update

The latest user choice is a creator-phone listener, accepting public-IP and forwarding
limitations. Emulator loopback and two-app-UID checks do not satisfy the matrix’s
separate-network hardware cases. Public IPv4 TCP forwarding to the phone, global IPv6
with inbound firewall permission, CGNAT failure and mobile OEM background behavior all
remain **untested**. TURN only affects WebRTC and cannot make this listener reachable.
See PHONE_PAIRING.md for setup and PHONE_PAIRING_SMOKE.json for any signed emulator evidence.
