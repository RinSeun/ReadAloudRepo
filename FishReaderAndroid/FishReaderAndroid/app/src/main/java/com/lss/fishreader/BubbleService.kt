package com.lss.fishreader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/**
 * Floating fish bubble shown over every app.
 * Copy text anywhere (Opera, Messenger...), then tap the bubble to read it.
 * Tap while reading = stop. Drag to move. Long-press = open Fish Reader.
 */
class BubbleService : Service() {
    companion object {
        private const val CHANNEL = "bubble"
        private const val NOTIF_ID = 8
        const val ACTION_HIDE = "com.lss.fishreader.HIDE_BUBBLE"

        fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

        fun start(ctx: Context) {
            Prefs(ctx).bubbleOn = true
            if (canShow(ctx)) ctx.startForegroundService(Intent(ctx, BubbleService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs(ctx).bubbleOn = false
            ctx.stopService(Intent(ctx, BubbleService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var bubble: View? = null
    private lateinit var ring: GradientDrawable
    private lateinit var lp: WindowManager.LayoutParams
    private val player get() = FishPlayer.shared

    private val poll = object : Runnable {
        override fun run() {
            val color = when (player.snapshot().phase) {
                FishPlayer.Phase.LOADING -> Color.parseColor("#8250DF")
                FishPlayer.Phase.PLAYING -> Color.parseColor("#2EA043")
                FishPlayer.Phase.PAUSED -> Color.parseColor("#D29922")
                FishPlayer.Phase.ERROR -> Color.parseColor("#F85149")
                else -> Color.TRANSPARENT
            }
            ring.setStroke(dp(3), color)
            handler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        player.init(this)
        val ch = NotificationChannel(CHANNEL, "Floating bubble", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (intent?.action == ACTION_HIDE || !canShow(this)) {
            Prefs(this).bubbleOn = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        bubble?.let { try { wm?.removeView(it) } catch (e: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val smallIcon = resources.getIdentifier("ic_notif", "drawable", packageName).takeIf { it != 0 }
            ?: android.R.drawable.ic_media_play
        val hide = PendingIntent.getService(
            this, 10, Intent(this, BubbleService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 11, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(smallIcon)
            .setContentTitle("Fish bubble is on")
            .setContentText("Copy text, then tap the fish bubble to read it.")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, smallIcon), "Hide bubble", hide).build())
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val w = getSystemService(WindowManager::class.java) ?: return
        wm = w
        val size = dp(56)

        val container = FrameLayout(this)
        ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setColor(Color.TRANSPARENT)
        ring.setStroke(dp(3), Color.TRANSPARENT)
        val icon = ImageView(this)
        icon.setImageResource(resources.getIdentifier("ic_launcher", "drawable", packageName))
        icon.contentDescription = "Fish Read"
        container.addView(icon, FrameLayout.LayoutParams(size, size))
        val ringView = View(this)
        ringView.background = ring
        container.addView(ringView, FrameLayout.LayoutParams(size, size))
        container.elevation = dp(6).toFloat()
        container.alpha = 0.92f

        val prefs = Prefs(this)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (prefs.bubbleX >= 0) prefs.bubbleX.coerceIn(0, screenW - size) else screenW - size - dp(4)
        lp.y = if (prefs.bubbleY >= 0) prefs.bubbleY.coerceIn(0, screenH - size) else screenH / 3

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        container.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false; longPressed = false
                    v.alpha = 1f
                    handler.postDelayed(longPress, 700)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > dp(8) || abs(dy) > dp(8))) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceIn(0, screenW - size)
                        lp.y = (startY + dy).toInt().coerceIn(0, screenH - size)
                        try { w.updateViewLayout(v, lp) } catch (ex: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    v.alpha = 0.92f
                    if (dragging) {
                        // snap to the nearest side
                        lp.x = if (lp.x + size / 2 < screenW / 2) dp(4) else screenW - size - dp(4)
                        try { w.updateViewLayout(v, lp) } catch (ex: Exception) {}
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP) {
                        onTap()
                    }
                }
            }
            true
        }

        try {
            w.addView(container, lp)
            bubble = container
            handler.post(poll)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun onTap() {
        if (player.isBusy()) {
            player.stop()
            return
        }
        // Reads what you copied (a tiny invisible screen grabs the clipboard, then closes)
        startActivity(Intent(this, ClipReadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
