package ua.stavr.dpsab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class CirclePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var diameterDp: Int = MainActivity.DEFAULT_DIAMETER_DP
        set(value) {
            field = value
            invalidate()
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.line)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(5f)), 0f)
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = minOf(width, height) * 0.39f
        val radius = minOf(dp(diameterDp.toFloat()) / 2f, maxRadius)
        canvas.drawCircle(centerX, centerY, maxRadius, guidePaint)
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, dp(3f), centerPaint)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
