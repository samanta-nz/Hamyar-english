package dev.ffmpegkit.kokoro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device neural text-to-speech with **Kokoro-82M** (StyleTTS 2 + ISTFTNet).
 *
 * Pipeline: text → espeak-ng (IPA phonemes) → tokens → Kokoro ONNX → 24 kHz audio.
 *
 * ```
 * KokoroTTS.initialize(context, "/path/kokoro.onnx")     // download the ONNX model first
 * val result = KokoroTTS.speak("Hello from Kokoro.")
 * // result.audioData is a WAV byte array
 * KokoroTTS.release()
 * ```
 *
 * Not thread-safe: serialize calls. Heavy calls are `suspend` (Dispatchers.Default).
 */
object KokoroTTS {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var voice: KokoroVoice = KokoroVoice.AF_HEART
    private var appContext: Context? = null

    // voice id → style embedding (flat float32, [nRows * STYLE_DIM]).
    private val voicepacks = HashMap<String, FloatArray>()

    private const val STYLE_DIM = 256   // Kokoro style vector size

    /**
     * Extract espeak data, start espeak-ng, and load the Kokoro ONNX model.
     *
     * @param modelPath path to the downloaded Kokoro `.onnx` (~300 MB — not bundled).
     * @param voice the initial voice (Free bundles [KokoroVoice.AF_HEART]).
     * @throws KokoroException on failure.
     */
    suspend fun initialize(
        context: Context,
        modelPath: String,
        voice: KokoroVoice = KokoroVoice.AF_HEART,
    ): Boolean = withContext(Dispatchers.Default) {
        val ctx = context.applicationContext
        appContext = ctx

        val dataPath = KokoroInitializer.ensureDataPath(ctx)
        if (KokoroJNI.nativeInitEspeak(dataPath) < 0) throw KokoroException.EspeakInitFailed()
        KokoroJNI.nativeSetVoice(voice.espeakLang)

        if (!File(modelPath).exists()) throw KokoroException.ModelLoadFailed(modelPath)
        val e = OrtEnvironment.getEnvironment()
        session = runCatching { e.createSession(modelPath, OrtSession.SessionOptions()) }
            .getOrElse { throw KokoroException.ModelLoadFailed(modelPath, it) }
        env = e

        this@KokoroTTS.voice = voice
        loadVoicepack(ctx, voice.id)
        true
    }

    /** Synthesize [text] to audio using the current voice. */
    suspend fun speak(text: String, config: KokoroConfig = KokoroConfig()): KokoroResult =
        withContext(Dispatchers.Default) {
            val sess = session ?: throw KokoroException.NotInitialized()
            val e = env ?: throw KokoroException.NotInitialized()

            val phonemes = KokoroJNI.nativePhonemize(text)
            val tokens = KokoroVocab.encode(phonemes)
            val style = styleFor(voice.id, tokens.size)
            val pcm = runModel(e, sess, tokens, style, config.speed)

            val audio = when (config.outputFormat) {
                AudioFormat.PCM -> floatToPcm16(pcm)
                else -> encodeWav(floatToPcm16(pcm), config.sampleRate)   // WAV (Free)
            }
            KokoroResult(
                audioData = audio,
                durationMs = (pcm.size * 1000L) / config.sampleRate,
                sampleRate = config.sampleRate,
                format = if (config.outputFormat == AudioFormat.PCM) AudioFormat.PCM else AudioFormat.WAV,
            )
        }

    /** Switch the active voice (must be bundled or previously [addVoice]-d). */
    fun setVoice(voice: KokoroVoice) {
        this.voice = voice
        KokoroJNI.nativeSetVoice(voice.espeakLang)
        appContext?.let { loadVoicepack(it, voice.id) }
    }

    /** Voices available in this build (Free: the bundled ones). */
    fun getAvailableVoices(): List<KokoroVoice> = KokoroVoice.BUNDLED

    /** Register a voicepack (`<id>.bin`, raw float32) from disk. @return success. */
    fun addVoice(voicePackPath: String): Boolean = runCatching {
        val id = File(voicePackPath).nameWithoutExtension
        voicepacks[id] = readFloats(File(voicePackPath).readBytes())
        true
    }.getOrDefault(false)

    /** Stop espeak-ng and free the ONNX session. Safe to call more than once. */
    fun release() {
        runCatching { session?.close() }; session = null
        env = null
        voicepacks.clear()
        KokoroJNI.nativeRelease()
    }

    // --- internals ---------------------------------------------------------

    private fun runModel(
        e: OrtEnvironment, sess: OrtSession,
        tokens: LongArray, style: FloatArray, speed: Float,
    ): FloatArray {
        // NOTE: input names/shapes follow the Kokoro-82M ONNX export
        // (input_ids / style / speed). Verify against your model's metadata.
        val ids = OnnxTensor.createTensor(e, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong()))
        val sty = OnnxTensor.createTensor(e, FloatBuffer.wrap(style), longArrayOf(1, STYLE_DIM.toLong()))
        val spd = OnnxTensor.createTensor(e, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1))
        val inputs = mapOf("input_ids" to ids, "style" to sty, "speed" to spd)
        try {
            sess.run(inputs).use { out ->
                val t = out[0].value
                return when (t) {
                    is FloatArray -> t
                    is Array<*> -> (t.firstOrNull() as? FloatArray) ?: FloatArray(0)
                    else -> throw KokoroException.SynthesisFailed("unexpected ONNX output type")
                }
            }
        } finally { ids.close(); sty.close(); spd.close() }
    }

    /** Style row for a given token count (voicepack is [rows, STYLE_DIM]). */
    private fun styleFor(id: String, nTokens: Int): FloatArray {
        val pack = voicepacks[id] ?: throw KokoroException.VoiceNotFound(id)
        val rows = pack.size / STYLE_DIM
        val row = if (rows <= 1) 0 else (nTokens - 1).coerceIn(0, rows - 1)
        return pack.copyOfRange(row * STYLE_DIM, row * STYLE_DIM + STYLE_DIM)
    }

    private fun loadVoicepack(context: Context, id: String) {
        if (voicepacks.containsKey(id)) return
        // Bundled voices live in assets/voices/<id>.bin.
        runCatching {
            context.assets.open("voices/$id.bin").use { voicepacks[id] = readFloats(it.readBytes()) }
        }.onFailure { throw KokoroException.VoiceNotFound(id) }
    }

    private fun readFloats(bytes: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        bb.asFloatBuffer().get(out)
        return out
    }

    private fun floatToPcm16(pcm: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in pcm) bb.putShort((f.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        return bb.array()
    }

    private fun encodeWav(pcm16: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun i16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        val byteRate = sampleRate * 2
        out.write("RIFF".toByteArray()); i32(36 + pcm16.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1)       // PCM, mono
        i32(sampleRate); i32(byteRate); i16(2); i16(16)                // block align, bits
        out.write("data".toByteArray()); i32(pcm16.size); out.write(pcm16)
        return out.toByteArray()
    }
}
