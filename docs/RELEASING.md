# Building and Publishing 0.0.1

## Signed builds

Use JDK 17/SDK 35 and the Gradle wrapper. The local signer created for this release
is stored in `.release-signing/release.jks`; its password is in the adjacent private
`password.txt`. Both are ignored, directory permissions are 0700 and files 0600.
Back these up securely: future APK updates must use the same signing identity.
Do not put secrets in Git, shell arguments, release notes or chat.

```sh
python3 tools/create_release_key.py  # creates once; refuses to replace an existing key
python3 tools/build_release.py
```

The build script supplies MMC_RELEASE_STORE/MMC_RELEASE_PASSWORD privately through
the subprocess environment. Without signing configuration, ordinary Gradle release
builds are unsigned. Outputs are under `app/build/outputs/apk/release/`.
The app signer is separate from each user's DTLS peer identity.

Public APK signer SHA-256 certificate fingerprint:
`2e6381e1b46492b37b3f13c8ba337fac36ae49a119cfc8f9d69a928f6168df9b`.

## Validation and packaging

Run the README checks. Verify APKs with Android build-tools `apksigner verify`; check
64-bit ZIP/native alignment. Confirm release manifest version 0.0.1/code 1, only
INTERNET/ACCESS_NETWORK_STATE permissions, backup exclusions, and absence of the debug
DocumentsProvider. Keep JNI callbacks under org.webrtc and org.jni_zero during shrinking.

Collect the four per-ABI APKs and universal APK under ignored `release/0.0.1/`, name
them `microminichat-0.0.1-ABI.apk`, and compute SHA256SUMS. Resource/size evidence belongs
in `docs/`; raw SDP, private keys and user-content logs do not.

For release-native UI testing on a disposable emulator, `-PqaPeer` builds a separate
QA package in `build/qa-peer/`; it is not a release download. The smoke harness requires
`--replace-test-install` and an emulator serial. It temporarily disables cached-app
freezing to model two foreground phones, restores that setting, and removes its QA peer.
This is not proof of physical-network or background-delivery behavior.

## Main commit and GitHub publication

Review changes, commit to main, create annotated tag `v0.0.1`, then push main and tag.
Existing GitHub credentials are required; never paste access tokens into chat.
`python3 tools/github_release.py --check` only verifies access. After a clean reviewed
main/tag are pushed, `--publish` creates a draft preview, uploads five APKs/checksums,
verifies reported asset digests and publishes it as a prerelease. It refuses to replace
an existing published release or mismatching asset. Physical acceptance remains deferred;
do not label this preview as independently audited or universally reachable.

APK VCS metadata is disabled deliberately: artifact hashes are reviewed with the
source commit/tag, without embedding the previous precommit SHA or creating a
self-reference between committed metrics and the signed APK. The release tag and
SHA256SUMS establish the published source/artifact association.
