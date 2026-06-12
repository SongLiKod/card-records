package com.cardrecords.overlay

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import com.cardrecords.CardRecordsApp
import com.cardrecords.R
import com.cardrecords.logic.ScoreCalculator
import com.cardrecords.logic.VoidSuitAnalyzer
import com.cardrecords.model.*
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
    private var gestureDetector: GestureDetector? = null
    private var overlayShown = false

    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val tracker get() = CardRecordsApp.instance.cardTracker
    private val app get() = CardRecordsApp.instance

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always ensure overlay is displayed and auto-refresh is running
        // on every service start, even from START_STICKY restart.
        ensureOverlayRunning()
        when (intent?.action) {
            ACTION_SHOW -> { /* ensureOverlayRunning already called */ }
            ACTION_HIDE -> stopSelf()
            ACTION_UPDATE -> updateDisplay()
            ACTION_APPLY_CONFIG -> { updateOverlayAlpha(); updateDisplay() }
            ACTION_RESET -> { app.resetGame(); updateDisplay() }
        }
        isRunning = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureOverlayRunning() {
        if (overlayShown && overlayView != null) return
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_collapsed, null) as ViewGroup
        expandedView = inflater.inflate(R.layout.overlay_expanded, null) as ViewGroup
        gestureDetector = GestureDetector(this, OverlayGestureListener())
        setupCollapsedView()
        setupExpandedView()
        showCollapsedView()
        startAutoRefresh()
        overlayShown = true
    }

    private inner class OverlayGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleExpand()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            toggleExpand()
            return true
        }
    }

    private fun setupCollapsedView() {
        val view = overlayView ?: return
        view.setOnTouchListener { v, event ->
            gestureDetector?.onTouchEvent(event)
            handleDrag(event, view)
            true
        }
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener { stopSelf() }
    }

    private fun setupExpandedView() {
        val view = expandedView ?: return
        view.setOnTouchListener { v, event ->
            handleDrag(event, view)
            true
        }
        view.findViewById<ImageButton>(R.id.btn_collapse)?.setOnClickListener { toggleExpand() }
    }

    private fun showCollapsedView() {
        try { expandedView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}
        try { overlayView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}
        params = buildLayoutParams(
            isPassThrough = app.gameConfig.passThrough,
            alpha = app.gameConfig.transparencyPercent.coerceIn(10, 90) / 100f
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 50; y = 200 }
        overlayView?.let { windowManager.addView(it, params) }
        isExpanded = false
        updateMiniDisplay()
    }

    private fun showExpandedView() {
        try { overlayView?.let { windowManager.removeViewImmediate(it) } } catch (_: Exception) {}
        params = buildLayoutParams(
            isPassThrough = false,
            alpha = ((app.gameConfig.transparencyPercent + 10).coerceIn(10, 90)) / 100f
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 50; y = 200 }
        expandedView?.let { windowManager.addView(it, params) }
        isExpanded = true
        populateExpandedView()
    }

    private fun buildLayoutParams(isPassThrough: Boolean, alpha: Float): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                (if (isPassThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { this.alpha = alpha }
    }

    private fun updateOverlayAlpha() {
        val passThrough = app.gameConfig.passThrough
        // Adjust alpha differently for expanded vs collapsed
        val targetAlpha = if (isExpanded) {
            ((app.gameConfig.transparencyPercent + 10).coerceIn(10, 90)) / 100f
        } else {
            app.gameConfig.transparencyPercent.coerceIn(10, 90) / 100f
        }
        params?.alpha = targetAlpha
        val currentFlags = params?.flags ?: 0
        params?.flags = if (passThrough) {
            currentFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            currentFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        try {
            val activeView = if (isExpanded) expandedView else overlayView
            activeView?.let { windowManager.updateViewLayout(it, params) }
        } catch (_: Exception) {}
    }

    private fun handleDrag(event: MotionEvent, view: View) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialX = params?.x ?: 0
                initialY = params?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                params?.x = initialX + dx
                params?.y = initialY + dy
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
            }
        }
    }

    private fun toggleExpand() {
        if (isDragging) return
        if (overlayView == null || expandedView == null) return
        if (isExpanded) showCollapsedView() else showExpandedView()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                if (overlayView != null) {
                    updateDisplay()
                }
                delay(500)
            }
        }
    }

    private fun updateDisplay() {
        if (isExpanded) populateExpandedView() else updateMiniDisplay()
    }

    private fun updateMiniDisplay() {
        val view = overlayView ?: return
        try {
            val remaining = tracker.getRemainingCountBySuit()
            view.findViewById<TextView>(R.id.tv_spade)?.text = "${Suit.SPADE.symbol}${remaining[Suit.SPADE] ?: 0}"
            view.findViewById<TextView>(R.id.tv_heart)?.text = "${Suit.HEART.symbol}${remaining[Suit.HEART] ?: 0}"
            view.findViewById<TextView>(R.id.tv_club)?.text = "${Suit.CLUB.symbol}${remaining[Suit.CLUB] ?: 0}"
            view.findViewById<TextView>(R.id.tv_diamond)?.text = "${Suit.DIAMOND.symbol}${remaining[Suit.DIAMOND] ?: 0}"
            view.findViewById<TextView>(R.id.tv_score)?.text = "Score: ${tracker.getRemainingScoreValue()}"
        } catch (_: Exception) {}
    }

    private fun populateExpandedView() {
        val view = expandedView ?: return
        try {
            val remainingLayout = view.findViewById<LinearLayout>(R.id.layout_remaining_cards)
            remainingLayout?.removeAllViews()
            val remaining = tracker.getRemainingCardsBySuit()
            if (remaining.isEmpty()) {
                remainingLayout?.addView(createTextItem("--", "#66FFFFFF"))
            } else {
                for ((suit, cards) in remaining) {
                    remainingLayout?.addView(createSuitRow(suit, cards, true))
                }
            }

            val playedLayout = view.findViewById<LinearLayout>(R.id.layout_played_cards)
            playedLayout?.removeAllViews()
            val played = tracker.getPlayedCardNames()
            if (played.isEmpty()) {
                playedLayout?.addView(createTextItem("none", "#66FFFFFF"))
            } else {
                playedLayout?.addView(createTextItem(played.joinToString(" "), "#88FFFFFF"))
            }

            val scoreLayout = view.findViewById<LinearLayout>(R.id.layout_score_stats)
            scoreLayout?.removeAllViews()
            val calculator = ScoreCalculator(tracker)
            val stats = calculator.getScoreStats()
            scoreLayout?.addView(createTextItem(stats.remainingDetail, "#FFFFD600", 11f))
            scoreLayout?.addView(createTextItem("Eaten: ${stats.progressText}", "#FFFFFFFF", 12f, 4))

            val scoreBySuit = tracker.getScoreBySuit()
            for ((suit, info) in scoreBySuit) {
                val suitColor = if (suit == Suit.HEART || suit == Suit.DIAMOND) "#FFE53935" else "#FFFFFF"
                scoreLayout?.addView(createTextItem(
                    "${suit.symbol} ${suit.displayName}: left ${info.remainingScore}pts (${info.remainingCount}cards)",
                    suitColor, 11f
                ))
            }

            val voidLayout = view.findViewById<LinearLayout>(R.id.layout_void_analysis)
            voidLayout?.removeAllViews()
            val analyzer = VoidSuitAnalyzer(tracker)
            voidLayout?.addView(createTextItem(analyzer.getAnalysisText(), "#FFEF5350", 11f))
        } catch (_: Exception) {}
    }

    private fun createTextItem(text: String, colorHex: String, textSize: Float = 11f, topPadding: Int = 0): TextView {
        return TextView(this).apply {
            this.text = text; setTextColor(Color.parseColor(colorHex)); this.textSize = textSize
            if (topPadding > 0) setPadding(0, topPadding, 0, 0)
        }
    }

    private fun createSuitRow(suit: Suit, cards: List<Card>, highlightScore: Boolean): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 2, 0, 2) }
        val suitColor = if (suit == Suit.HEART || suit == Suit.DIAMOND) Color.parseColor("#FFE53935") else Color.WHITE
        row.addView(TextView(this).apply { text = suit.symbol; setTextColor(suitColor); textSize = 13f; setTypeface(null, Typeface.BOLD) })
        val cardText = cards.joinToString(" ") { card ->
            if (highlightScore && card.rank.isScoreCard()) { "[${card.rank.display}]" } else { card.rank.display }
        }
        row.addView(TextView(this).apply { text = cardText; setTextColor(Color.parseColor("#CCFFFFFF")); textSize = 11f; setPadding(6, 0, 0, 0) })
        return row
    }

    override fun onDestroy() {
        isRunning = false
        refreshJob?.cancel()
        scope.cancel()
        try {
            overlayView?.let { windowManager.removeView(it) }
            expandedView?.let { windowManager.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        expandedView = null
        overlayShown = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.cardrecords.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.cardrecords.action.HIDE_OVERLAY"
        const val ACTION_UPDATE = "com.cardrecords.action.UPDATE"
        const val ACTION_RESET = "com.cardrecords.action.RESET"
        const val ACTION_APPLY_CONFIG = "com.cardrecords.action.APPLY_CONFIG"

        @Volatile
        var isRunning = false
            private set
    }
}