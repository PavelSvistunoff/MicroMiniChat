# Bundled Tor executable

Gradle verifies the entire info.guardianproject:tor-android:0.4.9.14 AAR hash before
extracting its four native libtor.so executables. Android extracts the libraries
at install time. TorRuntime launches the executable in a child process; it does
not load JNI or import the published Java bytecode. See docs/TOR_RUNTIME.md.

The original Tor/third-party license is TOR_LICENSE.txt. Bouncy Castle 1.86 is
used for standard SHA3 onion checksums; its license is retained here as well.
No independent reproducible-native-build or security-audit claim is made.
