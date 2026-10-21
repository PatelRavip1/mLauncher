package com.github.codeworkscreativehub.mlauncher.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.View

class WeekDashesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var filled: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var activeColor: Int = Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var trackColor: Int = Color.GRAY
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var dashWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var dashGap: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var alignGravity: Int = Gravity.START
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var gravity: Int
        get() = alignGravity
        set(value) {
            alignGravity = value
        }

    fun setProgress(
        filled: Int,
        active: Int,
        track: Int,
        dashWidth: Float,
        dashGap: Float,
        alignGravity: Int
    ) {
        this.filled = filled
        this.activeColor = active
        this.trackColor = track
        this.dashWidth = dashWidth
        this.dashGap = dashGap
        this.alignGravity = alignGravity
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dashWidth <= 0f) return

        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val h = bottom - top
        if (h <= 0f) return

        val cornerRadius = h / 2f
        val totalWidth = 7 * dashWidth + 6 * dashGap
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val horizontalGravity = Gravity.getAbsoluteGravity(alignGravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK

        val startX = when (horizontalGravity) {
            Gravity.RIGHT -> (width - paddingRight).toFloat() - totalWidth
            Gravity.CENTER_HORIZONTAL -> paddingLeft.toFloat() + (availableWidth - totalWidth) / 2f
            else -> paddingLeft.toFloat()
        }

        for (i in 0 until 7) {
            val left = startX + i * (dashWidth + dashGap)
            val right = left + dashWidth
            paint.color = if (i < filled) activeColor else trackColor
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
        }
    }
}
