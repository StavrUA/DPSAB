package ua.stavr.dpsab

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class CircleAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var controlPanel: View? = null
    private var drawingInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showControlPanel()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controlPanel?.let(windowManager::removeView)
        controlPanel = null
        super.onDestroy()
    }

    private fun showControlPanel() {
        if (controlPanel != null) return

        val density = resources.displayMetrics.density
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14, density), dp(8, density), dp(8, density), dp(8, density))
            background = roundedBackground(0xF2192033.toInt(), dp(20, density))
            elevation = dp(8, density).toFloat()
            contentDescription = "Плаваюча панель Рівне коло"
        }
        val title = TextView(this).apply {
            text = "МАЛЮВАТИ"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8, density), 0)
        }
        val circleButton = createPanelButton("Коло", density, 0xFF635BFF.toInt()) {
            drawCircle()
        }
        val textButton = createPanelButton("Текст", density, 0xFF16855B.toInt()) {
            drawText()
        }
        panel.addView(title)
        panel.addView(circleButton)
        panel.addView(textButton)
        panel.setOnTouchListener(PanelDragListener(panel, density, windowManager))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72, density)
        }

        controlPanel = panel
        windowManager.addView(panel, params)
    }

    private fun createPanelButton(
        label: String,
        density: Float,
        color: Int,
        onClick: () -> Unit
    ) = TextView(this).apply {
        text = label
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(12, density), dp(11, density), dp(12, density), dp(11, density))
        background = roundedBackground(color, dp(14, density))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun drawCircle() {
        if (drawingInProgress) {
            showMessage("Дочекайся завершення поточного малювання")
            return
        }
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val diameterDp = preferences.getInt(
            MainActivity.KEY_DIAMETER_DP,
            MainActivity.DEFAULT_DIAMETER_DP
        )
        val durationMs = preferences.getInt(
            MainActivity.KEY_SPEED_MS,
            MainActivity.NORMAL_SPEED_MS
        )
        val radius = minOf(
            diameterDp * resources.displayMetrics.density / 2f,
            minOf(bounds.width(), bounds.height()) * 0.42f
        )
        val path = Path().apply {
            addCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, Path.Direction.CW)
        }
        dispatchPath(path, durationMs.toLong(), "Коло намальовано")
    }

    private fun drawText() {
        if (drawingInProgress) {
            showMessage("Дочекайся завершення поточного малювання")
            return
        }

        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val text = preferences.getString(MainActivity.KEY_TEXT, "").orEmpty().trim().take(MAX_TEXT_LENGTH)
        if (text.isEmpty()) {
            showMessage("Спочатку введи текст у застосунку")
            return
        }

        val metrics = windowManager.currentWindowMetrics
        val screen = metrics.bounds
        val density = resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = preferences.getInt(
                MainActivity.KEY_TEXT_SIZE_DP,
                MainActivity.DEFAULT_TEXT_SIZE_DP
            ) * density
            style = Paint.Style.FILL
        }
        val textPath = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, textPath)
        val textBounds = RectF()
        textPath.computeBounds(textBounds, true)
        if (textBounds.isEmpty) {
            showMessage("Цей текст не містить символів для малювання")
            return
        }

        val scale = minOf(
            1f,
            screen.width() * MAX_TEXT_SCREEN_FRACTION / textBounds.width(),
            screen.height() * MAX_TEXT_SCREEN_FRACTION / textBounds.height()
        )
        textPath.offset(-textBounds.centerX(), -textBounds.centerY())
        textPath.transform(Matrix().apply { setScale(scale, scale) })
        textPath.offset(screen.exactCenterX(), screen.exactCenterY())

        val strokes = extractContours(textPath)
        if (strokes.isEmpty()) {
            showMessage("Не вдалося створити контури цього тексту")
            return
        }
        val totalDuration = preferences.getInt(
            MainActivity.KEY_SPEED_MS,
            MainActivity.NORMAL_SPEED_MS
        ).toLong() * text.length / 3
        val strokeDuration = (totalDuration / strokes.size).coerceIn(MIN_STROKE_DURATION_MS, MAX_GESTURE_DURATION_MS)
        dispatchTextStroke(strokes, 0, strokeDuration)
    }

    private fun extractContours(path: Path): List<Path> {
        val strokes = mutableListOf<Path>()
        val measure = PathMeasure(path, false)
        do {
            val length = measure.length
            if (length >= MIN_CONTOUR_LENGTH_PX) {
                val stroke = Path()
                if (measure.getSegment(0f, length, stroke, true)) {
                    strokes += stroke
                }
            }
        } while (measure.nextContour())
        return strokes
    }

    private fun dispatchTextStroke(strokes: List<Path>, index: Int, durationMs: Long) {
        if (index >= strokes.size) {
            drawingInProgress = false
            showMessage("Текст намальовано")
            return
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(strokes[index], 0, durationMs))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                dispatchTextStroke(strokes, index + 1, durationMs)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                drawingInProgress = false
                showMessage("Малювання тексту перервано системою або грою")
            }
        }, null)
        if (!accepted) {
            drawingInProgress = false
            showMessage("Не вдалося продовжити малювання тексту")
        } else if (index == 0) {
            drawingInProgress = true
        }
    }

    private fun dispatchPath(path: Path, durationMs: Long, successMessage: String) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                drawingInProgress = false
                showMessage(successMessage)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                drawingInProgress = false
                showMessage("Жест перервано системою або грою")
            }
        }, null)
        if (accepted) {
            drawingInProgress = true
        } else {
            showMessage("Не вдалося запустити жест")
        }
    }

    private fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun roundedBackground(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    private fun dp(value: Int, density: Float) = (value * density).toInt()

    private class PanelDragListener(
        private val panel: View,
        private val density: Float,
        private val windowManager: WindowManager
    ) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private val touchSlop = 8 * density

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = panel.layoutParams as? WindowManager.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - downRawX).toInt()
                    val deltaY = (event.rawY - downRawY).toInt()
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + deltaX
                        params.y = startY + deltaY
                        windowManager.updateViewLayout(panel, params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        panel.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        const val PREFERENCES_NAME = "circle_settings"
        private const val MAX_TEXT_LENGTH = 20
        private const val MAX_TEXT_SCREEN_FRACTION = 0.8f
        private const val MIN_CONTOUR_LENGTH_PX = 1f
        private const val MIN_STROKE_DURATION_MS = 50L
        private const val MAX_GESTURE_DURATION_MS = 60_000L
    }
}
