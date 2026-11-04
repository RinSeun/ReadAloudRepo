package com.lss.lotusaloud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class SpinRing(context: Context, private val strokePx: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        color = Color.TRANSPARENT
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    private val box = RectF()
    private var spinning = false
    private var angle = 0f
    private var lastFrame = 0L

    val idle get() = !spinning && paint.color == Color.TRANSPARENT

    fun spin(color: Int) {
        paint.color = color
        track.color = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))
        if (!spinning) {
            spinning = true
            lastFrame = 0L
        }
        postInvalidateOnAnimation()
    }

    fun solid(color: Int) {
        spinning = false
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokePx / 2 + 1
        box.set(inset, inset, getWidth() - inset, getHeight() - inset)
        if (spinning) {
            val now = System.nanoTime()
            if (lastFrame != 0L) angle = (angle + (now - lastFrame) / 1_000_000f * 0.36f) % 360f
            lastFrame = now
            canvas.drawOval(box, track)
            canvas.drawArc(box, angle, 100f, false, paint)
            postInvalidateOnAnimation()
        } else if (paint.color != Color.TRANSPARENT) {
            canvas.drawOval(box, paint)
        }
    }
}
