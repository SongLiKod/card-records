package com.cardrecords.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.cardrecords.CardRecordsApp
import com.cardrecords.R
import com.cardrecords.model.GameConfig

class SettingsActivity : AppCompatActivity() {

    private lateinit var spinnerGameType: Spinner
    private lateinit var numberPickerLevel: NumberPicker
    private lateinit var seekBarTransparency: SeekBar
    private lateinit var tvTransparencyValue: TextView
    private lateinit var switchPassThrough: SwitchCompat
    private lateinit var switchAutoCapture: SwitchCompat
    private lateinit var btnSave: Button

    private val config get() = CardRecordsApp.instance.gameConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        spinnerGameType = findViewById(R.id.spinner_game_type)
        numberPickerLevel = findViewById(R.id.number_picker_level)
        seekBarTransparency = findViewById(R.id.seekbar_transparency)
        tvTransparencyValue = findViewById(R.id.tv_transparency_value)
        switchPassThrough = findViewById(R.id.switch_pass_through)
        switchAutoCapture = findViewById(R.id.switch_auto_capture)
        btnSave = findViewById(R.id.btn_save)

        setupGameTypeSpinner()
        setupNumberPicker()
        setupTransparencySeekBar()
        loadCurrentConfig()

        btnSave.setOnClickListener { saveConfig() }
    }

    private fun setupGameTypeSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.game_types, android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerGameType.adapter = adapter
    }

    private fun setupNumberPicker() {
        val levelLabels = (2..14).map {
            when (it) {
                11 -> "J"
                12 -> "Q"
                13 -> "K"
                14 -> "A"
                else -> it.toString()
            }
        }.toTypedArray()

        numberPickerLevel.apply {
            minValue = 0
            maxValue = levelLabels.size - 1
            displayedValues = levelLabels
            wrapSelectorWheel = false
        }
    }

    private fun setupTransparencySeekBar() {
        seekBarTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress.coerceIn(10, 90)
                tvTransparencyValue.text = "$pct%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadCurrentConfig() {
        spinnerGameType.setSelection(
            when (config.numberOfDecks) {
                1 -> 0; 2 -> 1; 3 -> 2; 4 -> 3; else -> 1
            }
        )
        numberPickerLevel.value = (config.currentLevel - 2).coerceIn(0, 12)
        seekBarTransparency.progress = config.transparencyPercent.coerceIn(10, 90)
        tvTransparencyValue.text = "${config.transparencyPercent}%"
        switchPassThrough.isChecked = config.passThrough
        switchAutoCapture.isChecked = config.autoCapture
    }

    private fun saveConfig() {
        val decks = when (spinnerGameType.selectedItemPosition) {
            0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 2
        }
        val levelValues = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        val level = levelValues[numberPickerLevel.value]
        val transparency = seekBarTransparency.progress.coerceIn(10, 90)
        val passThrough = switchPassThrough.isChecked
        val autoCapture = switchAutoCapture.isChecked

        val playerCount = when (decks) {
            1, 2 -> 4; 3 -> 6; 4 -> 8; else -> 4
        }

        val newConfig = GameConfig(
            numberOfDecks = decks,
            currentLevel = level,
            playerCount = playerCount,
            transparencyPercent = transparency,
            passThrough = passThrough,
            autoCapture = autoCapture
        )

        CardRecordsApp.instance.updateConfig(newConfig)
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
