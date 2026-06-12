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
        // After returning from overlay permission settings, try to start
        if (checkOverlayPermission()) {
            startOverlayService()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能启动", Toast.LENGTH_LONG).show()
        }
    }

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "需要屏幕截取权限才能自动识别", Toast.LENGTH_SHORT).show()
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
            1 -> "1副牌（4人）"; 2 -> "2副牌（4人）"
            3 -> "3副牌（6人）"; 4 -> "4副牌（8人）"
            else -> "2副牌（4人）"
        }

        val remainingScore = tracker.getRemainingScoreValue()
        val totalScore = config.totalScore
        val playedCount = tracker.playedCards.size

        tvStatus.text = buildString {
            append("牌局：$deckText | 级数：${displayLevel(config.currentLevel)}\n")
            append("已出 $playedCount 张 | 剩余 ${tracker.getRemainingCards().size} 张\n")
            append("分数：已吃 ${totalScore - remainingScore} / $totalScore 分")
        }

        btnStartOverlay.text = if (isOverlayActive) "停止悬浮窗" else "启动悬浮窗"
        btnStartCapture.visibility = if (isOverlayActive) View.VISIBLE else View.GONE
        btnStartCapture.text = if (isCaptureActive) "停止截屏" else "开始截屏"
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
            .setTitle("需要悬浮窗权限")
            .setMessage("记牌器需要悬浮窗权限才能在游戏上方显示。请在接下来的设置中允许显示在其他应用上层。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        ContextCompat.startForegroundService(this, intent)
        isOverlayActive = true
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "自动识别已启动", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置牌局")
            .setMessage("确定要重置所有出牌记录和统计数据吗？")
            .setPositiveButton("确定") { _, _ ->
                CardRecordsApp.instance.resetGame()
                if (isOverlayActive) {
                    Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_RESET
                        startService(this)
                    }
                }
                Toast.makeText(this, "牌局已重置", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualAddDialog() {
        val config = CardRecordsApp.instance.gameConfig
        val suits = Suit.nonJokerSuits()
        val ranks = Rank.entries.filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }

        val suitNames = suits.map { "${it.symbol} ${it.displayName}" }.toTypedArray()
        val rankNames = ranks.map { it.display }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("手动添加已出牌")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 30)
        }

        // Suit
        layout.addView(TextView(this).apply {
            text = "花色："
            setTextColor(-0x1)
            textSize = 15f
        })
        val suitSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item, suitNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            suitSpinner.adapter = adapter
        }
        layout.addView(suitSpinner)

        // Rank
        layout.addView(TextView(this).apply {
            text = "牌面："
            setTextColor(-0x1)
            textSize = 15f
            setPadding(0, 12, 0, 0)
        })
        val rankSpinner = Spinner(this)
        ArrayAdapter(this, android.R.layout.simple_spinner_item, rankNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            rankSpinner.adapter = adapter
        }
        layout.addView(rankSpinner)

        // Joker checkbox
        val jokerCheck = CheckBox(this).apply {
            text = "王牌（大小王）"
            setTextColor(-0x1)
            setPadding(0, 12, 0, 0)
        }
        layout.addView(jokerCheck)

        // Player
        layout.addView(TextView(this).apply {
            text = "出牌玩家："
            setTextColor(-0x1)
            textSize = 15f
            setPadding(0, 12, 0, 0)
        })
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
        layout.addView(TextView(this).apply {
            text = "数量（多副牌时）："
            setTextColor(-0x1)
            textSize = 15f
            setPadding(0, 12, 0, 0)
        })
        val countPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = config.numberOfDecks.coerceAtMost(4)
            wrapSelectorWheel = false
        }
        layout.addView(countPicker)

        builder.setView(layout)

        jokerCheck.setOnCheckedChangeListener { _, isChecked ->
            suitSpinner.isEnabled = !isChecked
            rankSpinner.isEnabled = !isChecked
        }

        builder.setPositiveButton("添加") { _, _ ->
            val isJoker = jokerCheck.isChecked
            val card = if (isJoker) {
                Card(Suit.JOKER, Rank.SMALL_JOKER)
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

            if (isOverlayActive) {
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UPDATE
                    startService(this)
                }
            }
            Toast.makeText(this, "已添加 ${card.displayName} × $count", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        builder.setNegativeButton("取消", null)
        builder.show()
    }
}
