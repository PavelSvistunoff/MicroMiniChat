# Resolved native runtime qualification issue

An early in-process JNI integration reproduced a native destroyed-mutex abort
during actual onion-service qualification. Startup alone had passed and did not
establish safe service operation. The upstream native root cause is not claimed fixed.

The implemented runtime executes the pinned bundled binary in a fresh owned child
process. Cookie-authenticated loopback control, TAKEOWNERSHIP and the parent PID
constrain its lifetime. No Java24 bridge is imported; JDK17/SDK35 remain supported.

Actual descriptor publication, stable persisted onion identity, normal exit and
restart passed on API35 arm64. Two foreground emulator installations also passed
signed native UI pairing, durable text ACK and authenticated reconnect. These are
software results, not physical-network or independent native-audit acceptance.

See [runtime qualification and updates](TOR_RUNTIME.md),
[validation](VALIDATION.md) and [milestones](../TASKS.md).
