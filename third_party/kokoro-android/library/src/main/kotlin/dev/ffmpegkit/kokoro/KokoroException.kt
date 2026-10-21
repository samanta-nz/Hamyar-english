package dev.ffmpegkit.kokoro

/** Base type for all Kokoro errors. */
sealed class KokoroException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Engine used before a successful [KokoroTTS.initialize]. */
    class NotInitialized :
        KokoroException("KokoroTTS is not initialized. Call initialize() first.")

    /** The ONNX model file could not be loaded (missing / corrupt / out of memory). */
    class ModelLoadFailed(path: String, cause: Throwable? = null) :
        KokoroException("Failed to load Kokoro ONNX model '$path'. Check the path and that it's a valid model.", cause)

    /** espeak-ng failed to initialize (usually a wrong data path). */
    class EspeakInitFailed :
        KokoroException("espeak-ng failed to initialize (its data files could not be found).")

    /** The requested voicepack could not be loaded. */
    class VoiceNotFound(id: String) :
        KokoroException("Voice '$id' is not available. Bundle it or call addVoice() first.")

    /** Synthesis failed at the ONNX / audio layer. */
    class SynthesisFailed(message: String, cause: Throwable? = null) :
        KokoroException(message, cause)
}
