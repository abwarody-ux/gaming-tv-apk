package com.gamingtv.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var socketManager: SocketManager
    private lateinit var timerManager: TimerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tvId: String = "TV00"
    private var tvToken: String = ""
    private var currentSession: SessionState? = null
    private var totalDurationSeconds: Int = 0
    private var alertDismissJob: Runnable? = null
    private var blinkAnimation: Animation? = null

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

    private var waitingManagerCountdown: CountDownTimer? = null
    private val MANAGER_WAIT_SECONDS = 90L

    private val frontendUrl = "https://gaming-tv-frontend.vercel.app"

    companion object {
        private const val TAG = "MainActivity"
        private const val COLOR_CYAN = "#00E5FF"
        private const val COLOR_GREEN = "#00FF88"
        private const val COLOR_ORANGE = "#FF9500"
        private const val COLOR_RED = "#FF3355"
        private const val COLOR_WHITE = "#FFFFFF"
        private const val COLOR_BLUE = "#4FC3F7"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lire le TV_ID et le tv_token depuis SharedPreferences
        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        tvId = prefs.getString(Config.KEY_TV_ID, "TV00") ?: "TV00"
        tvToken = prefs.getString(Config.KEY_TV_TOKEN, "") ?: ""

        setupUI()
        setupTimerManager()
        loadKioskSettings()

        if (tvToken.isNotEmpty()) {
            setupSocketManager()
            socketManager.connect()
        } else {
            // TV activee avant le correctif de securite — rattrapage via mac_address deja connue
            ensureTvTokenThenConnect(prefs)
        }

        Log.d(TAG, "App started — TV_ID: $tvId")
    }

    private fun ensureTvTokenThenConnect(prefs: android.content.SharedPreferences) {
        val mac = prefs.getString(Config.KEY_MAC_ADDRESS, null)
        if (mac.isNullOrEmpty()) {
            Log.e(TAG, "Impossible de recuperer un tv_token: mac_address inconnue localement")
            return
        }
        val json = JSONObject().apply { put("mac_address", mac) }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/kasmok/tv-registry/token-by-mac")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Rattrapage tv_token echoue (reseau): ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return
                if (!response.isSuccessful) {
                    Log.e(TAG, "Rattrapage tv_token echoue: $bodyStr")
                    return
                }
                try {
                    val data = JSONObject(bodyStr)
                    val newToken = data.optString("tv_token", "")
                    if (newToken.isEmpty()) return
                    tvToken = newToken
                    prefs.edit().putString(Config.KEY_TV_TOKEN, newToken).apply()
                    mainHandler.post {
                        setupSocketManager()
                        socketManager.connect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur parsing rattrapage tv_token: ${e.message}")
                }
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (kioskMode) {
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_MENU) {
                showAlert("Veuillez contacter KASMOK DIGITAL pour cette action", "WARNING", 3000)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (kioskMode) return
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            if (kioskMode) window.decorView.postDelayed({ enableKioskModeIfPossible() }, 100)
        }
    }

    private fun enableKioskModeIfPossible() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
                startLockTask()
                Log.d(TAG, "Kiosk mode: Device Owner actif — lock task silencieux")
            } else {
                startLockTask()
                Log.d(TAG, "Kiosk mode: pas Device Owner — screen pinning standard (confirmation utilisateur requise)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kiosk mode: échec activation — ${e.message}")
        }
    }

    private fun loadKioskSettings() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/kasmok/tv-registry/$tvId/settings")
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
                        if (kioskMode) enableKioskModeIfPossible()
                        loadVideoFiles()
                        startInactivityTimer()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }
        })
    }

    private fun showQrCode(ticketNumber: String, consoleType: String) {
        val url = "$frontendUrl/addtime?tv=$tvId&ticket=$ticketNumber&console=$consoleType"
        binding.qrCodeView.setQrData(url, "#$ticketNumber")
        binding.qrCodeView.visibility = View.VISIBLE
        binding.qrCodeViewPause.setQrData(url, "#$ticketNumber")
        Log.d(TAG, "QR Code shown: $url")
    }

    private fun hideQrCode() {
        binding.qrCodeView.visibility = View.GONE
        binding.qrCodeViewPause.visibility = View.GONE
    }

    private fun loadVideoFiles() {
        val folder = File(videosPath)
        videoFiles = if (folder.exists() && folder.isDirectory) {
            folder.listFiles { f -> f.extension.lowercase() == "mp4" }?.sortedBy { it.name } ?: emptyList()
        } else emptyList()
    }

    private fun startInactivityTimer() {
        stopInactivityTimer()
        if (videoFiles.isEmpty() || isPubPlaying || currentSession != null) return
        inactivityRunnable = Runnable { if (currentSession == null) startPub() }
        inactivityHandler.postDelayed(inactivityRunnable!!, inactivityBeforePubSeconds * 1000L)
    }

    private fun stopInactivityTimer() {
        inactivityRunnable?.let { inactivityHandler.removeCallbacks(it) }
        inactivityRunnable = null
    }

    private fun startPub() {
        if (videoFiles.isEmpty()) return
        isPubPlaying = true
        videoView = VideoView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        (binding.root as? android.widget.FrameLayout)?.addView(videoView)
        playNextVideo()
    }

    private fun playNextVideo() {
        if (!isPubPlaying || videoFiles.isEmpty()) return
        val file = videoFiles[currentVideoIndex % videoFiles.size]
        currentVideoIndex++
        videoView?.apply {
            setVideoURI(Uri.fromFile(file))
            setOnCompletionListener { if (isPubPlaying) playNextVideo() }
            setOnErrorListener { _, _, _ -> if (isPubPlaying) playNextVideo(); true }
            start()
        }
    }

    private fun stopPub() {
        if (!isPubPlaying) return
        isPubPlaying = false
        videoView?.stopPlayback()
        (binding.root as? android.widget.FrameLayout)?.removeView(videoView)
        videoView = null
    }

    private fun setupUI() {
        binding.tvId.text = "TV $tvId"
        binding.consoleBadge.text = "—"
        binding.alertContainer.visibility = View.GONE
        showWaitingState()
    }

    private fun setupSocketManager() {
        socketManager = SocketManager(
            tvId = tvId,
            tvToken = tvToken,
            onSessionStart = { session -> mainHandler.post { handleSessionStart(session) } },
            onTimeSync = { seconds, status -> mainHandler.post { handleTimeSync(seconds, status) } },
            onSessionPause = { mainHandler.post { handleSessionPause() } },
            onSessionResume = { mainHandler.post { handleSessionResume() } },
            onSessionEnd = { message -> mainHandler.post { handleSessionEnd(message) } },
            onAlert = { alert -> mainHandler.post { showAlert(alert.message, alert.type) } },
            onTimeAdded = { added, remaining -> mainHandler.post { handleTimeAdded(added, remaining) } },
            onConnectionChange = { connected -> mainHandler.post { updateConnectionStatus(connected) } }
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

    private fun handleSessionStart(session: SessionState) {
        Log.d(TAG, "Session started: ${session.ticketNumber}")
        if (session.status == "WAITING_MANAGER") {
            handleWaitingManager(session)
            return
        }
        startActiveSession(session)
    }

    private fun handleWaitingManager(session: SessionState) {
        currentSession = session
        stopPub()
        stopInactivityTimer()
        cancelManagerCountdown()

        binding.waitingScreen.visibility = View.GONE
        binding.sessionOverlay.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE
        binding.endScreen.visibility = View.GONE
        binding.waitingScreen.visibility = View.VISIBLE

        binding.tvId.text = "#${session.ticketNumber}"
        binding.connectionText.text = "EN ATTENTE DU MANAGER"
        binding.connectionText.setTextColor(Color.parseColor(COLOR_BLUE))
        binding.connectionDot.setBackgroundColor(Color.parseColor(COLOR_BLUE))

        showPersistentManagerAlert(session)

        waitingManagerCountdown = object : CountDownTimer(MANAGER_WAIT_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val min = seconds / 60
                val sec = seconds % 60
                val timeStr = String.format("%02d:%02d", min, sec)
                mainHandler.post {
                    binding.alertText.text = "⏳ Nouveau client — ${session.ticketNumber} — ${session.consoleType}\nDémarrage auto dans $timeStr"
                }
            }
            override fun onFinish() {
                mainHandler.post { binding.alertContainer.visibility = View.GONE }
            }
        }.start()
    }

    private fun showPersistentManagerAlert(session: SessionState) {
        binding.alertText.text = "⏳ Nouveau client — ${session.ticketNumber} — ${session.consoleType}\nEn attente du manager..."
        binding.alertContainer.setBackgroundColor(Color.parseColor(COLOR_BLUE).let {
            Color.argb(220, Color.red(it), Color.green(it), Color.blue(it))
        })
        binding.alertText.setTextColor(Color.parseColor(COLOR_WHITE))
        binding.alertContainer.visibility = View.VISIBLE
        alertDismissJob?.let { mainHandler.removeCallbacks(it) }
    }

    private fun cancelManagerCountdown() {
        waitingManagerCountdown?.cancel()
        waitingManagerCountdown = null
    }

    private fun startActiveSession(session: SessionState) {
        cancelManagerCountdown()
        currentSession = session
        totalDurationSeconds = session.timeRemainingSeconds

        stopPub()
        stopInactivityTimer()

        binding.tvId.text = "TV $tvId"
        binding.ticketNumber.text = "#${session.ticketNumber}"
        binding.consoleBadge.text = session.consoleType
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_CYAN))

        timerManager.start(session.timeRemainingSeconds)
        updateTimerDisplay(session.timeRemainingSeconds)
        updateProgressBar(session.timeRemainingSeconds)

        binding.sessionOverlay.visibility = View.VISIBLE
        binding.waitingScreen.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE
        binding.endScreen.visibility = View.GONE
        binding.alertContainer.visibility = View.GONE

        showQrCode(session.ticketNumber, session.consoleType)
        showAlert("Session démarrée ! Bonne partie 🎮", "SUCCESS")
    }

    private fun handleTimeSync(seconds: Int, status: String) {
        if (status == "WAITING_MANAGER") return
        if (currentSession?.status == "WAITING_MANAGER" && status == "ACTIVE") {
            currentSession?.let { startActiveSession(it.copy(timeRemainingSeconds = seconds, status = "ACTIVE")) }
            return
        }
        timerManager.syncTime(seconds)
        updateTimerDisplay(seconds)
        updateProgressBar(seconds)
    }

    private fun handleSessionPause() {
        timerManager.pause()
        binding.statusText.text = "EN PAUSE"
        binding.statusText.setTextColor(Color.parseColor(COLOR_ORANGE))
        binding.pauseScreen.visibility = View.VISIBLE
        binding.qrCodeViewPause.visibility = View.VISIBLE
        stopBlinkAnimation()
    }

    private fun handleSessionResume() {
        timerManager.resume()
        binding.statusText.text = "EN JEU"
        binding.statusText.setTextColor(Color.parseColor(COLOR_CYAN))
        binding.pauseScreen.visibility = View.GONE
        binding.sessionOverlay.visibility = View.VISIBLE
        binding.qrCodeViewPause.visibility = View.GONE
        binding.qrCodeView.visibility = View.VISIBLE
    }

    private fun handleSessionEnd(message: String) {
        cancelManagerCountdown()
        timerManager.stop()
        currentSession = null
        totalDurationSeconds = 0
        stopBlinkAnimation()
        hideQrCode()

        binding.endMessage.text = message
        binding.endScreen.visibility = View.VISIBLE
        binding.sessionOverlay.visibility = View.GONE
        binding.pauseScreen.visibility = View.GONE

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

    private fun updateTimerDisplay(seconds: Int) {
        val min = seconds / 60
        val sec = seconds % 60
        binding.timerDisplay.text = String.format("%02d:%02d", min, sec)
        val color = when {
            seconds <= 60 -> COLOR_RED
            seconds <= 300 -> COLOR_ORANGE
            else -> COLOR_CYAN
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
        hideQrCode()
        binding.tvId.text = "TV $tvId"
        binding.ticketNumber.text = "—"
        binding.consoleBadge.text = "—"
        binding.statusText.text = "EN ATTENTE"
        binding.statusText.setTextColor(Color.parseColor(COLOR_WHITE))
        binding.timerDisplay.text = "--:--"
        binding.timerDisplay.setTextColor(Color.parseColor(COLOR_WHITE))
        binding.progressBar.progress = 0
        binding.connectionText.text = "EN LIGNE"
        binding.connectionText.setTextColor(Color.parseColor(COLOR_GREEN))
    }

    private fun updateConnectionStatus(connected: Boolean) {
        binding.connectionDot.setBackgroundColor(
            if (connected) Color.parseColor(COLOR_GREEN) else Color.parseColor(COLOR_RED)
        )
        if (currentSession?.status != "WAITING_MANAGER") {
            binding.connectionText.text = if (connected) "EN LIGNE" else "HORS LIGNE"
        }
        if (!connected && currentSession != null) {
            showAlert("Connexion perdue — timer local actif", "WARNING")
        }
    }

    private fun showAlert(message: String, type: String = "INFO", durationMs: Long = 4000) {
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
        mainHandler.postDelayed(alertDismissJob!!, durationMs)
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
        cancelManagerCountdown()
        stopPub()
        stopInactivityTimer()
        timerManager.destroy()
        socketManager.disconnect()
        mainHandler.removeCallbacksAndMessages(null)
        inactivityHandler.removeCallbacksAndMessages(null)
    }
}