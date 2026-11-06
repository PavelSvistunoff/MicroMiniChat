# Persistent identity and trust

Each installation owns a persistent TLS certificate and a persistent Tor v3 onion
identity. New P-256 signing keys use Android Keystore; existing AES-GCM-wrapped
PEM material is read without rewriting its certificate or fingerprint. Missing or
expired established keys fail closed. Hardware backing is not assumed.

Conscrypt supplies mutual TLS1.3 with ALPN mmc-tor/2. The invitation pins the creator's
certificate; both people compare full fingerprints independently before confirming.
Certificate fingerprint and onion route are committed together. Unexpected changes
are blocked; archived history remains visible and never authorizes a new peer.

Confirmed identity rotation preserves history and remote trust. The other person
must explicitly reset trust and verify the new identity. Normal upgrades never
reset identities or erase history. Tor-managed keys remain in private no-backup
storage; missing established onion keys require deliberate recovery.

See [security](../SECURITY.md), [pairing](PAIRING.md) and
[runtime storage/update details](TOR_RUNTIME.md).
