# Building a Tor preview

Use JDK17, Android SDK35/build-tools35.0.0 and the pinned wrapper. The configured
version is 0.1.0-tor-preview/code4. Historical signed release/0.0.1 artifacts and
tags are immutable and are not the Tor product. Do not overwrite them.

```sh
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
```

Four ABI APKs and universal APK are under app/build/outputs/apk/debug or release.
Ordinary release builds are minified/resource-shrunk and unsigned without a signer.
Install debug APKs only on test devices; a debug signer cannot update a differently
signed installed app. Never uninstall a user's existing app to bypass this restriction.

For signed local builds, retain the established APK signer for upgrades. Configure
MMC_RELEASE_STORE and MMC_RELEASE_PASSWORD privately in the environment, or use the
ignored .release-signing/release.jks and password.txt with tools/build_release.py.
The helper supplies secrets through a subprocess environment, not command arguments.
tools/create_release_key.py creates a local signer once and refuses replacement;
a different key is appropriate only for a new test installation. Never commit
keys/passwords or paste them into chat.

```sh
python3 tools/build_release.py
python3 tools/check_tor_apks.py
```

Before distribution run README checks and real-Tor tests. Verify every signature
using SDK apksigner verify --verbose --print-certs. The artifact audit checks
ZIP alignment, manifest/version/permissions, absence of the release debug provider,
exact native inventory, approved executable content and bundled notices. Supplying
--expected-release-signer with the public certificate SHA256 also verifies every
release signature and its continuity. It records sizes/hashes
from the current Gradle output metadata rather than stale APKs. --build-tools can
select a nonstandard SDK tool directory. Retain SHA256SUMS and the source commit.
Review Tor advisories, notices and physical gaps. Signing is not security acceptance.

## Native UI validation on two emulators

```sh
python3 tools/tor_ui_smoke.py --device emulator-5554 --peer-device emulator-5556 --replace-test-install --variant release
```

This replaces installations only on two distinct disposable emulators, never a
user device/profile. Both ordinary signed apps remain foreground in separate
Android instances. The tool reads the Android Sharesheet invitation in memory,
checks its public fingerprint and enters it through non-PTY stdin without placing
the capability in host command arguments/output. It verifies exact input, explicit
Connect, bilateral full fingerprints, durable text ACK, force-stop history and
verified automatic reconnect. It also opens bundled licenses. It stops both apps
in cleanup. MMC_ADB can select a nonstandard adb path.

A single emulator cycling cached activities did not qualify this path; finite
background grace and UI automation cannot substitute for two open phones. Two
emulators still do not establish physical Wi-Fi/mobile/CGNAT/OEM battery acceptance.
Use the ordinary app package on both emulators. Only the current build/audit
commands above are supported.

## Publication

Publication requires explicit user authorization. The user authorized preparing
and publishing 0.1.0-tor-preview on 2026-11-06. Future releases require their own
authorization. Keep new generated APKs in ignored release/build output; only the
historical tracked artifact directory has a generated-file exception.

1. Build/check using the commands above; verify the established signer and record
   exact hashes. Re-run signed UI qualification if the APK bytes have changed.
2. Commit release notes and evidence without keys, generated APKs or unrelated
   staged workspace changes. Create an annotated version tag at that exact commit.
3. Stage the five ABI/universal APKs with versioned names, SOURCE.json (commit,
   certificate fingerprint and APK hashes) and SHA256SUMS in an ignored directory.
4. Push the reviewed source commit and tag without force. Create a draft GitHub
   prerelease using the explicit tag and notes file; do not overwrite existing tags
   or published assets. Upload all seven files.
5. Download the draft assets and verify each byte count/hash plus all checksums
   against local qualified files. Only then publish with prerelease status retained.

The resulting release is a preview with the physical checklist still unverified.
GitHub release notes and attached SOURCE.json identify the published source/assets.
