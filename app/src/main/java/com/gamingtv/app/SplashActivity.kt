package com.gamingtv.app

import android.animation.*
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_splash)

        // ── SON PS2 AU DÉMARRAGE ──
        SoundManager.playPS2Intro(this)

        val logoContainer = findViewById<View>(R.id.logoContainer)
        val textGaming = findViewById<TextView>(R.id.textGaming)
        val textTV = findViewById<TextView>(R.id.textTV)
        val textTagline = findViewById<TextView>(R.id.textTagline)
        val cornerTL = findViewById<View>(R.id.cornerTL)
        val cornerTR = findViewById<View>(R.id.cornerTR)
        val cornerBL = findViewById<View>(R.id.cornerBL)
        val cornerBR = findViewById<View>(R.id.cornerBR)

        // Appliquer le stroke néon sur GAMING et TV
        applyNeonStroke(textGaming, "#00E5FF")
        applyNeonStroke(textTV, "#00E5FF")

        // ── SÉQUENCE D'ANIMATION ──

        // 0ms: Coins apparaissent
        animateCorners(cornerTL, cornerTR, cornerBL, cornerBR)

        // 300ms: Logo container visible
        Handler(Looper.getMainLooper()).postDelayed({
            logoContainer.alpha = 1f

            // GAMING — slide depuis le haut + fade
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

            // TV — slide depuis le bas + fade (décalé 200ms)
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

                // Effet GLITCH après apparition TV
                Handler(Looper.getMainLooper()).postDelayed({
                    startGlitchLoop(textGaming, textTV)
                }, 900)

                // Tagline fade in
                Handler(Looper.getMainLooper()).postDelayed({
                    textTagline.animate()
                        .alpha(1f)
                        .setDuration(800)
                        .start()
                }, 1200)

            }, 200)

        }, 300)

        // 4500ms: Pulse final + transition vers MainActivity
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
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            })
            pulseAnim.start()
        }, 4500)
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