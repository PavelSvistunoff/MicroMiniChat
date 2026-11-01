# JNI resolves org.webrtc classes/callbacks by name; the published AAR has no consumer rules.
# Preserve this package only; application code is still shrunk and obfuscated.
-keep class org.webrtc.** { *; }
# Modern WebRTC registers JNI Zero support classes dynamically during JNI_OnLoad.
-keep class org.jni_zero.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# The publisher omits this optional dynamic-classloader bridge. Our normal library
# initialization never invokes JniZero.setJniClassLoader; do not suppress other warnings.
-dontwarn org.jni_zero.JniZeroJni
