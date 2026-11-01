package ua.stavr.dpsab

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val preferences by lazy {
        getSharedPreferences(CircleAccessibilityService.PREFERENCES_NAME, MODE_PRIVATE)
    }

    private lateinit var diameterSlider: SeekBar
    private lateinit var diameterValue: TextView
    private lateinit var previewDiameter: TextView
    private lateinit var preview: CirclePreviewView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var accessibilityButton: TextView
    private lateinit var speedSlow: TextView
    private lateinit var speedNormal: TextView
    private lateinit var speedFast: TextView
    private var selectedSpeed = NORMAL_SPEED_MS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        diameterSlider = findViewById(R.id.diameterSlider)
        diameterValue = findViewById(R.id.diameterValue)
        previewDiameter = findViewById(R.id.previewDiameter)
        preview = findViewById(R.id.circlePreview)
        status = findViewById(R.id.serviceStatus)
        hint = findViewById(R.id.serviceHint)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        speedSlow = findViewById(R.id.speedSlow)
        speedNormal = findViewById(R.id.speedNormal)
        speedFast = findViewById(R.id.speedFast)

        val savedDiameter = preferences.getInt(KEY_DIAMETER_DP, DEFAULT_DIAMETER_DP)
            .coerceIn(MIN_DIAMETER_DP, MAX_DIAMETER_DP)
        diameterSlider.progress = savedDiameter - MIN_DIAMETER_DP
        updateDiameter(savedDiameter)

        val savedSpeed = preferences.getInt(KEY_SPEED_MS, NORMAL_SPEED_MS)
        selectedSpeed = when (savedSpeed) {
            SLOW_SPEED_MS, NORMAL_SPEED_MS, FAST_SPEED_MS -> savedSpeed
            else -> NORMAL_SPEED_MS
        }
        if (selectedSpeed != savedSpeed) {
            preferences.edit().putInt(KEY_SPEED_MS, selectedSpeed).apply()
        }
        updateSpeedSelection()

        diameterSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDiameter(MIN_DIAMETER_DP + progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        speedSlow.setOnClickListener { selectSpeed(SLOW_SPEED_MS) }
        speedNormal.setOnClickListener { selectSpeed(NORMAL_SPEED_MS) }
        speedFast.setOnClickListener { selectSpeed(FAST_SPEED_MS) }

        accessibilityButton.setOnClickListener {
            if (isCircleServiceEnabled()) {
                Toast.makeText(this, "Плаваюча панель уже доступна поверх екрана", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateDiameter(diameterDp: Int) {
        diameterValue.text = "$diameterDp dp"
        previewDiameter.text = "$diameterDp dp · ідеальна окружність"
        preview.diameterDp = diameterDp
        preferences.edit().putInt(KEY_DIAMETER_DP, diameterDp).apply()
    }

    private fun selectSpeed(speedMs: Int) {
        selectedSpeed = speedMs
        preferences.edit().putInt(KEY_SPEED_MS, speedMs).apply()
        updateSpeedSelection()
    }

    private fun updateSpeedSelection() {
        setSpeedChip(speedSlow, selectedSpeed == SLOW_SPEED_MS)
        setSpeedChip(speedNormal, selectedSpeed == NORMAL_SPEED_MS)
        setSpeedChip(speedFast, selectedSpeed == FAST_SPEED_MS)
    }

    private fun setSpeedChip(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(if (selected) R.drawable.chip_selected else R.drawable.chip_unselected)
        chip.setTextColor(getColor(if (selected) R.color.surface else R.color.ink))
        chip.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun updateServiceStatus() {
        val enabled = isCircleServiceEnabled()
        status.text = if (enabled) "Сервіс увімкнено" else "Сервіс вимкнено"
        hint.text = if (enabled) {
            "Плаваюча панель готова до роботи"
        } else {
            "Потрібно дозволити керування жестами"
        }
        accessibilityButton.text = if (enabled) "Панель активна" else "Увімкнути керування"
    }

    private fun isCircleServiceEnabled(): Boolean {
        val expected = ComponentName(this, CircleAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':').any { service ->
            service.equals(expected.flattenToString(), ignoreCase = true) ||
                service.equals(expected.flattenToShortString(), ignoreCase = true)
        }
    }

    companion object {
        const val KEY_DIAMETER_DP = "diameter_dp"
        const val KEY_SPEED_MS = "speed_ms"
        const val MIN_DIAMETER_DP = 100
        const val MAX_DIAMETER_DP = 700
        const val DEFAULT_DIAMETER_DP = 250
        const val SLOW_SPEED_MS = 2400
        const val NORMAL_SPEED_MS = 1400
        const val FAST_SPEED_MS = 800
    }
}
