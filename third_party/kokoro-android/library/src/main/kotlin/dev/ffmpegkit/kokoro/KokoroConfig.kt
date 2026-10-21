package dev.ffmpegkit.kokoro

/** Output audio format. Free exports WAV; Pro adds MP3/OGG/PCM. */
enum class AudioFormat { WAV, PCM, MP3, OGG }

/**
 * Synthesis configuration.
 *
 * @property speed speaking rate multiplier (1.0 = normal, <1 slower, >1 faster).
 * @property sampleRate output sample rate; Kokoro is natively 24 kHz.
 * @property outputFormat container for [KokoroResult.audioData] (Free: WAV/PCM).
 */
data class KokoroConfig(
    val speed: Float = 1.0f,
    val sampleRate: Int = 24000,
    val outputFormat: AudioFormat = AudioFormat.WAV,
)
