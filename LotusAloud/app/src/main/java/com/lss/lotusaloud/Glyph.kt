package com.lss.lotusaloud

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class Glyph(private val kind: Kind, color: Int, private val size: Float) : Drawable() {
    enum class Kind { PLAY, PAUSE, PREV, NEXT, CLOSE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = size * 0.13f
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val h = size / 2f
        when (kind) {
            Kind.PLAY -> canvas.drawPath(triangle(cx - h * 0.72f, cy, h * 1.75f, h, right = true), paint)
            Kind.PAUSE -> {
                val w = size * 0.27f
                val r = w * 0.25f
                canvas.drawRoundRect(RectF(cx - h * 0.78f, cy - h, cx - h * 0.78f + w, cy + h), r, r, paint)
                canvas.drawRoundRect(RectF(cx + h * 0.78f - w, cy - h, cx + h * 0.78f, cy + h), r, r, paint)
            }
            Kind.NEXT -> {
                canvas.drawPath(triangle(cx - h * 0.85f, cy, h * 1.45f, h * 0.85f, right = true), paint)
                canvas.drawRect(cx + h * 0.62f, cy - h * 0.85f, cx + h * 0.85f, cy + h * 0.85f, paint)
            }
            Kind.PREV -> {
                canvas.drawPath(triangle(cx + h * 0.85f, cy, h * 1.45f, h * 0.85f, right = false), paint)
                canvas.drawRect(cx - h * 0.85f, cy - h * 0.85f, cx - h * 0.62f, cy + h * 0.85f, paint)
            }
            Kind.CLOSE -> {
                val d = h * 0.7f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, stroke)
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, stroke)
            }
        }
    }

    private fun triangle(x: Float, cy: Float, len: Float, half: Float, right: Boolean) = Path().apply {
        val tip = if (right) x + len else x - len
        moveTo(x, cy - half)
        lineTo(tip, cy)
        lineTo(x, cy + half)
        close()
    }

    override fun getIntrinsicWidth() = (size * 1.4f).toInt()
    override fun getIntrinsicHeight() = (size * 1.4f).toInt()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; stroke.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; stroke.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
