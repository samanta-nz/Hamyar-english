package dev.ffmpegkit.kokoro

/**
 * Result of a [KokoroTTS.speak] call.
 *
 * @property audioData encoded audio bytes in [format] (WAV by default).
 * @property durationMs audio duration in milliseconds.
 * @property sampleRate sample rate of the audio (Hz).
 * @property format container of [audioData].
 */
data class KokoroResult(
    val audioData: ByteArray,
    val durationMs: Long,
    val sampleRate: Int,
    val format: AudioFormat,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is KokoroResult &&
            audioData.contentEquals(other.audioData) && durationMs == other.durationMs &&
            sampleRate == other.sampleRate && format == other.format)

    override fun hashCode(): Int =
        (((audioData.contentHashCode() * 31 + durationMs.hashCode()) * 31) + sampleRate) * 31 + format.hashCode()
}
