package com.lss.fishreader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.lss.fishreader.FishPlayer.Phase

/** Keeps reading while you use other apps, and shows the player notification. */
class ReadService : Service() {
    companion object {
        const val ACTION_READ = "com.lss.fishreader.READ"
        const val ACTION_TOGGLE = "com.lss.fishreader.TOGGLE"
        const val ACTION_STOP = "com.lss.fishreader.STOP"
        const val EXTRA_TEXT = "text"
        const val EXTRA_VOICE = "voice"
        private const val CHANNEL = "reading"
        private const val NOTIF_ID = 7

        fun read(ctx: Context, text: String, voice: String? = null) {
            val i = Intent(ctx, ReadService::class.java).setAction(ACTION_READ).putExtra(EXTRA_TEXT, text)
            if (voice != null) i.putExtra(EXTRA_VOICE, voice)
            ctx.startForegroundService(i)
        }

        fun fmt(seconds: Double): String {
            val t = seconds.toInt().coerceAtLeast(0)
            return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
        }
    }

    private val player get() = FishPlayer.shared
    private val handler = Handler(Looper.getMainLooper())
    private var lastStartId = 0
    private var lastErrorShown = 0L
    private val tick = object : Runnable {
        override fun run() = update()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Reading aloud", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        goForeground(buildNotification(player.snapshot()))
        when (intent?.action) {
            ACTION_READ -> {
                val prefs = Prefs(this)
                val text = TextCleaner.clean(intent.getStringExtra(EXTRA_TEXT) ?: "")
                when {
                    prefs.apiKey.isBlank() -> player.showError("Add your Fish Audio API key in the Fish Reader app first.")
                    text.isEmpty() -> player.showError("There's no text to read.")
                    else -> player.speak(
                        text, prefs.apiKey, intent.getStringExtra(EXTRA_VOICE) ?: prefs.mainVoice,
                        prefs.model, prefs.quality, prefs.speed,
                    )
                }
            }
            ACTION_TOGGLE -> player.togglePause()
            ACTION_STOP -> player.stop()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun update() {
        val s = player.snapshot()
        val now = System.currentTimeMillis()
        if (s.phase == Phase.ERROR && s.since != lastErrorShown) {
            lastErrorShown = s.since
            Toast.makeText(this, s.message, Toast.LENGTH_LONG).show()
        }
        val finished = s.phase == Phase.IDLE ||
            (s.phase == Phase.ENDED && now - s.since > 1500) ||
            (s.phase == Phase.ERROR && now - s.since > 8000)
        if (finished) {
            handler.removeCallbacks(tick)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(s))
        handler.postDelayed(tick, 500)
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun serviceIntent(action: String, req: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, req, Intent(this, ReadService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(s: FishPlayer.State): Notification {
        val now = System.currentTimeMillis()
        val smallIcon = resources.getIdentifier("ic_notif", "drawable", packageName).takeIf { it != 0 }
            ?: android.R.drawable.ic_media_play
        val timeText = if (s.streaming || s.duration <= 0.0) fmt(s.current) else "${fmt(s.current)} / ${fmt(s.duration)}"
        val (title, text) = when (s.phase) {
            Phase.LOADING -> "Generating audio..." to "${(now - s.since) / 1000}s"
            Phase.PLAYING -> (if (s.streaming) "Playing · live" else "Playing") to timeText
            Phase.PAUSED -> "Paused" to timeText
            Phase.ENDED -> "Done" to ""
            Phase.ERROR -> "Couldn't read that" to s.message
            Phase.IDLE -> "Fish Reader" to "Ready"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Made for LSS Only")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.phase == Phase.ERROR) b.setStyle(Notification.BigTextStyle().bigText(s.message))

        when (s.phase) {
            Phase.LOADING -> b.setProgress(0, 0, true)
            Phase.PLAYING, Phase.PAUSED ->
                if (s.streaming || s.duration <= 0.0) b.setProgress(0, 0, true)
                else b.setProgress(1000, (s.current / s.duration * 1000).toInt().coerceIn(0, 1000), false)
            Phase.ENDED -> b.setProgress(1000, 1000, false)
            else -> {}
        }

        if (s.phase == Phase.PLAYING || s.phase == Phase.PAUSED) {
            val playing = s.phase == Phase.PLAYING
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                    if (playing) "Pause" else "Resume",
                    serviceIntent(ACTION_TOGGLE, 1),
                ).build()
            )
        }
        if (s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED) {
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    serviceIntent(ACTION_STOP, 2),
                ).build()
            )
        }
        return b.build()
    }
}
