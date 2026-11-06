# Repository Guidelines

## Scope and Workflow

The user explicitly approved the complete Tor migration and terminal UI redesign.
The product requirements and architecture are documented in SPEC.md and ARCHITECTURE.md.
Implement on the dedicated feature branch, commit each validated milestone, and do
not publish APKs/create releases as part of this migration without authorization.
Tor is the only product runtime. No alternate Internet transport, application
backend or manual network setup is permitted.
Preserve local history and require explicit peer re-verification where necessary.

Physical-device acceptance was explicitly deferred; never report unexecuted
hardware/network checks as passing. Keep TASKS.md and docs/VALIDATION.md accurate.
Inspect, implement, test, fix, review security/complexity and document each milestone.

## Project Structure

`app/src/main/` contains Java/native Views, Tor transport, identity, protocol,
SQLite storage, file streaming and lifecycle policy. `app/src/test/` holds JVM tests;
`app/src/androidTest/` holds emulator/device integration tests. The protected fixture
provider in `app/src/debug/` must never appear in a release. `tools/` contains checks/release tooling; `docs/`
contains evidence. `third_party/` retains dependency notices.

## Build and Test

Use JDK 17, SDK 35 and build-tools 35.0.0; set `ANDROID_HOME` or untracked
`local.properties`. Use the checksum-pinned Gradle wrapper.

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: host build/checks.
- `./gradlew :app:connectedDebugAndroidTest`: emulator/device integration.
- `python3 -m unittest discover -s tools -p 'test_*.py'`: host contracts/measurements.
- `python3 tools/build_release.py`: signed release with a local signer; see releasing docs.

Test boundaries, persistence/ACK failures, identity changes, file corruption/cancel and
lifecycle races. No coverage percentage or standalone formatter is configured.
Android lint is the static check. Record measurements, not guesses.

## Style and Security

Use four-space Java indentation, UpperCamelCase classes, lowerCamelCase methods and
clear resource names. Native Views/platform APIs are preferred. Keep dependencies
minimal/pinned; review verification metadata changes. Never log messages, invitations, network
addresses, keys or file contents. Use established cryptography; do not add a new
handshake. No external application backend/cloud service without explicit approval.
Read SPEC, ARCHITECTURE, SECURITY and PROTOCOL before changing these boundaries.

## Commits and Releases

Use concise imperative commit subjects. PRs explain behavior, validation and material
limits; link issues and UI evidence when relevant. Never commit SDKs, generated output,
IDE settings, signing keys/passwords or sensitive logs. The user explicitly authorized
tracking signed `release/0.0.1/*.apk` and `SHA256SUMS`; this is the sole generated-output
exception. Preserve GPL/third-party notices.
Physical-network gaps require a clearly labeled preview; no universal connectivity,
permanent background execution or terminated-app delivery promises.
