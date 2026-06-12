package com.cardrecords.recognition

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.cardrecords.CardRecordsApp
import com.cardrecords.R
import com.cardrecords.overlay.OverlayService
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var isCapturing = false
    private var pendingStop = false

    private val cardRecognizer = CardRecognizer()
    private var captureIntervalMs = 1000L
    private var scope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        cardRecognizer.initialize(this)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode != -1 && data != null) {
                    startCapture(resultCode, data)
                }
            }
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture(resultCode: Int, data: Intent) {
        if (isCapturing) return

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Listen for projection lifecycle to auto-stop when invalidated
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
            }
        }, null)

        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)

        val densityDpi = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        handlerThread = HandlerThread("CaptureThread").apply { start() }
        captureHandler = Handler(handlerThread!!.looper)

        mediaProjection?.createVirtualDisplay(
            "CaptureDisplay",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler
        )

        isCapturing = true
        pendingStop = false
        startCaptureLoop()
    }

    private fun startCaptureLoop() {
        val localHandler = captureHandler ?: return
        localHandler.post(object : Runnable {
            override fun run() {
                // Check stop conditions on the capture thread
                if (pendingStop || !isCapturing) {
                    cleanupOnCaptureThread()
                    return
                }
                try {
                    val reader = imageReader
                    if (reader != null) {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            processImageAsync(image)
                        }
                    }
                } catch (e: IllegalStateException) {
                    // ImageReader was closed; stop gracefully
                    cleanupOnCaptureThread()
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Only reschedule if we are not stopping
                if (!pendingStop && isCapturing) {
                    localHandler.postDelayed(this, captureIntervalMs)
                } else {
                    cleanupOnCaptureThread()
                }
            }
        })
    }

    private fun cleanupOnCaptureThread() {
        // This runs on the capture handler thread, after all processing is done
        isCapturing = false
        captureHandler?.removeCallbacksAndMessages(null)
        try {
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        captureHandler = null
        scope?.cancel()
        scope = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun processImageAsync(image: Image) {
        val currentScope = scope ?: run { image.close(); return }
        currentScope.launch {
            processImage(image)
        }
    }

    private suspend fun processImage(image: Image) {
        try {
            val bitmap = imageToBitmap(image)
            val recognizedCards = cardRecognizer.recognizeCards(bitmap)

            if (recognizedCards.isNotEmpty()) {
                val tracker = CardRecordsApp.instance.cardTracker
                var newCards = 0
                for (card in recognizedCards) {
                    if (!tracker.isCardPlayed(card)) {
                        tracker.recordPlayedCard(card, 0)
                        newCards++
                    }
                }
                if (newCards > 0) {
                    tracker.finishRound()
                }
                // Send update to overlay on main thread
                withContext(Dispatchers.Main) {
                    try {
                        val updateIntent = Intent(this@ScreenCaptureService, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_UPDATE
                        }
                        startService(updateIntent)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride

        val width = image.width
        val height = image.height

        // Use ByteBuffer bulk read to avoid IndexOutOfBounds
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Position the buffer at the beginning
        buffer.position(0)
        val totalBytes = width * height * 4
        val remaining = buffer.remaining().coerceAtMost(totalBytes)
        if (remaining < totalBytes) {
            // Buffer too small; return empty bitmap
            return bmp
        }

        // Read pixels row by row with proper byte ordering
        for (y in 0 until height) {
            val rowBase = y * rowStride
            for (x in 0 until width) {
                val bufPos = rowBase + x * pixelStride
                // Safely read bytes using absolute get
                val r: Int
                val g: Int
                val b: Int
                val a: Int
                try {
                    r = buffer.get(bufPos).toInt() and 0xFF
                    g = buffer.get(bufPos + 1).toInt() and 0xFF
                    b = buffer.get(bufPos + 2).toInt() and 0xFF
                    a = buffer.get(bufPos + 3).toInt() and 0xFF
                } catch (e: IndexOutOfBoundsException) {
                    // If buffer is shorter than expected, fill remaining with black
                    break
                }
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun stopCapture() {
        // Signal the capture thread to stop; cleanup happens there
        pendingStop = true
        isCapturing = false
        captureHandler?.removeCallbacksAndMessages(null)
        // If the capture thread is not running, clean up directly
        if (handlerThread == null || !handlerThread!!.isAlive) {
            cleanupOnCaptureThread()
        }
    }

    private fun createNotification(): Notification {
        val channelId = CardRecordsApp.CHANNEL_OVERLAY
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.capturing))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.cardrecords.action.START_CAPTURE"
        const val ACTION_STOP = "com.cardrecords.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val NOTIFICATION_ID = 1002
    }
}