# ProGuard rules for LiveKit Android and WebRTC
-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }
-keepattributes InnerClasses, Signature, *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
