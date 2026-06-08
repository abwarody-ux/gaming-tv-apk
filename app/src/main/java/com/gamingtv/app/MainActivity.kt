package com.gamingtv.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.gamingtv.app.databinding.ActivityMainBinding
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var socketManager: SocketManager
    private lateinit var timerManager: TimerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSession: SessionState? = null
    private var totalDurationSeconds: Int = 0
    private var alertDismissJob: Runnable? = null
    private var blinkAnimation: Animation? = null

    // Kiosk & Pub
    private var kioskMode: Boolean = true
    private var videosPath: String = "/storage/videos/"
    private var pubDurationSeconds: Int = 30
    private var inactivityBeforePubSeconds: Int = 60
    private var inactivityHandler: Handler = Handler(Looper.getMainLooper())
    private var inactivityRunnable: Runnable? = null
    private var videoView: VideoView? = null
    private var videoFiles: List<File> = emptyList()
    private var currentVideoIndex: Int = 0
    private var isPubPlaying: Boolean = false

    companion object {
        private const val TAG = "MainActivity"
        private const val COLOR_GREEN = "#00FF88"
        private const val COLOR_ORANGE = "#FF9500"
        private const val COLOR_RED = "#FF3355"
        private const val COLOR_WHITE = "#FFFFFF"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupSocketManager()
        setupTimerManager()
        socketManager.connect()

        // Charger les paramètres kiosk depuis l'API
        loadKioskSettings()

        Log.d(TAG, "App started — TV_ID: ${Config.TV_ID}")
    }

    // ── KIOSK MODE ──
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (kioskMode) {
            // Bloquer retour, home, recent apps en mode kiosk
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_MENU) {
                return true // Consommer l'event
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (kioskMode) return // Bloquer
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Maintenir le fullscreen immersive
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            // En mode kiosk, revenir si l'app perd le focus
            if (kioskMode) {
                window.decorView.postDelayed({
                    startLockTask()
                }, 100)
            }
        }
    }

    // ── CHARGER PARAMÈTRES KIOSK ──
    private fun loadKioskSettings() {
        val client = OkHttpClient()
        val token = Config.TOKEN
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/settings")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to load kiosk settings: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val json = JSONObject(body)
                    kioskMode = json.optBoolean("kioskMode", true)
                    videosPath = json.optString("videosPath", "/storage/videos/")
                    pubDurationSeconds = json.optInt("pubDurationSeconds", 30)
                    inactivityBeforePubSeconds = json.optInt("inactivityBeforePubSeconds", 60)

                    mainHandler.post {
                        Log.d(TAG, "Kiosk: $kioskMode, Pub: ${inactivityBeforePubSeconds}s inactivity")
                        if (kioskMode) {
                            try { startLockTask() } catch (e: Exception) { }
                        }
                        // Charger les vidéos pub
                        loadVideoFiles()
                        // Démarrer le timer d'inactivité si sur écran d'attente
                        startInactivityTimer()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }
        })
    }

    // ── PUB VIDÉO ──
    private fun loadVideoFiles() {
        val folder = File(videosPath)
        videoFiles = if (folder.exists() && folder.isDirectory) {
            folder.listFiles { f -> f.extension.lowercase() == "mp4" }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else emptyList()
        Log.d(TAG, "Videos found: ${videoFiles.size}")
    }

    private fun startInactivityTimer() {
        stopInactivityTimer()
        if (videoFiles.isEmpty() || isPubPlaying || currentSession != null) return

        inactivityRunnable = Runnable {
            if (currentSession == null) {
                startPub()
            }
        }
        inactivityHandler.postDelayed(inactivityRunnable!!, inactivityBeforePubSeconds * 1000L)
        Log.d(TAG, "Inactivity timer started: ${inactivityBeforePubSeconds}s")
    }

    private fun stopInactivityTimer() {
        inactivityRunnable?.let { inactivityHandler.removeCallbacks(it) }
        inactivityRunnable = null
    }

    private fun startPub() {
        if (videoFiles.isEmpty()) return
        isPubPlaying = true
        Log.d(TAG, "Starting pub video")

        // Créer VideoView dynamiquement
        videoView = VideoView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Ajouter sur l'écran d'attente
        val rootLayout = binding.root as? android.widget.FrameLayout
        rootLayout?.addView(videoView)

        playNextVideo()
    }

    private fun playNextVideo() {
        if (!isPubPlaying || videoFiles.isEmpty()) return
        val file = videoFiles[currentVideoIndex % videoFiles.size]
        currentVideoIndex++

        videoView?.apply {
            setVideoURI(Uri.fromFile(file))
            setOnCompletionListener {
                if (isPubPlaying) playNextVideo()
            }
            setOnErrorListener { _, _, _ ->
                if (isPubPlaying) playNextVideo()
                true
            }
            start()
        }
    }

    private fun stopPub() {
        if (!isPubPlaying) return
        isPubPlaying = false
        videoView?.stopPlayback()
        val rootLayout = binding.root as? android.widget.FrameLayout
        rootLayout?.removeView(videoView)
        videoView = null
        Log.d(TAG, "Pub stopped")
    }

    // ── UI SETUP ──
    private fun setupUI() {
        binding.tvId.text = "TV ${Config.TV_ID}"
        binding.consoleBadge.text = "—"
        binding.alertContainer.visibility = View.GONE
        showWaitingState()
    }

    private fun setupSocketManager() {
        socketManager = SocketManager(
            onSessionStart = { session ->
                mainHandler.post { handleSessionStart(session) }
            },
            onTimeSync = { seconds, status ->
                mainHandler.post { handleTimeSync(seconds, status) }
            },
            onSessionPause = {
                mainHandler.post { handleSessionPause() }
            },
            onSessionResume = {
                mainHandler.post { handleSessionResume() }
            },
            onSessionEnd = { message ->
                mainHandler.post { handleSessionEnd(message) }
            },
            onAlert = { alert ->
                mainHandler.post { showAlert(alert.message, alert.type) }
            },
            onTimeAdded = { added, remaining ->
                mainHandler.post { handleTimeAdded(added, remaining) }
            },
            onConnectionChange = { connected ->
                mainHandler.post { updateConnectionStatus(connected) }
            }
        )
    }

    private fun setupTimerManager() {
        timerManager = TimerManager(
            onTick = { seconds ->
                mainHandler.post {
                    updateTimerDisplay(seconds)
                    updateProgressBar(seconds)
                    checkWarningThreshold(seconds)
                }
            },
            onFinished = {
                mainHandler.post {
                    handleSessionEnd("Temps écoulé !")
                    socketManager.emitSessionEndedLocal(0)
                }
            }
        )
    }

    // ── SESSION HANDLERS ──
    private fun handleSessionStart(session: SessionState) {
        Log.d(TAG, "Session started: ${session.ticketNumber}")
        currentSession = session
        totalDurationSeconds = session.timeRemainingSeconds

        // Arrêter la pub et le timer d'inactivité
        stopPub()
        stopInactivityTimer()

        binding.ticketNumber.text = "#${session.ticketNumber}"
        binding.consoleBadge.text = session.consoleType
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_GREEN))

        timerManager.start(session.timeRemainingSeconds)
        updateTimerDisplay(session.timeRemainingSeconds)
        updateProgressBar(session.timeRemainingSeconds)

        binding.sessionOverlay.visibility = View.VISIBLE
        binding.waitingScreen.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE
        binding.endScreen.visibility = View.GONE

        showAlert("Session démarrée ! Bonne partie 🎮", "SUCCESS")
    }

    private fun handleTimeSync(seconds: Int, status: String) {
        timerManager.syncTime(seconds)
        updateTimerDisplay(seconds)
        updateProgressBar(seconds)
    }

    private fun handleSessionPause() {
        timerManager.pause()
        binding.statusText.text = "EN PAUSE"
        binding.statusText.setTextColor(Color.parseColor(COLOR_ORANGE))
        binding.pauseScreen.visibility = View.VISIBLE
        stopBlinkAnimation()
    }

    private fun handleSessionResume() {
        timerManager.resume()
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_GREEN))
        binding.pauseScreen.visibility = View.GONE
        binding.sessionOverlay.visibility = View.VISIBLE
    }

    private fun handleSessionEnd(message: String) {
        timerManager.stop()
        currentSession = null
        totalDurationSeconds = 0
        stopBlinkAnimation()

        binding.endMessage.text = message
        binding.endScreen.visibility = View.VISIBLE
        binding.sessionOverlay.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE

        // Retour écran d'attente après 5s puis timer inactivité
        mainHandler.postDelayed({
            showWaitingState()
            startInactivityTimer()
        }, 5000)
    }

    private fun handleTimeAdded(added: Int, remaining: Int) {
        timerManager.syncTime(remaining)
        updateTimerDisplay(remaining)
        showAlert("+$added minutes ajoutées ✓", "SUCCESS")
    }

    // ── UI UPDATES ──
    private fun updateTimerDisplay(seconds: Int) {
        val min = seconds / 60
        val sec = seconds % 60
        binding.timerDisplay.text = String.format("%02d:%02d", min, sec)
        val color = when {
            seconds <= 60 -> COLOR_RED
            seconds <= 300 -> COLOR_ORANGE
            else -> COLOR_GREEN
        }
        binding.timerDisplay.setTextColor(Color.parseColor(color))
    }

    private fun updateProgressBar(seconds: Int) {
        if (totalDurationSeconds <= 0) return
        val progress = ((seconds.toFloat() / totalDurationSeconds.toFloat()) * 100).toInt()
        binding.progressBar.progress = progress.coerceIn(0, 100)
    }

    private fun checkWarningThreshold(seconds: Int) {
        when {
            seconds == Config.ALERT_BLINK_THRESHOLD -> {
                showAlert("⚠️ 5 minutes restantes !", "WARNING")
                startBlinkAnimation()
            }
            seconds == 60 -> showAlert("🔴 1 minute restante !", "CRITICAL")
            seconds <= 0 -> stopBlinkAnimation()
        }
    }

    private fun showWaitingState() {
        binding.waitingScreen.visibility = View.VISIBLE
        binding.sessionOverlay.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE
        binding.endScreen.visibility = View.GONE
        binding.alertContainer.visibility = View.GONE
        binding.ticketNumber.text = "—"
        binding.consoleBadge.text = "—"
        binding.statusText.text = "EN ATTENTE"
        binding.statusText.setTextColor(Color.parseColor(COLOR_WHITE))
        binding.timerDisplay.text = "--:--"
        binding.timerDisplay.setTextColor(Color.parseColor(COLOR_WHITE))
        binding.progressBar.progress = 0
    }

    private fun updateConnectionStatus(connected: Boolean) {
        binding.connectionDot.setBackgroundColor(
            if (connected) Color.parseColor(COLOR_GREEN) else Color.parseColor(COLOR_RED)
        )
        binding.connectionText.text = if (connected) "EN LIGNE" else "HORS LIGNE"
        if (!connected && currentSession != null) {
            showAlert("Connexion perdue — timer local actif", "WARNING")
        }
    }

    private fun showAlert(message: String, type: String = "INFO") {
        binding.alertText.text = message
        val color = when (type) {
            "SUCCESS" -> COLOR_GREEN
            "WARNING" -> COLOR_ORANGE
            "CRITICAL", "ERROR" -> COLOR_RED
            else -> COLOR_WHITE
        }
        binding.alertContainer.setBackgroundColor(
            Color.parseColor(color).let { Color.argb(200, Color.red(it), Color.green(it), Color.blue(it)) }
        )
        binding.alertText.setTextColor(Color.parseColor(color))
        binding.alertContainer.visibility = View.VISIBLE

        alertDismissJob?.let { mainHandler.removeCallbacks(it) }
        alertDismissJob = Runnable { binding.alertContainer.visibility = View.GONE }
        mainHandler.postDelayed(alertDismissJob!!, 4000)
    }

    private fun startBlinkAnimation() {
        blinkAnimation = AlphaAnimation(1.0f, 0.2f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.timerDisplay.startAnimation(blinkAnimation)
    }

    private fun stopBlinkAnimation() {
        blinkAnimation?.cancel()
        binding.timerDisplay.clearAnimation()
        blinkAnimation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPub()
        stopInactivityTimer()
        timerManager.destroy()
        socketManager.disconnect()
        mainHandler.removeCallbacksAndMessages(null)
        inactivityHandler.removeCallbacksAndMessages(null)
    }
}