# MicroMiniChat 0.1.0-tor-preview

Complete Tor-only Android migration: bundled persistent v3 onion services, mutual
TLS1.3, single-use private invitations, bilateral identity verification, automatic
verified-peer reconnect, durable text ACK/deduplication and consented SAF file streaming.
Native terminal UI with English/Ukrainian resources and retained drafts. WebRTC,
phone-hosted signaling, ICE/STUN/TURN/SDP and the obsolete probe are removed.

Preview qualification is in docs/VALIDATION.md. Real Tor emulator checks are distinct
from physical-phone/mobile/CGNAT/OEM battery acceptance, which remains NOT VERIFIED.
Keep both apps open. Tor may be blocked or slow, files restart from zero after
interruption, and providers may refuse partial cleanup. No push or terminated-app
delivery, independent security audit, or universal connectivity claim.

Existing history/trust/identity are retained where safe; legacy peers require explicit
Tor route verification. Install upgrades with the same APK signer. Historical signed
0.0.1 artifacts remain immutable and use the superseded architecture.
This migration builds local preview artifacts and does not publish a release.
