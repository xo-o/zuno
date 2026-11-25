# Preserve source file & line numbers so Play Console crash/ANR traces deobfuscate cleanly
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ProGuard rules for LiveKit Android and WebRTC
-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }
-keepattributes InnerClasses, Signature, *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
