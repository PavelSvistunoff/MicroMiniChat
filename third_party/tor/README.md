# Tor Android source adapter

`org/torproject/jni/TorService.java` is derived from the actual Maven Central
`info.guardianproject:tor-android:0.4.9.14` sources JAR (SHA-256
095118a41782a91b9002038461c4c6a65f5c41bc6f5a37323362eceaf0088a2b).
The upstream license is retained alongside this file.

The published AAR requires SDK 37.1 and Java 24 class files. To preserve this
project's SDK 35/Java 17 toolchain, Gradle extracts only its four native libraries
and compiles the Java bridge locally. It never imports the Java 24 classes or
silently ignores Android metadata. The entire AAR is checksum-verified before
extraction. Exception logging and broadcast exception details are suppressed in
the adapted source. Lock acquisition on the main thread is nonblocking.

This is not a claim of independently reproducible native binaries. Upstream source
and native dependency update/security review remain required. No executable is
downloaded by the installed application. Do not upgrade just the version string;
review the JNI/source pair and update the explicit artifact hash together.
