package com.gamingtv.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class QrScannerActivity : AppCompatActivity() {

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

        setContentView(R.layout.activity_qr_scanner)

        val tokenInput = findViewById<EditText>(R.id.tokenInput)
        val validateBtn = findViewById<Button>(R.id.validateBtn)
        val cancelBtn = findViewById<Button>(R.id.cancelBtn)
        val statusText = findViewById<TextView>(R.id.statusText)

        validateBtn.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isEmpty()) {
                statusText.text = "Veuillez entrer le code d'activation."
                statusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            activateWithToken(token, statusText)
        }

        cancelBtn.setOnClickListener { finish() }
    }

    private fun activateWithToken(token: String, statusText: TextView) {
        val json = JSONObject().apply { put("token", token) }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${Config.BACKEND_URL}/kasmok/tv-registry/activate-qr")
            .addHeader("x-token", Config.TOKEN)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    statusText.text = "Erreur réseau. Vérifiez votre connexion."
                    statusText.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                mainHandler.post {
                    if (response.isSuccessful) {
                        try {
                            val data = JSONObject(bodyStr)
                            val tvId = data.optString("tv_id", "")
                            val status = data.optString("status", "")
                            val tvToken = data.optString("tv_token", "")

                            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString(Config.KEY_TV_ID, tvId)
                                .putString(Config.KEY_TV_STATUS, status)
                                .putString(Config.KEY_TV_TOKEN, tvToken)
                                .apply()

                            Toast.makeText(this@QrScannerActivity, "TV activée ✅", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this@QrScannerActivity, MainActivity::class.java))
                            finish()
                        } catch (e: Exception) {
                            statusText.text = "Erreur de traitement."
                            statusText.visibility = View.VISIBLE
                        }
                    } else {
                        statusText.text = "Code invalide ou expiré. Demandez un nouveau code."
                        statusText.visibility = View.VISIBLE
                    }
                }
            }
        })
    }
}