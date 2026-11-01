# WebRTC Dependency Feasibility Record

## Selection and provenance

Probe-only coordinate: `io.github.webrtc-sdk:android:150.7871.04`, from Maven Central.
The app foundation has no runtime dependencies. The probe runtime graph contains
only this AAR, with no transitive runtime dependencies. Test dependencies are
AndroidX runner 1.6.2/JUnit 4.13.2 and their test-only graph.

The [publisher README](https://github.com/webrtc-sdk/android) still suggests .01;
the [publisher changelog](https://github.com/webrtc-sdk/android/blob/main/CHANGES.md)
and [publishing configuration](https://github.com/webrtc-sdk/android/blob/main/gradle.properties)
identify .04 and a certificate-related security correction. The resolved
[Maven publication](https://central.sonatype.com/artifact/io.github.webrtc-sdk/android/150.7871.04)
was inspected locally. Java API compatibility is compilation-tested: this version
has no DataChannel.protocol() accessor, so the probe validates its channel label
and fixed binary frame patterns instead. It does not claim authentication.

Downloaded AAR: **49,068,339 bytes**.
SHA-256: `1872dda4e3bf7d6ea6f7c6e11861332081c57081a1e62fc82fab72415c69c84c`.
Gradle verification metadata records this and build/test artifact checksums. This
is a recorded download identity, not proof of source/binary reproducibility or an
independent security audit. Review checksum changes deliberately.

## Native size and page alignment

| ABI | Native .so uncompressed bytes | ELF PT_LOAD alignment |
| --- | ---: | --- |
| arm64-v8a | 12,266,048 | 16,384 on all load segments |
| armeabi-v7a | 6,791,820 | 4,096 on all load segments |
| x86_64 | 16,144,848 | 16,384 on all load segments |
| x86 | 12,798,344 | 4,096 on all load segments |

Measured by inspecting ELF program headers in the downloaded AAR. Android
build-tools 35 `zipalign -c -P 16 4` passes for the arm64 and x86_64 unsigned release
APKs. These structural checks support 64-bit 16 KB alignment; runtime compatibility
on a 16 KB Android device has not been tested. No NDK is installed; AGP reports
that it packages the supplied native binary without additional symbol stripping.

## Licensing and remaining adoption gate

The POM declares BSD-3-Clause; this does not cover every bundled third-party
component. The AAR has no embedded notice bundle. The publisher provides a generic
[WebRTC license collection](https://github.com/webrtc-sdk/android/blob/main/Licenses/WEBRTC.md).
Exact version-matched native notices and source-to-binary correspondence remain
unverified. An [existing publisher issue](https://github.com/webrtc-sdk/android/issues/59)
requests .04 NOTICE/version evidence and mentions a declared source revision;
that report is not independently verified evidence of the Maven binary's source.
No issue or other message was posted by this project.

Do not publish/distribute a probe release as a finished messenger. Resolve the
version-specific notices and provenance, run native tests and real-device network
checks before accepting M02/adopting this dependency for the product. The observed
size may motivate an upstream data-only build, but no smaller or custom build has
been evaluated. No claim of vulnerability-free code is made.

## Follow-up investigation — 2026-11-01

The pinned [build VERSION declaration](https://github.com/webrtc-sdk/webrtc-build/blob/m150.7871.04/build/VERSION)
identifies source commit `a545de45259b47dfac5f3635863e47cb601f1bb2`. This is now
verified against the publisher's tag rather than inferred from a third-party issue.

The public release `webrtc.tar.gz` was downloaded and its AAR, NOTICE and VERSIONS
inspected. NOTICE SHA-256 is `f851751c645ded1b53ac12ff643557c5a10f8c8db45637c0ba525e62739d68f3`.
The bundled AAR hash is `d07dddeb5c56e7d7616dcbc80da6bd49a2559aee8ee6bc5d836f92a6465ebd54`,
which differs from the standard Maven AAR. Comparing decompressed members confirms
that classes.jar and all four native libraries differ, so this is not merely ZIP
recompression. It also does not match the stripped Maven publication's AAR checksum.
Do not describe this NOTICE as a verified byte-matched Maven notice bundle.
[Recorded comparison](WEBRTC_BUNDLE_COMPARISON.json) contains only artifact hashes,
not sensitive runtime data. No dependency was silently replaced.

Native loading and repeated roundtrip/disposal tests now pass on an API 35 arm64
AOSP emulator ([results](NATIVE_TEST_RESULTS.json)). Physical-device internet and
16 KB runtime tests remain unperformed.

## Final adoption decision

The app retains `io.github.webrtc-sdk:android:150.7871.04` from Maven Central with
SHA-256 verification. The alternative public-bundle AAR was actually compiled/tested: it
requires Java 21, its default library name did not match its packaged filename, and
explicit filename selection still resulted in a native process crash. It was rejected,
not silently substituted. Production remains on JDK 17 and the native-tested Maven AAR.

The publisher's comprehensive `Licenses/WEBRTC.md` collection is retained verbatim in
third_party/webrtc/NOTICE and embedded in the app alongside app GPL/source attribution.
The signed preview includes this provenance uncertainty rather than claiming an exact
reproducible source/binary match. An independent native/security review remains open;
physical acceptance was explicitly deferred by the user.

Signed-release validation additionally exposed JNI Zero class stripping. Explicit
keeps for org.webrtc and org.jni_zero fixed native initialization. The final signed
release passes two-process pairing, explicit pin verification, acknowledged text
and force-stop/restart identity/history checks. This adds release-mode evidence;
it does not replace the deferred physical-network matrix or independent audit.
