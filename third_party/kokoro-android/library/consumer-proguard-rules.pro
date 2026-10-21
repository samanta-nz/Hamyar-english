# JNI bridge — native methods resolved by name.
-keep class dev.ffmpegkit.kokoro.KokoroJNI { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Public API
-keep public class dev.ffmpegkit.kokoro.** { public *; }
# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
