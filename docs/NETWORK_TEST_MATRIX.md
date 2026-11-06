# Tor network validation

The executable physical-network checklist and measurement template are in
[PHYSICAL_TEST_PLAN.md](PHYSICAL_TEST_PLAN.md). Every unexecuted physical scenario
remains NOT VERIFIED, including real Wi-Fi, mobile, CGNAT, network handover, OEM
sleep and battery behavior.

Software evidence is in [VALIDATION.md](VALIDATION.md): real embedded Tor lifecycle,
onion publication/persistence, mutual TLS, text ACKs, files, reconnect and two
foreground emulator installations. Emulator results do not qualify physical networks.

Keep both apps foreground, compare full fingerprints independently, use synthetic
content, record actual durations/resources and never log invitations, routes, keys
or message/file contents. Record failures and recovery instead of inferring success.
