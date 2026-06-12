package com.cardrecords.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cardrecords.CardRecordsApp
import com.cardrecords.R
import com.cardrecords.model.*
import com.cardrecords.overlay.OverlayService
import com.cardrecords.recognition.ScreenCaptureService

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartOverlay: Button
    private lateinit var btnStartCapture: Button
    private lateinit var btnManualAdd: Button
    private lateinit var btnReset: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView

    private var isCaptureActive = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            startOverlayService()
        } else {
            Toast.makeText(this@MainActivity, "Overlay permission required", Toast.LENGTH_LONG).show()
        }
    }

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this@MainActivity, "Screen capture permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tv_game_status)
        btnStartOverlay = findViewById(R.id.btn_start_overlay)
        btnStartCapture = findViewById(R.id.btn_start_capture)
        btnManualAdd = findViewById(R.id.btn_manual_add)
        btnReset = findViewById(R.id.btn_reset)
        btnSettings = findViewById(R.id.btn_settings)
        btnStartOverlay.setOnClickListener { onOverlayToggle() }
        btnStartCapture.setOnClickListener { onCaptureToggle() }
        btnManualAdd.setOnClickListener { showManualAddDialog() }
        btnReset.setOnClickListener { showResetConfirmDialog() }
        btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val app = CardRecordsApp.instance
        val config = app.gameConfig
        val tracker = app.cardTracker
        val overlayRunning = OverlayService.isRunning
        val deckLabel = when (config.numberOfDecks) {
            1 -> "1 deck (4p)"; 2 -> "2 decks (4p)"
            3 -> "3 decks (6p)"; 4 -> "4 decks (8p)"
            else -> "2 decks (4p)"
        }
        tvStatus.text = buildString {
            append("Game: $deckLabel | Level: ${displayLevel(config.currentLevel)}\n")
            append("Played: ${tracker.playedCards.size} | Left: ${tracker.getRemainingCards().size}\n")
            append("Score left: ${tracker.getRemainingScoreValue()} / ${config.totalScore}")
        }
        btnStartOverlay.text = if (overlayRunning) "Stop Overlay" else "Start Overlay"
        btnStartCapture.visibility = if (overlayRunning) View.VISIBLE else View.GONE
        btnStartCapture.text = if (isCaptureActive) "Stop Capture" else "Start Capture"
    }

    private fun displayLevel(value: Int): String = when (value) {
        11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"
        else -> value.toString()
    }

    private fun onOverlayToggle() {
        if (OverlayService.isRunning) {
            stopService(Intent(this@MainActivity, OverlayService::class.java))
        } else {
            if (!checkOverlayPermission()) { requestOverlayPermission(); return }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                    return
                }
            }
            startOverlayService()
        }
        updateUI()
    }

    private fun checkOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this@MainActivity)
        else true

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startService(intent)
        Toast.makeText(this@MainActivity, "Overlay started", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun onCaptureToggle() {
        if (isCaptureActive) {
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            })
            isCaptureActive = false
        } else {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            capturePermissionLauncher.launch(manager.createScreenCaptureIntent())
        }
        updateUI()
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isCaptureActive = true
        Toast.makeText(this@MainActivity, "Capture started", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Reset Game")
            .setMessage("Reset all card records?")
            .setPositiveButton("Reset") { _, _ ->
                CardRecordsApp.instance.resetGame()
                if (OverlayService.isRunning) {
                    startService(Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_RESET
                    })
                }
                Toast.makeText(this@MainActivity, "Game reset", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualAddDialog() {
        val config = CardRecordsApp.instance.gameConfig
        val suits = Suit.nonJokerSuits()
        val ranks = Rank.entries.filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }
        val suitLabels = suits.map { "${it.symbol} ${it.displayName}" }.toTypedArray()
        val rankLabels = ranks.map { it.display }.toTypedArray()

        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Manual Card Entry")
        val layout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(60, 30, 60, 30)
        }

        layout.addView(TextView(this@MainActivity).apply { text = "Suit:"; setTextColor(-0x1); textSize = 15f })
        val suitSpinner = Spinner(this@MainActivity)
        ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, suitLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); suitSpinner.adapter = it
        }
        layout.addView(suitSpinner)

        layout.addView(TextView(this@MainActivity).apply { text = "Rank:"; setTextColor(-0x1); textSize = 15f; setPadding(0,12,0,4) })
        val rankSpinner = Spinner(this@MainActivity)
        ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, rankLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); rankSpinner.adapter = it
        }
        layout.addView(rankSpinner)

        val jokerCheck = CheckBox(this@MainActivity).apply { text = "Joker"; setTextColor(-0x1) }
        layout.addView(jokerCheck)
        layout.addView(TextView(this@MainActivity).apply { text = "Joker type:"; setTextColor(-0x1); textSize = 15f; setPadding(0,12,0,4) })
        val jokerTypeSpinner = Spinner(this@MainActivity)
        ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, arrayOf("Small", "Big")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); jokerTypeSpinner.adapter = it
        }
        jokerTypeSpinner.visibility = View.GONE
        layout.addView(jokerTypeSpinner)

        jokerCheck.setOnCheckedChangeListener { _, isChecked ->
            suitSpinner.isEnabled = !isChecked; rankSpinner.isEnabled = !isChecked
            jokerTypeSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        layout.addView(TextView(this@MainActivity).apply { text = "Player:"; setTextColor(-0x1); textSize = 15f; setPadding(0,12,0,4) })
        val playerSpinner = Spinner(this@MainActivity)
        val playerLabels = PlayerPosition.getLabels(config.playerCount).toTypedArray()
        ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, playerLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); playerSpinner.adapter = it
        }
        layout.addView(playerSpinner)

        layout.addView(TextView(this@MainActivity).apply { text = "Qty:"; setTextColor(-0x1); textSize = 15f; setPadding(0,12,0,4) })
        val countPicker = NumberPicker(this@MainActivity).apply {
            minValue = 1; maxValue = config.numberOfDecks.coerceAtMost(4); wrapSelectorWheel = false
            value = 1
        }
        layout.addView(countPicker)

        builder.setView(layout)
        builder.setPositiveButton("Add") { _, _ ->
            val isJoker = jokerCheck.isChecked
            val card = if (isJoker) Card(Suit.JOKER, if (jokerTypeSpinner.selectedItemPosition == 0) Rank.SMALL_JOKER else Rank.BIG_JOKER)
                       else Card(suits[suitSpinner.selectedItemPosition], ranks[rankSpinner.selectedItemPosition])
            val count = countPicker.value
            val tracker = CardRecordsApp.instance.cardTracker
            repeat(count) { tracker.recordPlayedCard(card, playerSpinner.selectedItemPosition) }
            tracker.finishRound()
            if (OverlayService.isRunning) {
                startService(Intent(this@MainActivity, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UPDATE
                })
            }
            Toast.makeText(this@MainActivity, "Added ${card.displayName} x $count", Toast.LENGTH_SHORT).show()
            updateUI()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}