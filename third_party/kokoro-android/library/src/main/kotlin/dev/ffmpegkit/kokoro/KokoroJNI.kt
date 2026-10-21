package dev.ffmpegkit.kokoro

/**
 * Internal bridge to the native espeak-ng library. Not part of the public API — use
 * [KokoroTTS]. Loads espeak-ng, then kokoro_jni. The native side does text→IPA
 * phonemes only; the Kokoro ONNX model runs in Kotlin (onnxruntime-android).
 */
internal object KokoroJNI {

    init {
        runCatching { System.loadLibrary("espeak-ng") }
        System.loadLibrary("kokoro_jni")
    }

    /** @param dataPath directory containing `espeak-ng-data/`. @return sample rate, or -1. */
    external fun nativeInitEspeak(dataPath: String): Int

    /** @param name espeak language, e.g. "en-us". */
    external fun nativeSetVoice(name: String): Boolean

    /** @return continuous IPA phoneme string for [text]. */
    external fun nativePhonemize(text: String): String

    external fun nativeRelease()
}
