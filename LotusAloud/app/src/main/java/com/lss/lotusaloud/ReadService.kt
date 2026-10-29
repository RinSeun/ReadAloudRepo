package com.lss.lotusaloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.lss.lotusaloud.SpeechPlayer.Phase

/**
 * Keeps reading while you use other apps. Shows a media player notification and
 * works with lock-screen controls, earbuds, Bluetooth and car buttons.
 */
class ReadService : Service() {
    companion object {
        const val ACTION_READ = "com.lss.lotusaloud.READ"
        const val ACTION_TOGGLE = "com.lss.lotusaloud.TOGGLE"
        const val ACTION_STOP = "com.lss.lotusaloud.STOP"
        const val ACTION_BACK = "com.lss.lotusaloud.BACK10"
        const val ACTION_FWD = "com.lss.lotusaloud.FWD10"
        const val ACTION_READ_IMAGES = "com.lss.lotusaloud.READ_IMAGES"
        const val ACTION_NEXT_PART = "com.lss.lotusaloud.NEXT_PART"
        const val ACTION_PREV_PART = "com.lss.lotusaloud.PREV_PART"
        const val ACTION_JUMP = "com.lss.lotusaloud.JUMP"
        const val EXTRA_PART = "part"
        const val EXTRA_FRACTION = "fraction"
        const val EXTRA_PATHS = "paths"
        const val EXTRA_TEXT = "text"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_RECORD = "record"
        private const val CHANNEL = "reading"
        private const val NOTIF_ID = 7
        private const val SKIP = 10.0

        /** record = add it to Recent reads (not used for tests and voice samples). */
        fun read(ctx: Context, text: String, voice: String? = null, record: Boolean = true) {
            val i = Intent(ctx, ReadService::class.java).setAction(ACTION_READ)
                .putExtra(EXTRA_TEXT, text).putExtra(EXTRA_RECORD, record)
            if (voice != null) i.putExtra(EXTRA_VOICE, voice)
            ctx.startForegroundService(i)
        }

        /** Finds the text in screenshots / photos (already copied into the app), then reads it. */
        fun readImages(ctx: Context, paths: List<String>) {
            val i = Intent(ctx, ReadService::class.java).setAction(ACTION_READ_IMAGES)
                .putExtra(EXTRA_PATHS, paths.toTypedArray())
            ctx.startForegroundService(i)
        }

        /** Jump to a spot: part number + how far into it (0..1). Used by the follow-along screen. */
        fun jump(ctx: Context, part: Int, fraction: Double) {
            ctx.startForegroundService(
                Intent(ctx, ReadService::class.java).setAction(ACTION_JUMP)
                    .putExtra(EXTRA_PART, part).putExtra(EXTRA_FRACTION, fraction)
            )
        }

        fun control(ctx: Context, action: String) {
            ctx.startForegroundService(Intent(ctx, ReadService::class.java).setAction(action))
        }

        fun fmt(seconds: Double): String {
            val t = seconds.toInt().coerceAtLeast(0)
            return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
        }
    }

