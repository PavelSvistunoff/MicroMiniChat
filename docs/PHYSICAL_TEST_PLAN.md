# Physical-device acceptance checklist

Only an API 35 arm64 AOSP emulator is available to this migration run. It can run
two independent embedded Tor clients/onion services, but does not establish any
physical Wi-Fi/mobile/CGNAT, radio, OEM battery or multi-phone result. Physical
acceptance was explicitly deferred. Every physical scenario below is NOT VERIFIED.

Use two disposable test installations with consented synthetic text/files. Do not
reset existing identities/history or log invitations, addresses, keys or contents.
Install the same qualified APK version and record its SHA-256. Keep both phones
foreground. Create/share one invitation, explicitly accept it, independently compare
both full fingerprints and confirm on both devices before testing product traffic.

| Scenario | Physical status | Procedure / observable acceptance |
| --- | --- | --- |
| Two phones, same Wi-Fi | NOT VERIFIED | Pair through Tor, exchange bidirectional text and a verified file. |
| Different Wi-Fi networks | NOT VERIFIED | Repeat without IP settings, forwarding or a backend. |
| Wi-Fi to mobile data | NOT VERIFIED | Verify mutual identity, durable delivery and file hash. |
| Mobile to mobile | NOT VERIFIED | Repeat with both real radios; record carrier restrictions. |
| CGNAT | NOT VERIFIED | Establish actual provider/network conditions; do not infer from emulator NAT. |
| Tor bootstrap failure | NOT VERIFIED | Block Tor connectivity; report failure, never Connected; restore and recover. |
| Temporary Tor disconnection | NOT VERIFIED | Interrupt connectivity, queue text, restore and verify one durable delivery. |
| Android app restart | NOT VERIFIED | Force-stop/relaunch, verify stable identity/history and automatic authenticated reconnection. |
| Device network change | NOT VERIFIED | Change actual default network mid-session; verify state/retry and no duplicate display. |
| Long-running chat | NOT VERIFIED | Run foreground for at least 60 minutes with intermittent text; record failures/resources. |
| Large file | NOT VERIFIED | Use at least 100 MiB, explicit SAF acceptance, concurrent text, completion ACK and independent SHA-256. |
| Interrupted file | NOT VERIFIED | Interrupt/cancel at multiple offsets; no false completion, close streams and inspect partial cleanup. |
| Peer restart | NOT VERIFIED | Restart either peer/Tor, retain route/trust, drain pending IDs without a new invitation. |
| Identity mismatch | NOT VERIFIED | In a disposable test profile rotate identity explicitly; old peer blocks it until deliberate reverification. |

For every execution record:

- Date, APK hash/version, device model, Android/API/ABI and OEM battery policy.
- Scenario, network type/carrier and whether CGNAT conditions were established.
- Cold-start, Tor bootstrap, onion publication and pairing/authentication durations.
- Text counts, pending/sent/delivered outcomes, retries, duplicate display and failure recovery.
- File bytes, duration/throughput, source/destination SHA-256, consent/cancel/cleanup behavior.
- App plus Tor-child PSS/RSS during idle, active text and transfer; measurement method.
- Actual idle battery observations over a stated duration, screen state and network use.
- Unexpected identities, errors, cleanup failures and exact recovery actions.

Also exercise font scaling, small screens, Ukrainian/English, keyboard/SAF/share
switches, short background grace, grace expiry, Doze, process kill and force-stop.
Do not promise continuous background or terminated-app incoming delivery. Real Tor
on an emulator and deterministic fakes must be identified separately in results.
