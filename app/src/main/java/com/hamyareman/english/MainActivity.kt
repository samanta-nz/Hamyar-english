package com.hamyareman.english

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var prepareButton: Button
    private lateinit var startButton: Button
    private lateinit var testButton: Button

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    private var player: MediaPlayer? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Foreground service still starts; this only affects notification visibility. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        progress = findViewById(R.id.downloadProgress)
        progressText = findViewById(R.id.progressText)
        prepareButton = findViewById(R.id.prepareButton)
        startButton = findViewById(R.id.startButton)
        testButton = findViewById(R.id.testButton)

        requestNotificationsIfNeeded()
        refreshUi()

        prepareButton.setOnClickListener { prepareModel() }
        startButton.setOnClickListener { startLocalService() }
        testButton.setOnClickListener { testFullLocalPath() }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshUi() {
        val ready = ModelManager.isReady(this)
        if (ready) {
            statusText.text = "مدل آماده است. سرویس را شروع کنید."
            progress.progress = 100
            progressText.text = "مدل FP16 تأیید شده است."
        } else {
            statusText.text = "مدل داخلی هنوز آماده‌سازی نشده است."
            val have = ModelManager.preparedBytes(this)
            val pct = ((have * 100) / ModelManager.MODEL_SIZE).toInt().coerceIn(0, 100)
            progress.progress = pct
            progressText.text = if (have > 0) {
                "در حال آماده‌سازی: ${mb(have)} / ${mb(ModelManager.MODEL_SIZE)} MB"
            } else {
                "مدل داخل APK: ${mb(ModelManager.MODEL_SIZE)} MB"
            }
        }
        prepareButton.isEnabled = !ready
        startButton.isEnabled = ready
        testButton.isEnabled = false
    }

    private fun prepareModel() {
        prepareButton.isEnabled = false
        startButton.isEnabled = false
        statusText.text = "در حال استخراج و بررسی مدل داخلی..."

        lifecycleScope.launch {
            try {
                ModelManager.ensureModel(this@MainActivity) { downloaded, total ->
                    runOnUiThread {
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                        progress.progress = pct
                        progressText.text = "${mb(downloaded)} / ${mb(total)} MB   ($pct%)"
                    }
                }
                statusText.text = "مدل داخلی آماده شد و SHA-256 تأیید شد."
                progress.progress = 100
                startButton.isEnabled = true
                startLocalService()
            } catch (t: Throwable) {
                statusText.text = "خطای آماده‌سازی: ${t.message ?: t.javaClass.simpleName}"
                prepareButton.isEnabled = true
            }
        }
    }

    private fun startLocalService() {
        if (!ModelManager.isReady(this)) return
        ContextCompat.startForegroundService(this, Intent(this, TtsForegroundService::class.java))
        statusText.text = "در حال شروع سرویس محلی..."
        startButton.isEnabled = false

        lifecycleScope.launch {
            repeat(15) {
                delay(400)
                if (healthOk()) {
                    statusText.text = "سرویس آماده است: http://127.0.0.1:8766"
                    testButton.isEnabled = true
                    startButton.isEnabled = true
                    return@launch
                }
            }
            statusText.text = "سرویس پاسخ نداد؛ اعلان برنامه را برای خطا بررسی کنید."
            startButton.isEnabled = true
        }
    }

    private suspend fun healthOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://127.0.0.1:8766/health").get().build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun testFullLocalPath() {
        testButton.isEnabled = false
        statusText.text = "در حال ساخت تست از طریق API محلی..."
        lifecycleScope.launch {
            try {
                val wav = withContext(Dispatchers.IO) {
                    val json = JSONObject()
                        .put("input", "Are they at the office?")
                        .put("voice", "af_heart")
                        .put("speed", 1.0)
                        .put("response_format", "wav")
                    val request = Request.Builder()
                        .url("http://127.0.0.1:8766/v1/audio/speech")
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                        response.body?.bytes() ?: error("Empty audio response")
                    }
                }
                val file = File(cacheDir, "local-api-test.wav").apply { writeBytes(wav) }
                play(file)
                statusText.text = "تست موفق: صدا کاملاً از 127.0.0.1 ساخته شد."
            } catch (t: Throwable) {
                statusText.text = "تست ناموفق: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                testButton.isEnabled = true
            }
        }
    }

    private fun play(file: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
            prepareAsync()
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun mb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1024.0 / 1024.0)
}
