# Bundled Tor executable

Gradle verifies the entire info.guardianproject:tor-android:0.4.9.14 AAR hash before
extracting its four native libtor.so executables. Android extracts the libraries
at install time. TorRuntime launches the executable in a child process; it does
not load JNI or import the published Java bytecode. See docs/TOR_RUNTIME.md.

The original Tor/third-party license is TOR_LICENSE.txt. Bouncy Castle 1.86 is
used for standard SHA3 onion checksums; its license is retained here as well.
No independent reproducible-native-build or security-audit claim is made.

Conscrypt Android2.8.0 uses BoringSSL revision
427ec40cc8edc545253289232e885708c409e5ea, read from the pinned AAR's
org/conscrypt/conscrypt.properties. BORINGSSL_LICENSE.txt is from that exact revision.
Conscrypt NOTICE and the referenced Netty/Harmony Apache notices are retained from
https://github.com/google/conscrypt/tree/2.8.0 . JTORCTL_LICENSE.txt is from
https://github.com/guardianproject/jtorctl/tree/0.4.5.7 . These complement the Tor
bundle's upstream notices and are included in APK assets/the license screen.
