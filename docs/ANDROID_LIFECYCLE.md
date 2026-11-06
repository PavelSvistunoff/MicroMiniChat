# Android lifecycle and connectivity policy

The app owns one serialized conversation coordinator across Activity recreation.
SQLite history/receipts, peer route/trust, TLS identity and Tor onion keys persist
across process death. Pending text stays local; no server queues it. A new launch
starts Tor and authenticates the stored peer again before retrying delivery.
Incoming delivery is unavailable after force-stop/termination.

Tor runs in an owned native child process. TAKEOWNERSHIP closes it when control is
lost; the owning-controller PID provides an additional parent-death guard. Normal
shutdown closes sockets, cancels handshake workers and file engines, interrupts
pending writes, terminates/reaps the child off the main thread and unregisters
network callbacks on coordinator shutdown. Native state is never reinitialized in
the application process. Keystore/SQLite and SAF operations stay off the UI thread.

The foreground-only product policy has a best-effort two-minute grace for sharing
and SAF pickers. Expiry is checked on returning to the foreground as well as by a
timer; suspension cannot extend it. Android may freeze/kill the process earlier.
After expiry the app closes Tor/streams; returning restarts and reconnects stored
verified peers. Explicit Disconnect stays disconnected across Activity recreation.
Draft text belongs to the retained session/view state. Interrupted files restart
from zero with renewed source/destination consent; providers may delay cleanup.

## Foreground service evaluation

No continuous-connectivity foreground service is enabled. The current Android
remoteMessaging category describes continuity of a user's messaging tasks between
their devices; that is not this two-person messenger's demonstrated behavior.
connectedDevice directs remote messaging to its corresponding category. A finite
file transfer could use dataSync, but that is not an indefinite incoming-message
permission; Android 15+ imposes a shared six-hour/24-hour background limit and
start restrictions. A service was therefore not added just to retain sockets.
There is no boot receiver, push, wake-lock keepalive or permanent-background claim.

Sources reviewed during migration:
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/timeout

API 26/33/34/35+ physical-device sleep/Doze/OEM behavior remains NOT VERIFIED unless
specifically recorded in VALIDATION.md. Emulator lifecycle tests do not establish
physical battery or terminated-app delivery behavior.
