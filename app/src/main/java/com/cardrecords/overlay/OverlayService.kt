package com.cardrecords.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.cardrecords.CardRecordsApp
import com.cardrecords.R
import com.cardrecords.logic.ScoreCalculator
import com.cardrecords.logic.VoidSuitAnalyzer
import com.cardrecords.model.*
import com.cardrecords.ui.MainActivity
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ViewGroup? = null
    private var expandedView: ViewGroup? = null
    private var isExpanded = false
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var params: WindowManager.LayoutParams? = null

    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val tracker get() = CardRecordsApp.instance.cardTracker
    private val app get() = CardRecordsApp.instance

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_UPDATE -> updateDisplay()
            ACTION_RESET -> {
                app.resetGame()
                updateDisplay()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayView != null) return

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_collapsed, null) as ViewGroup
        expandedView = inflater.inflate(R.layout.overlay_expanded, null) as ViewGroup

        setupCollapsedView()
        setupExpandedView()
        showCollapsedView()

        startAutoRefresh()
    }

    private fun createNotification(): Notification {
        val channelId = CardRecordsApp.CHANNEL_OVERLAY
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("拖拉机记牌器")
            .setContentText("悬浮窗已显示")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupCollapsedView() {
        val view = overlayView ?: return
        val dragHandle = view.findViewById<View>(R.id.drag_handle)

        view.setOnClickListener {
            if (!isDragging) toggleExpand()
        }

        dragHandle.setOnTouchListener { _, event ->
            handleDrag(event, view)
            true
        }

        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            stopSelf()
        }
    }

    private fun setupExpandedView() {
        val view = expandedView ?: return
        view.findViewById<ImageButton>(R.id.btn_collapse).setOnClickListener {
            toggleExpand()
        }
    }

    private fun showCollapsedView() {
        try { expandedView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}
        try { overlayView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}

        val config = app.gameConfig
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
            alpha = config.transparencyPercent.coerceIn(10, 90) / 100f
        }

        overlayView?.let { windowManager.addView(it, params) }
        isExpanded = false
        updateMiniDisplay()
    }

    private fun showExpandedView() {
        try { overlayView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}

        val config = app.gameConfig
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
            alpha = (config.transparencyPercent + 10).coerceIn(10, 90) / 100f
        }

        expandedView?.let { windowManager.addView(it, params) }
        isExpanded = true
        populateExpandedView()
    }

    private fun toggleExpand() {
        if (isExpanded) showCollapsedView() else showExpandedView()
    }

    private fun handleDrag(event: MotionEvent, view: View) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialX = params?.x ?: 0
                initialY = params?.y ?: 0
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                    isDragging = true
                    params?.x = initialX + dx
                    params?.y = initialY + dy
                    params?.let { windowManager.updateViewLayout(view, it) }
                }
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                if (isExpanded) populateExpandedView() else updateMiniDisplay()
                delay(500)
            }
        }
    }

    fun updateDisplay() {
        if (isExpanded) populateExpandedView() else updateMiniDisplay()
    }

    private fun updateMiniDisplay() {
        val view = overlayView ?: return
        try {
            val remaining = tracker.getRemainingCountBySuit()
            view.findViewById<TextView>(R.id.tv_spade)?.text = "♠${remaining[Suit.SPADE] ?: 0}"
            view.findViewById<TextView>(R.id.tv_heart)?.text = "♥${remaining[Suit.HEART] ?: 0}"
            view.findViewById<TextView>(R.id.tv_club)?.text = "♣${remaining[Suit.CLUB] ?: 0}"
            view.findViewById<TextView>(R.id.tv_diamond)?.text = "♦${remaining[Suit.DIAMOND] ?: 0}"
            view.findViewById<TextView>(R.id.tv_score)?.text = "剩${tracker.getRemainingScoreValue()}分"
        } catch (_: Exception) {}
    }

    private fun populateExpandedView() {
        val view = expandedView ?: return
        try {
            // Remaining cards section
            val remainingLayout = view.findViewById<LinearLayout>(R.id.layout_remaining_cards)
            remainingLayout?.removeAllViews()
            val remaining = tracker.getRemainingCardsBySuit()
            if (remaining.isEmpty()) {
                remainingLayout?.addView(createTextItem("暂无", "#66FFFFFF"))
            } else {
                for ((suit, cards) in remaining) {
                    remainingLayout?.addView(createSuitRow(suit, cards, true))
                }
            }

            // Played cards section
            val playedLayout = view.findViewById<LinearLayout>(R.id.layout_played_cards)
            playedLayout?.removeAllViews()
            val played = tracker.getPlayedCardNames()
            if (played.isEmpty()) {
                playedLayout?.addView(createTextItem("暂无出牌记录", "#66FFFFFF"))
            } else {
                playedLayout?.addView(createTextItem(played.joinToString(" "), "#88FFFFFF"))
            }

            // Score stats section
            val scoreLayout = view.findViewById<LinearLayout>(R.id.layout_score_stats)
            scoreLayout?.removeAllViews()
            val calculator = ScoreCalculator(tracker)
            val stats = calculator.getScoreStats()

            scoreLayout?.addView(createTextItem(stats.remainingDetail, "#FFFFD600", 11f))
            scoreLayout?.addView(createTextItem("已吃：${stats.progressText}", "#FFFFFFFF", 12f, 4))

            // Score by suit
            val scoreBySuit = tracker.getScoreBySuit()
            for ((suit, info) in scoreBySuit) {
                val suitColor = if (suit == Suit.HEART || suit == Suit.DIAMOND)
                    "#FFE53935" else "#FFFFFF"
                scoreLayout?.addView(createTextItem(
                    "${suit.symbol} ${suit.displayName}：剩${info.remainingScore}分（余${info.remainingCount}张）",
                    suitColor, 11f
                ))
            }

            // Void suit analysis
            val voidLayout = view.findViewById<LinearLayout>(R.id.layout_void_analysis)
            voidLayout?.removeAllViews()
            val analyzer = VoidSuitAnalyzer(tracker)
            voidLayout?.addView(createTextItem(analyzer.getAnalysisText(), "#FFEF5350", 11f))
        } catch (_: Exception) {}
    }

    private fun createTextItem(text: String, colorHex: String, textSize: Float = 11f, topPadding: Int = 0): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(colorHex))
            this.textSize = textSize
            if (topPadding > 0) setPadding(0, topPadding, 0, 0)
        }
    }

    private fun createSuitRow(suit: Suit, cards: List<Card>, highlightScore: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 2, 0, 2)
        }

        val suitColor = if (suit == Suit.HEART || suit == Suit.DIAMOND)
            Color.parseColor("#FFE53935") else Color.WHITE

        val suitLabel = TextView(this).apply {
            text = suit.symbol
            setTextColor(suitColor)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        row.addView(suitLabel)

        val cardText = cards.joinToString(" ") { card ->
            if (highlightScore && card.rank.isScoreCard()) {
                "〖${card.rank.display}〗"
            } else {
                card.rank.display
            }
        }

        val cardsView = TextView(this).apply {
            text = cardText
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 11f
            setPadding(6, 0, 0, 0)
        }
        row.addView(cardsView)

        return row
    }

    private fun hideOverlay() {
        stopSelf()
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        try {
            overlayView?.let { windowManager.removeView(it) }
            expandedView?.let { windowManager.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        expandedView = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.cardrecords.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.cardrecords.action.HIDE_OVERLAY"
        const val ACTION_UPDATE = "com.cardrecords.action.UPDATE"
        const val ACTION_RESET = "com.cardrecords.action.RESET"
        private const val NOTIFICATION_ID = 1001
    }
}
