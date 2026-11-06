# Experimental migration artifacts

These are debug development artifacts from the blocked M02/M03 candidate. They
still select the historical WebRTC ChatSession. They are **not Tor-native
messenger builds** and do not satisfy final acceptance. Nothing was published.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| app-arm64-v8a-debug.apk | 23108791 | `d41c4af76b679e9b8f1e9f8228cbee321fdbdeef3a010c690530eb595228a9ba` |
| app-armeabi-v7a-debug.apk | 16875967 | `d744a362baf407572c12a81df46d48b90c73b033dc7a64841385b79789aeb4ef` |
| app-universal-debug.apk | 81400742 | `8bbec837e9650f353539304de2a675033c1cdd8049701671ba12d5de4ba4efbe` |
| app-x86-debug.apk | 23299027 | `d4beb71ac696a5ea3a6cf8204cd75521ce347df1487649fc7884cd6556c41194` |
| app-x86_64-debug.apk | 27857361 | `848f2a992ca414874fd550d2f8b50082e5985887a8dd913f8aecf23f2595466e` |

Files are local under `app/build/outputs/apk/debug/`; generated APKs are not
committed. The instrumentation APK is under
`app/build/outputs/apk/androidTest/debug/`.

Final candidate host validation: 25 JVM tests PASS; six Python host checks PASS;
Android lint PASS; debug APK and instrumentation APK build PASS. Real onion
publication/restart integration FAILS with a native process abort. See
TOR_BLOCKER.md. Tor bootstrap duration, authenticated connection time, PSS,
battery and file throughput are NOT VERIFIED; these APK sizes are not optimized
Tor product measurements. Four native ABIs are packaged; only arm64 was run.

Build using JDK 17, SDK 35/build-tools 35.0.0 and the checksum-pinned wrapper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
```

Existing release signing instructions remain in RELEASING.md. Do not publish or
represent this candidate as a Tor release. Native source-update qualification is
documented in TOR_RUNTIME.md; pending product work is in TOR_MIGRATION_PLAN.md.
