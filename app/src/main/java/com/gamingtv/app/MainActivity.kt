package com.gamingtv.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import com.gamingtv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var socketManager: SocketManager
    private lateinit var timerManager: TimerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSession: SessionState? = null
    private var totalDurationSeconds: Int = 0
    private var alertDismissJob: Runnable? = null
    private var blinkAnimation: Animation? = null

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
        Log.d(TAG, "App started — TV_ID: ${Config.TV_ID}")
    }

    private fun setupUI() {
        // TV ID display
        binding.tvId.text = "TV ${Config.TV_ID}"

        // Console badge
        binding.consoleBadge.text = "—"

        // Hide alert initially
        binding.alertContainer.visibility = View.GONE

        // Status initial
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

    // ── Session handlers ──
    private fun handleSessionStart(session: SessionState) {
        Log.d(TAG, "Session started: ${session.ticketNumber}")
        currentSession = session
        totalDurationSeconds = session.timeRemainingSeconds

        binding.ticketNumber.text = "#${session.ticketNumber}"
        binding.consoleBadge.text = session.consoleType
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_GREEN))

        timerManager.start(session.timeRemainingSeconds)
        updateTimerDisplay(session.timeRemainingSeconds)
        updateProgressBar(session.timeRemainingSeconds)

        // Show session overlay
        binding.sessionOverlay.visibility = View.VISIBLE
        binding.waitingScreen.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE
        binding.endScreen.visibility = View.GONE

        showAlert("Session démarrée ! Bonne partie 🎮", "SUCCESS")
    }

    private fun handleTimeSync(seconds: Int, status: String) {
        // Réconciliation backend — source de vérité
        timerManager.syncTime(seconds)
        updateTimerDisplay(seconds)
        updateProgressBar(seconds)
    }

    private fun handleSessionPause() {
        Log.d(TAG, "Session paused")
        timerManager.pause()
        binding.statusText.text = "EN PAUSE"
        binding.statusText.setTextColor(Color.parseColor(COLOR_ORANGE))
        binding.pauseScreen.visibility = View.VISIBLE
        stopBlinkAnimation()
    }

    private fun handleSessionResume() {
        Log.d(TAG, "Session resumed")
        timerManager.resume()
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_GREEN))
        binding.pauseScreen.visibility = View.GONE
        binding.sessionOverlay.visibility = View.VISIBLE
    }

    private fun handleSessionEnd(message: String) {
        Log.d(TAG, "Session ended: $message")
        timerManager.stop()
        currentSession = null
        totalDurationSeconds = 0
        stopBlinkAnimation()

        binding.endMessage.text = message
        binding.endScreen.visibility = View.VISIBLE
        binding.sessionOverlay.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE

        // Retour à l'écran d'attente après 5 secondes
        mainHandler.postDelayed({
            showWaitingState()
        }, 5000)
    }

    private fun handleTimeAdded(added: Int, remaining: Int) {
        timerManager.syncTime(remaining)
        updateTimerDisplay(remaining)
        showAlert("+$added minutes ajoutées ✓", "SUCCESS")
    }

    // ── UI updates ──
    private fun updateTimerDisplay(seconds: Int) {
        val min = seconds / 60
        val sec = seconds % 60
        val timeStr = String.format("%02d:%02d", min, sec)
        binding.timerDisplay.text = timeStr

        // Couleur selon temps restant
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
            seconds == 60 -> {
                showAlert("🔴 1 minute restante !", "CRITICAL")
            }
            seconds <= 0 -> {
                stopBlinkAnimation()
            }
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

    // ── Alert system ──
    private fun showAlert(message: String, type: String = "INFO") {
        binding.alertText.text = message
        val color = when (type) {
            "SUCCESS" -> COLOR_GREEN
            "WARNING" -> COLOR_ORANGE
            "CRITICAL" -> COLOR_RED
            "ERROR" -> COLOR_RED
            else -> COLOR_WHITE
        }
        binding.alertContainer.setBackgroundColor(
            Color.parseColor(color).let { Color.argb(200, Color.red(it), Color.green(it), Color.blue(it)) }
        )
        binding.alertText.setTextColor(Color.parseColor(color))
        binding.alertContainer.visibility = View.VISIBLE

        // Auto-dismiss après 4 secondes
        alertDismissJob?.let { mainHandler.removeCallbacks(it) }
        alertDismissJob = Runnable {
            binding.alertContainer.visibility = View.GONE
        }
        mainHandler.postDelayed(alertDismissJob!!, 4000)
    }

    // ── Blink animation for low time ──
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
        timerManager.destroy()
        socketManager.disconnect()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
