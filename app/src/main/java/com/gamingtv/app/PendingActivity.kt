package com.gamingtv.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class PendingActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollingHandler: Handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_pending)

        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        val mac = prefs.getString(Config.KEY_MAC_ADDRESS, "—") ?: "—"

        findViewById<TextView>(R.id.macAddressText).text = mac

        // Bouton scanner QR
        findViewById<Button>(R.id.scanQrButton).setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }

        // Démarrer polling toutes les 10s
        startPolling(mac)
    }

    private fun startPolling(mac: String) {
        pollingRunnable = object : Runnable {
            override fun run() {
                checkStatus(mac)
                pollingHandler.postDelayed(this, 10_000L)
            }
        }
        pollingHandler.post(pollingRunnable!!)
    }

    private fun checkStatus(mac: String) {
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/kasmok/tv-registry/status?mac=${mac}")
            .addHeader("x-token", Config.TOKEN)
            .get()
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Ignorer — on réessaie dans 10s
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val data = JSONObject(body)
                    val status = data.optString("status", "PENDING")
                    val tvId = data.optString("tv_id", "")

                    if (status == "ACTIVE") {
                        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString(Config.KEY_TV_ID, tvId)
                            .putString(Config.KEY_TV_STATUS, "ACTIVE")
                            .apply()

                        mainHandler.post {
                            stopPolling()
                            startActivity(Intent(this@PendingActivity, MainActivity::class.java))
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    // Ignorer
                }
            }
        })
    }

    private fun stopPolling() {
        pollingRunnable?.let { pollingHandler.removeCallbacks(it) }
        pollingRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }
}