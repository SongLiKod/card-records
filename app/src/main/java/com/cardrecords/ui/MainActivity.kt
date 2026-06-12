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

    private var isOverlayActive = false
    private var isCaptureActive = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            startOverlayService()
        } else {
            Toast.makeText(this, "闇€瑕佹偓娴獥鏉冮檺鎵嶈兘鍚姩", Toast.LENGTH_LONG).show()
        }
    }

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "闇€瑕佸睆骞曟埅鍙栨潈闄愭墠鑳借嚜鍔ㄨ瘑鍒?, Toast.LENGTH_SHORT).show()
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
            startActivity(Intent(this, SettingsActivity::class.java))
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

        val deckText = when (config.numberOfDecks) {
            1 -> "1鍓墝锛?浜猴級"; 2 -> "2鍓墝锛?浜猴級"
            3 -> "3鍓墝锛?浜猴級"; 4 -> "4鍓墝锛?浜猴級"
            else -> "2鍓墝锛?浜猴級"
        }

        val remainingScore = tracker.getRemainingScoreValue()
        val totalScore = config.totalScore
        val playedCount = tracker.playedCards.size

        tvStatus.text = buildString {
            append("鐗屽眬锛?deckText | 绾ф暟锛?{displayLevel(config.currentLevel)}\n")
            append("宸插嚭 $playedCount 寮?| 鍓╀綑 ${tracker.getRemainingCards().size} 寮燶n")
            append("鍓╀綑鍒嗘暟锛?remainingScore / $totalScore 鍒?)
        }

        btnStartOverlay.text = if (isOverlayActive) "鍋滄鎮诞绐? else "鍚姩鎮诞绐?
        btnStartCapture.visibility = if (isOverlayActive) View.VISIBLE else View.GONE
        btnStartCapture.text = if (isCaptureActive) "鍋滄鎴睆" else "寮€濮嬫埅灞?
    }

    private fun displayLevel(value: Int): String = when (value) {
        11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"
        else -> value.toString()
    }

    private fun onOverlayToggle() {
        if (isOverlayActive) {
            stopService(Intent(this, OverlayService::class.java))
            isOverlayActive = false
        } else {
            if (!checkOverlayPermission()) {
                requestOverlayPermission()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                    return
                }
            }
            startOverlayService()
        }
        updateUI()
    }

    private fun checkOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this)
        else true

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("闇€瑕佹偓娴獥鏉冮檺")
            .setMessage("璁扮墝鍣ㄩ渶瑕佹偓娴獥鏉冮檺鎵嶈兘鍦ㄦ父鎴忎笂鏂规樉绀恒€傝鍦ㄦ帴涓嬫潵鐨勮缃腑鍏佽鏄剧ず鍦ㄥ叾浠栧簲鐢ㄤ笂灞傘€?)
            .setPositiveButton("鍘昏缃?) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("鍙栨秷", null)
            .show()
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startService(intent)
        isOverlayActive = true
        Toast.makeText(this, "鎮诞绐楀凡鍚姩", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun onCaptureToggle() {
        if (isCaptureActive) {
            stopService(Intent(this, ScreenCaptureService::class.java))
            isCaptureActive = false
        } else {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            capturePermissionLauncher.launch(manager.createScreenCaptureIntent())
        }
        updateUI()
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        isCaptureActive = true
        Toast.makeText(this, "鑷姩璇嗗埆宸插惎鍔?, Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("閲嶇疆鐗屽眬")
            .setMessage("纭畾瑕侀噸缃墍鏈夊嚭鐗岃褰曞拰缁熻鏁版嵁鍚楋紵")
            .setPositiveButton("纭畾") { _, _ ->
                CardRecordsApp.instance.resetGame()
                if (isOverlayActive) {
                    Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_RESET
                        startService(this)
                    }
                }
                Toast.makeText(this, "鐗屽眬宸查噸缃?, Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNegativeButton("鍙栨秷", null)
            .show()
    }

    private fun showManualAddDialog() {
        val config = CardRecordsApp.instance.gameConfig
        val suits = Suit.nonJokerSuits()
        val ranks = Rank.entries.filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }

        val suitNames = suits.map { "${it.symbol} ${it.displayName}" }.toTypedArray()
        val rankNames = ranks.map { it.display }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("鎵嬪姩娣诲姞鍑虹墝")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 30)
        }

        // Suit spinner
        layout.addView(createLabel("鑺辫壊锛?))
        val suitSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item, suitNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            suitSpinner.adapter = adapter
        }
        layout.addView(suitSpinner)

        // Rank spinner
        layout.addView(createLabel("鐗岄潰锛?))
        val rankSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item, rankNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            rankSpinner.adapter = adapter
        }
        layout.addView(rankSpinner)

        // Joker checkbox
        val jokerCheck = CheckBox(this).apply {
            text = "鐜嬬墝"
            setTextColor(-0x1)
        }
        layout.addView(jokerCheck)

        // Joker type spinner (shown only when joker is checked)
        layout.addView(createLabel("鐜嬬被鍨嬶細"))
        val jokerTypeSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf("灏忕帇", "澶х帇")).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            jokerTypeSpinner.adapter = adapter
        }
        jokerTypeSpinner.visibility = View.GONE
        layout.addView(jokerTypeSpinner)

        jokerCheck.setOnCheckedChangeListener { _, isChecked ->
            suitSpinner.isEnabled = !isChecked
            rankSpinner.isEnabled = !isChecked
            jokerTypeSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Player spinner
        layout.addView(createLabel("鍑虹墝鐜╁锛?))
        val playerNames = (0 until config.playerCount).map {
            PlayerPosition.fromIndex(it).label
        }.toTypedArray()
        val playerSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item, playerNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            playerSpinner.adapter = adapter
        }
        layout.addView(playerSpinner)

        // Quantity
        layout.addView(createLabel("鏁伴噺锛堝鍓墝鏃讹級锛?))
        val countPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = config.numberOfDecks.coerceAtMost(4)
            wrapSelectorWheel = false
        }
        layout.addView(countPicker)

        // This round's remaining players (default: all 4)
        layout.addView(createLabel("鏈疆鍏卞嚭鐗屼汉鏁帮細"))
        val roundPlayerCount = NumberPicker(this).apply {
            minValue = 1
            maxValue = config.playerCount
            value = config.playerCount
            wrapSelectorWheel = false
        }
        layout.addView(roundPlayerCount)

        builder.setView(layout)

        builder.setPositiveButton("娣诲姞") { _, _ ->
            val isJoker = jokerCheck.isChecked
            val card = if (isJoker) {
                val jokerRank = if (jokerTypeSpinner.selectedItemPosition == 0)
                    Rank.SMALL_JOKER else Rank.BIG_JOKER
                Card(Suit.JOKER, jokerRank)
            } else {
                val suit = suits[suitSpinner.selectedItemPosition]
                val rank = ranks[rankSpinner.selectedItemPosition]
                Card(suit, rank)
            }
            val playerIdx = playerSpinner.selectedItemPosition
            val count = countPicker.value

            val tracker = CardRecordsApp.instance.cardTracker
            repeat(count) {
                tracker.recordPlayedCard(card, playerIdx)
            }
            // Advance round for void-suit analysis
            tracker.finishRound()

            if (isOverlayActive) {
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UPDATE
                    startService(this)
                }
            }
            Toast.makeText(this, "宸叉坊鍔?${card.displayName} 脳 $count", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        builder.setNegativeButton("鍙栨秷", null)
        builder.show()
    }

    private fun createLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(-0x1)
        textSize = 15f
        setPadding(0, 12, 0, 4)
    }
}


