package com.hamyareman.english

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TtsForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: TtsEngine? = null
    private var server: LocalTtsServer? = null
    private var startJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("در حال راه‌اندازی موتور محلی..."))
        if (startJob?.isActive == true || server?.isAlive == true) return START_STICKY

        startJob = scope.launch {
            try {
                if (!ModelManager.isReady(this@TtsForegroundService)) {
                    notify("مدل آماده نیست؛ ابتدا داخل برنامه موتور را آماده کنید.")
                    stopSelf()
                    return@launch
                }
                val tts = TtsEngine(this@TtsForegroundService)
                tts.initialize()
                val http = LocalTtsServer(tts)
                http.start(SOCKET_TIMEOUT_MS, false)
                engine = tts
                server = http
                notify("آماده: 127.0.0.1:8766")
            } catch (t: Throwable) {
                notify("خطا: ${t.message ?: t.javaClass.simpleName}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        runCatching { engine?.release() }
        engine = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "همیار انگلیسی", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("همیار انگلیسی")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        private const val CHANNEL_ID = "hamyar_english_tts"
        private const val NOTIFICATION_ID = 8766
        private const val SOCKET_TIMEOUT_MS = 5_000
    }
}
