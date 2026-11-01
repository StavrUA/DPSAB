package ua.stavr.dpsab

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
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
            text = "◯  КОЛО"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(10, density), 0)
        }
        val drawButton = TextView(this).apply {
            text = "Малювати"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16, density), dp(11, density), dp(16, density), dp(11, density))
            background = roundedBackground(0xFF635BFF.toInt(), dp(14, density))
            isClickable = true
            isFocusable = true
            setOnClickListener { drawCircle() }
        }
        panel.addView(title)
        panel.addView(drawButton)
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

    private fun drawCircle() {
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
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.toLong()))
            .build()

        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                showMessage("Коло намальовано")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                showMessage("Жест перервано системою або грою")
            }
        }, null)
        if (!accepted) showMessage("Не вдалося запустити жест")
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
    }
}
