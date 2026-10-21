package com.hamyareman.english

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ModelManager {
    const val ASSET_PATH = "models/kokoro-v1.0.fp16.onnx"
    const val MODEL_SIZE = 163_527_961L
    const val MODEL_SHA256 = "f3a290d384fbb27966d462905c71a46cef9e5fd00516b40df32a0b4afe77ac96"

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, "kokoro-v1.0.fp16.onnx")

    private fun partFile(context: Context): File = File(modelFile(context).parentFile, modelFile(context).name + ".part")
    private fun markerFile(context: Context): File = File(modelFile(context).parentFile, ".verified")

    fun isReady(context: Context): Boolean {
        val f = modelFile(context)
        val marker = markerFile(context)
        return f.isFile && f.length() == MODEL_SIZE && marker.isFile && marker.readText().trim() == MODEL_SHA256
    }

    fun preparedBytes(context: Context): Long = when {
        isReady(context) -> MODEL_SIZE
        partFile(context).isFile -> partFile(context).length().coerceAtMost(MODEL_SIZE)
        else -> 0L
    }

    suspend fun ensureModel(
        context: Context,
        onProgress: (copied: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (isReady(context)) {
            onProgress(MODEL_SIZE, MODEL_SIZE)
            return@withContext modelFile(context)
        }

        val target = modelFile(context)
        val part = partFile(context)
        markerFile(context).delete()
        part.delete()
        onProgress(0L, MODEL_SIZE)

        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(part, false).use { out ->
                val buffer = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    copied += n
                    onProgress(copied.coerceAtMost(MODEL_SIZE), MODEL_SIZE)
                }
                out.fd.sync()
            }
        }

        if (part.length() != MODEL_SIZE) {
            part.delete()
            error("Bundled model size mismatch: ${part.length()} / $MODEL_SIZE bytes")
        }

        val sha = sha256(part)
        if (!sha.equals(MODEL_SHA256, ignoreCase = true)) {
            part.delete()
            error("Bundled model SHA-256 mismatch")
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        markerFile(context).writeText(MODEL_SHA256)
        onProgress(MODEL_SIZE, MODEL_SIZE)
        target
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
