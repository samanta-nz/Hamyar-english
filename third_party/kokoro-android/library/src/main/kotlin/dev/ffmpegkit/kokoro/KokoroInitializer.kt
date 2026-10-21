package dev.ffmpegkit.kokoro

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Extracts the bundled `espeak-ng-data/` (phoneme dictionaries, ~15 MB) from
 * `assets/` into app-private storage so espeak-ng can read it from a real filesystem
 * path. [ensureDataPath] returns the directory that *contains* `espeak-ng-data/` —
 * that parent path is what `espeak_Initialize` expects (mirrors the Tesseract
 * datapath gotcha).
 */
internal object KokoroInitializer {

    private const val ASSET_DIR = "espeak-ng-data"

    /**
     * Extract `assets/espeak-ng-data/` recursively (if not already present) and return
     * the directory containing `espeak-ng-data/`.
     */
    fun ensureDataPath(context: Context): String {
        val baseDir = File(context.filesDir, "kokoro")   // parent of espeak-ng-data
        val dataDir = File(baseDir, ASSET_DIR)
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw KokoroException.EspeakInitFailed()
        }
        try {
            extractRecursively(context, ASSET_DIR, baseDir)
        } catch (e: IOException) {
            throw KokoroException.EspeakInitFailed()
        }
        // espeak_Initialize(path) wants the PARENT of espeak-ng-data/, not the dir itself.
        return baseDir.absolutePath
    }

    /**
     * Recursively copy an assets subtree into [baseDir]/[assetPath], writing each file
     * atomically (temp + rename) so an interrupted copy never leaves a truncated file
     * that would be skipped on restart.
     */
    private fun extractRecursively(context: Context, assetPath: String, baseDir: File) {
        val assets = context.assets
        val entries = assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            // Leaf = a file.
            val target = File(baseDir, assetPath)
            if (target.exists() && target.length() > 0) return
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            assets.open(assetPath).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) { tmp.delete(); throw IOException("rename failed: $target") }
            return
        }
        for (name in entries) extractRecursively(context, "$assetPath/$name", baseDir)
    }
}
