package com.hamyareman.english

import android.content.Context
import dev.ffmpegkit.kokoro.AudioFormat
import dev.ffmpegkit.kokoro.KokoroConfig
import dev.ffmpegkit.kokoro.KokoroTTS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

class TtsEngine(private val context: Context) {
    private val mutex = Mutex()
    private var initialized = false

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        require(ModelManager.isReady(context)) { "Model is not ready" }
        KokoroTTS.initialize(context, ModelManager.modelFile(context).absolutePath)
        initialized = true
    }

    suspend fun synthesize(text: String, speed: Float): ByteArray = mutex.withLock {
        check(initialized) { "Engine is not initialized" }
        require(text.isNotBlank()) { "Text is empty" }
        require(speed in 0.5f..2.0f) { "Speed must be between 0.5 and 2.0" }

        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val cache = cacheFile(normalized, speed)
        if (cache.isFile && cache.length() > 44) return@withLock cache.readBytes()

        val chunks = splitForKokoro(normalized)
        val wavs = chunks.map { chunk ->
            KokoroTTS.speak(
                chunk,
                KokoroConfig(speed = speed, sampleRate = 24_000, outputFormat = AudioFormat.WAV)
            ).audioData
        }
        val merged = mergeWav(wavs)

        val tmp = File(cache.parentFile, cache.name + ".tmp")
        tmp.writeBytes(merged)
        if (cache.exists()) cache.delete()
        if (!tmp.renameTo(cache)) {
            tmp.copyTo(cache, overwrite = true)
            tmp.delete()
        }
        merged
    }

    fun release() {
        if (initialized) runCatching { KokoroTTS.release() }
        initialized = false
    }

    private fun cacheFile(text: String, speed: Float): File {
        val dir = File(context.filesDir, "audio_cache").apply { mkdirs() }
        val key = "af_heart|${"%.3f".format(java.util.Locale.US, speed)}|$text"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.wav")
    }

    private fun splitForKokoro(text: String, maxChars: Int = 320): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val out = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            val s = current.toString().trim()
            if (s.isNotEmpty()) out += s
            current = StringBuilder()
        }

        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue
            if (s.length > maxChars) {
                flush()
                var rest = s
                while (rest.length > maxChars) {
                    var cut = rest.lastIndexOf(' ', maxChars)
                    if (cut < maxChars / 2) cut = maxChars
                    out += rest.substring(0, cut).trim()
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotEmpty()) current.append(rest)
            } else if (current.isEmpty()) {
                current.append(s)
            } else if (current.length + 1 + s.length <= maxChars) {
                current.append(' ').append(s)
            } else {
                flush()
                current.append(s)
            }
        }
        flush()
        return out.ifEmpty { listOf(text.take(maxChars)) }
    }

    private fun mergeWav(wavs: List<ByteArray>): ByteArray {
        require(wavs.isNotEmpty())
        if (wavs.size == 1) return wavs.first()
        val pcm = ByteArrayOutputStream()
        for (wav in wavs) {
            require(wav.size >= 44) { "Invalid WAV chunk" }
            pcm.write(wav, 44, wav.size - 44)
        }
        return wavFromPcm16(pcm.toByteArray(), 24_000)
    }

    private fun wavFromPcm16(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun i32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun i16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray()); i32(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1)
        i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
