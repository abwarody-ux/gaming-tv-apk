package com.gamingtv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class QrCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var qrBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 229, 255)
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    private var url: String = ""
    private var ticketNumber: String = ""

    fun setQrData(url: String, ticketNumber: String) {
        this.url = url
        this.ticketNumber = ticketNumber
        generateQrCode(url)
        invalidate()
    }

    private fun generateQrCode(content: String) {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.parseColor("#00E5FF")
                        else Color.TRANSPARENT
                    )
                }
            }
            qrBitmap = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (url.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 16f
        val qrSize = minOf(w, h) - padding * 2 - 60f

        // Background semi-transparent
        val bgRect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)

        // Border néon
        val borderRect = RectF(2f, 2f, w - 2f, h - 2f)
        canvas.drawRoundRect(borderRect, 14f, 14f, borderPaint)

        // Title
        canvas.drawText("⏱ AJOUTER DU TEMPS", w / 2, padding + 28f, textPaint)

        // QR Code
        qrBitmap?.let { bmp ->
            val qrLeft = (w - qrSize) / 2
            val qrTop = padding + 44f
            val qrRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
            canvas.drawBitmap(bmp, null, qrRect, paint)
        }

        // Ticket number
        canvas.drawText(ticketNumber, w / 2, h - 20f, subTextPaint)
    }
}