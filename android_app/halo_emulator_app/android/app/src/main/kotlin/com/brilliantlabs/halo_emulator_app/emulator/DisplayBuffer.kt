package com.brilliantlabs.halo_emulator_app.emulator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

class DisplayBuffer {
    private val width = 256
    private val height = 256
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun clear(color: Int) {
        canvas.drawColor(color or 0xFF000000.toInt())
    }

    fun text(text: String, x: Int, y: Int, color: Int) {
        paint.color = color or 0xFF000000.toInt()
        paint.textSize = 16f
        canvas.drawText(text, (x - 1).toFloat(), (y - 1 + 16).toFloat(), paint)
    }

    fun setPixel(x: Int, y: Int, color: Int) {
        bitmap.setPixel(x - 1, y - 1, color or 0xFF000000.toInt())
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        paint.color = color or 0xFF000000.toInt()
        paint.strokeWidth = 1f
        canvas.drawLine((x0 - 1).toFloat(), (y0 - 1).toFloat(), (x1 - 1).toFloat(), (y1 - 1).toFloat(), paint)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int, filled: Boolean) {
        paint.color = color or 0xFF000000.toInt()
        val left = x - 1
        val top = y - 1
        val rect = Rect(left, top, left + w - 1, top + h - 1)
        if (filled) canvas.drawRect(rect, paint) else canvas.drawRect(rect, paint.apply { style = Paint.Style.STROKE })
        paint.style = Paint.Style.FILL
    }

    fun circle(cx: Int, cy: Int, r: Int, color: Int, filled: Boolean) {
        paint.color = color or 0xFF000000.toInt()
        paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        canvas.drawCircle((cx - 1).toFloat(), (cy - 1).toFloat(), r.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    fun show() {}

    fun width(): Int = width
    fun height(): Int = height

    fun toPng(): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