    private val player get() = SpeechPlayer.shared
    private val handler = Handler(Looper.getMainLooper())
    private var lastStartId = 0
    private var lastErrorShown = 0L
    private var title = "Lotus Aloud"
    private var lastDuration = -1.0
    private lateinit var session: MediaSession
    private var focusRequest: AudioFocusRequest? = null
    /** Shown while we're preparing text (reading a screenshot, fetching an article...). */
    @Volatile private var preparing: String? = null
    private val tick = object : Runnable {
        override fun run() = update()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        player.init(this)
        val ch = NotificationChannel(CHANNEL, "Reading aloud", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)

        session = MediaSession(this, "LotusAloud")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { player.resume(); update() }
            override fun onPause() { player.pause(); update() }
            override fun onStop() { player.stop(); update() }
            override fun onSeekTo(pos: Long) { player.seekTo(pos / 1000.0); update() }
            override fun onFastForward() { player.skip(SKIP); update() }
            override fun onRewind() { player.skip(-SKIP); update() }
            // earbud double / triple tap: next / previous part (or 10 s when there's only one part)
            override fun onSkipToNext() { if (ReadSession.count > 1) nextPart() else player.skip(SKIP); update() }
            override fun onSkipToPrevious() { if (ReadSession.count > 1) prevPart() else player.skip(-SKIP); update() }
            override fun onCustomAction(action: String, extras: Bundle?) {
                when (action) {
                    ACTION_BACK -> player.skip(-SKIP)
                    ACTION_FWD -> player.skip(SKIP)
                }
                update()
            }
        })
        session.setSessionActivity(openAppIntent())

        // when a part finishes, carry straight on with the next one
        player.onEnded = {
            handler.post {
                if (ReadSession.hasNext) playPart(ReadSession.index + 1)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        goForeground(buildNotification(player.snapshot()))
        when (intent?.action) {
            ACTION_READ -> startRead(
                intent.getStringExtra(EXTRA_TEXT) ?: "",
                intent.getStringExtra(EXTRA_VOICE),
                intent.getBooleanExtra(EXTRA_RECORD, true),
            )
            ACTION_READ_IMAGES -> {
                val paths = intent.getStringArrayExtra(EXTRA_PATHS)?.toList() ?: emptyList()
                if (paths.isNotEmpty()) {
                    player.stop()
                    preparing = "Finding the text in your screenshot..."
                    Thread({
                        val found = try { ImageText.recognize(this, paths) } catch (e: Exception) { "" }
                        handler.post {
                            preparing = null
                            if (found.isBlank()) player.showError("Couldn't find any text in that image.")
                            else startRead(found, null, true)
                            update()
                        }
                    }, "lotus-ocr").start()
                }
            }
            ACTION_NEXT_PART -> nextPart()
            ACTION_PREV_PART -> prevPart()
            ACTION_JUMP -> {
                val part = intent.getIntExtra(EXTRA_PART, 0)
                val frac = intent.getDoubleExtra(EXTRA_FRACTION, 0.0).coerceIn(0.0, 1.0)
                val now = player.snapshot()
                val canSeekHere = part == ReadSession.index && !now.streaming && now.duration > 0 &&
                    (now.phase == Phase.PLAYING || now.phase == Phase.PAUSED || now.phase == Phase.ENDED)
                if (canSeekHere) {
                    player.seekTo(frac * now.duration)
                } else if (part in ReadSession.parts.indices) {
                    playPart(part)
                    ReadSession.pendingFraction = if (frac > 0.02) frac else null
                }
            }
            ACTION_TOGGLE -> player.togglePause()
            ACTION_STOP -> player.stop()
            ACTION_BACK -> player.skip(-SKIP)
            ACTION_FWD -> player.skip(SKIP)
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private fun startRead(raw: String, voiceOverride: String?, record: Boolean) {
        val prefs = Prefs(this)
        val voiceToUse = voiceOverride ?: prefs.mainVoice
        if (!prefs.hasKeyFor(voiceToUse)) {
            player.showError(Providers.missingKeyText(voiceToUse))
            return
        }
        // A webpage link? Fetch the article and read that instead.
        val link = Article.linkIn(raw)
        if (link != null) {
            player.stop()
            preparing = "Getting the article..."
            Thread({
                val result = try { Result.success(Article.fetch(link)) } catch (e: Exception) { Result.failure(e) }
                handler.post {
                    preparing = null
                    result.onSuccess { startText(it.text, voiceOverride, record, it.title) }
                        .onFailure { player.showError(it.message ?: "Couldn't read that link.") }
                    update()
                }
            }, "lotus-article").start()
            return
        }
        startText(raw, voiceOverride, record, null)
    }

    private fun startText(raw: String, voiceOverride: String?, record: Boolean, titleOverride: String?) {
        val prefs = Prefs(this)
        val text = TextCleaner.clean(raw)
        val voice = voiceOverride ?: prefs.mainVoice
        val parts = Parts.split(text)
        if (parts.isEmpty()) {
            player.showError("There's no text to read.")
            return
        }
        if (record) History.add(this, text, voice)
        title = (titleOverride ?: text).replace(Regex("\\s+"), " ").take(80)
        ReadSession.start(title, parts, voice)
        requestFocus()
        playPart(0)
    }

    /** Plays one part, and quietly prepares the next one so there's no gap. */
    private fun playPart(i: Int) {
        val parts = ReadSession.parts
        if (i !in parts.indices) return
        val prefs = Prefs(this)
        ReadSession.index = i
        ReadSession.pendingFraction = null
        lastDuration = -1.0
        player.setSpeed(prefs.speed)
        player.speak(parts[i], ReadSession.voice, TtsConfig.from(prefs))
        if (i + 1 < parts.size) {
            player.prefetch(parts[i + 1], ReadSession.voice, TtsConfig.from(prefs))
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun nextPart() { if (ReadSession.hasNext) playPart(ReadSession.index + 1) }

    private fun prevPart() {
        val s = player.snapshot()
        // like a music player: back to the start of this part, or the previous part if we're near the start
        if (s.current > 3.0 || !ReadSession.hasPrev) {
            if (s.phase == Phase.PLAYING || s.phase == Phase.PAUSED) player.seekTo(0.0) else playPart(ReadSession.index)
        } else {
            playPart(ReadSession.index - 1)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        abandonFocus()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ audio focus (pauses for calls etc.)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player.pause()
        }
    }

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val req = focusRequest ?: return
        getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(req)
        focusRequest = null
    }

    // ------------------------------------------------------------------ updates
    private fun update() {
        val s = player.snapshot()
        val now = System.currentTimeMillis()
        if (s.phase == Phase.ERROR && s.since != lastErrorShown) {
            lastErrorShown = s.since
            Toast.makeText(this, s.message, Toast.LENGTH_LONG).show()
        }
        updateSession(s)
        val jump = ReadSession.pendingFraction
        if (jump != null && s.phase == Phase.PLAYING && !s.streaming && s.duration > 0) {
            ReadSession.pendingFraction = null
            player.seekTo(jump * s.duration)
        }
        val finished = preparing == null && (
            s.phase == Phase.IDLE ||
                (s.phase == Phase.ENDED && !ReadSession.hasNext && now - s.since > 2500) ||
                (s.phase == Phase.ERROR && now - s.since > 8000)
            )
        if (finished) {
            handler.removeCallbacks(tick)
            abandonFocus()
            session.isActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(s))
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 400)
    }

    private fun updateSession(s: SpeechPlayer.State) {
        val busy = s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED
        val duration = if (s.streaming) -1.0 else s.duration
        if (duration != lastDuration) {
            lastDuration = duration
            val md = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Lotus Aloud · Made for LSS Only")
            if (duration > 0) md.putLong(MediaMetadata.METADATA_KEY_DURATION, (duration * 1000).toLong())
            session.setMetadata(md.build())
        }
        val state = when (s.phase) {
            Phase.LOADING -> PlaybackState.STATE_BUFFERING
            Phase.PLAYING -> PlaybackState.STATE_PLAYING
            Phase.PAUSED -> PlaybackState.STATE_PAUSED
            Phase.ERROR -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_STOPPED
        }
        val pb = PlaybackState.Builder()
            .setState(state, (s.current * 1000).toLong(), if (s.phase == Phase.PLAYING) s.speed else 0f)
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
            )
            .addCustomAction(PlaybackState.CustomAction.Builder(ACTION_BACK, "Back 10s", android.R.drawable.ic_media_rew).build())
            .addCustomAction(PlaybackState.CustomAction.Builder(ACTION_FWD, "Forward 10s", android.R.drawable.ic_media_ff).build())
        if (s.phase == Phase.ERROR) pb.setErrorMessage(s.message)
        session.setPlaybackState(pb.build())
        if (busy && !session.isActive) session.isActive = true
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    /** Tapping the notification opens the follow-along screen. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, ReaderActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String, req: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, req, Intent(this, ReadService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(icon: Int, label: String, act: String, req: Int) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), label, serviceIntent(act, req)).build()

    private fun buildNotification(s: SpeechPlayer.State): Notification {
        val now = System.currentTimeMillis()
        val smallIcon = resources.getIdentifier("ic_notif", "drawable", packageName).takeIf { it != 0 }
            ?: android.R.drawable.ic_media_play
        val timeText = if (s.streaming || s.duration <= 0.0) fmt(s.current) else "${fmt(s.current)} / ${fmt(s.duration)}"
        val partInfo = if (ReadSession.count > 1) " \u00b7 Part ${ReadSession.index + 1}/${ReadSession.count}" else ""
        val (head, text) = if (preparing != null) (preparing ?: "") to "Lotus Aloud" else when (s.phase) {
            Phase.LOADING -> "Generating audio..." to "${(now - s.since) / 1000}s"
            Phase.PLAYING -> (if (s.streaming) "Playing \u00b7 live" else "Playing") to timeText + partInfo
            Phase.PAUSED -> "Paused" to timeText + partInfo
            Phase.ENDED -> "Done" to ""
            Phase.ERROR -> "Couldn't read that" to s.message
            Phase.IDLE -> "Lotus Aloud" to "Ready"
        }
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(smallIcon)
            .setContentTitle(if (s.phase == Phase.PLAYING || s.phase == Phase.PAUSED) title else head)
            .setContentText(if (s.phase == Phase.PLAYING || s.phase == Phase.PAUSED) "$head · $text" else text)
            .setSubText("Made for LSS Only")
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        if (preparing != null) {
            b.setProgress(0, 0, true)
            return b.build()
        }
        when (s.phase) {
            Phase.PLAYING, Phase.PAUSED, Phase.ENDED -> {
                val playing = s.phase == Phase.PLAYING
                b.addAction(action(android.R.drawable.ic_media_rew, "Back 10s", ACTION_BACK, 3))
                b.addAction(
                    action(
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (playing) "Pause" else "Resume", ACTION_TOGGLE, 1,
                    )
                )
                b.addAction(action(android.R.drawable.ic_media_ff, "Forward 10s", ACTION_FWD, 4))
                b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 2))
                b.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            }
            Phase.LOADING -> {
                b.setProgress(0, 0, true)
                b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 2))
            }
            Phase.ERROR -> b.setStyle(Notification.BigTextStyle().bigText(s.message))
            else -> {}
        }
        return b.build()
    }
}
