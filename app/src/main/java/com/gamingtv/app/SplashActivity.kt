package com.gamingtv.app

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SplashActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_splash)

        SoundManager.playPS2Intro(this)

        val logoContainer = findViewById<View>(R.id.logoContainer)
        val textGaming = findViewById<TextView>(R.id.textGaming)
        val textTV = findViewById<TextView>(R.id.textTV)
        val textTagline = findViewById<TextView>(R.id.textTagline)
        val cornerTL = findViewById<View>(R.id.cornerTL)
        val cornerTR = findViewById<View>(R.id.cornerTR)
        val cornerBL = findViewById<View>(R.id.cornerBL)
        val cornerBR = findViewById<View>(R.id.cornerBR)

        applyNeonStroke(textGaming, "#00E5FF")
        applyNeonStroke(textTV, "#00E5FF")

        animateCorners(cornerTL, cornerTR, cornerBL, cornerBR)

        Handler(Looper.getMainLooper()).postDelayed({
            logoContainer.alpha = 1f

            textGaming.translationY = -200f
            textGaming.alpha = 0f
            val gamingAnim = AnimatorSet()
            gamingAnim.playTogether(
                ObjectAnimator.ofFloat(textGaming, "translationY", -200f, 0f),
                ObjectAnimator.ofFloat(textGaming, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(textGaming, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(textGaming, "scaleY", 0.5f, 1f)
            )
            gamingAnim.duration = 800
            gamingAnim.interpolator = DecelerateInterpolator(2f)
            gamingAnim.start()

            Handler(Looper.getMainLooper()).postDelayed({
                textTV.translationY = 200f
                textTV.alpha = 0f
                val tvAnim = AnimatorSet()
                tvAnim.playTogether(
                    ObjectAnimator.ofFloat(textTV, "translationY", 200f, 0f),
                    ObjectAnimator.ofFloat(textTV, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(textTV, "scaleX", 0.5f, 1f),
                    ObjectAnimator.ofFloat(textTV, "scaleY", 0.5f, 1f)
                )
                tvAnim.duration = 800
                tvAnim.interpolator = DecelerateInterpolator(2f)
                tvAnim.start()

                Handler(Looper.getMainLooper()).postDelayed({
                    startGlitchLoop(textGaming, textTV)
                }, 900)

                Handler(Looper.getMainLooper()).postDelayed({
                    textTagline.animate().alpha(1f).setDuration(800).start()
                }, 1200)

            }, 200)
        }, 300)

        // Après l'animation → vérifier enregistrement TV
        Handler(Looper.getMainLooper()).postDelayed({
            val pulseAnim = AnimatorSet()
            pulseAnim.playTogether(
                ObjectAnimator.ofFloat(logoContainer, "scaleX", 1f, 1.1f, 0f),
                ObjectAnimator.ofFloat(logoContainer, "scaleY", 1f, 1.1f, 0f),
                ObjectAnimator.ofFloat(logoContainer, "alpha", 1f, 1f, 0f)
            )
            pulseAnim.duration = 600
            pulseAnim.interpolator = AccelerateInterpolator()
            pulseAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    checkTvRegistration()
                }
            })
            pulseAnim.start()
        }, 4500)
    }

    private fun checkTvRegistration() {
        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        val savedTvId = prefs.getString(Config.KEY_TV_ID, null)
        val savedStatus = prefs.getString(Config.KEY_TV_STATUS, null)

        if (savedTvId != null && savedStatus == "ACTIVE") {
            // TV déjà enregistrée et active → aller directement à MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } else if (savedTvId != null && savedStatus == "PENDING") {
            // TV en attente → aller à PendingActivity
            startActivity(Intent(this, PendingActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } else {
            // Première connexion → enregistrer la TV
            registerTv()
        }
    }

    private fun registerTv() {
        val mac = getMacAddress()
        val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(Config.KEY_MAC_ADDRESS, mac).apply()

        val json = JSONObject().apply {
            put("mac_address", mac)
            put("gps_lat", 0.0)
            put("gps_lng", 0.0)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/kasmok/tv-registry/register")
            .addHeader("x-token", Config.TOKEN)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Réseau indisponible → aller en PENDING local
                mainHandler.post {
                    val p = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                    p.edit().putString(Config.KEY_TV_STATUS, "PENDING").apply()
                    startActivity(Intent(this@SplashActivity, PendingActivity::class.java))
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val data = JSONObject(body)
                    val tvId = data.optString("tv_id", "")
                    val status = data.optString("status", "PENDING")

                    val p = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                    p.edit()
                        .putString(Config.KEY_TV_ID, tvId)
                        .putString(Config.KEY_TV_STATUS, status)
                        .apply()

                    mainHandler.post {
                        if (status == "ACTIVE") {
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this@SplashActivity, PendingActivity::class.java))
                        }
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        startActivity(Intent(this@SplashActivity, PendingActivity::class.java))
                        finish()
                    }
                }
            }
        })
    }

    private fun getMacAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.macAddress ?: "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }

    private fun applyNeonStroke(tv: TextView, hexColor: String) {
        tv.paint.style = Paint.Style.STROKE
        tv.paint.strokeWidth = 4f
        tv.setTextColor(Color.parseColor(hexColor))
        tv.setShadowLayer(60f, 0f, 0f, Color.parseColor(hexColor))
    }

    private fun animateCorners(vararg corners: View) {
        corners.forEachIndexed { i, corner ->
            Handler(Looper.getMainLooper()).postDelayed({
                corner.animate().alpha(1f).setDuration(400).start()
            }, i * 80L)
        }
    }

    private fun startGlitchLoop(vararg views: View) {
        val handler = Handler(Looper.getMainLooper())
        val glitch = object : Runnable {
            override fun run() {
                views.forEach { v ->
                    v.translationX = (-3..3).random().toFloat()
                    v.translationY = (-2..2).random().toFloat()
                }
                handler.postDelayed({
                    views.forEach { v ->
                        v.translationX = 0f
                        v.translationY = 0f
                    }
                }, 60)
                handler.postDelayed(this, (2000..4000).random().toLong())
            }
        }
        handler.postDelayed(glitch, 500)
    }
}