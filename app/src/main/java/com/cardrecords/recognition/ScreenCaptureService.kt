package com.cardrecords.recognition

import android.app.*
import android.content.Context
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

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var isCapturing = false

    private val cardRecognizer = CardRecognizer()
    private var captureIntervalMs = 500L

    override fun onCreate() {
        super.onCreate()
        cardRecognizer.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra(EXTRA_DATA, android.content.Intent::class.java)
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
        startCaptureLoop()
    }

    private fun startCaptureLoop() {
        captureHandler?.post(object : Runnable {
            override fun run() {
                if (!isCapturing) return

                val image = imageReader?.acquireLatestImage()
                image?.let { processImage(it) }

                captureHandler?.postDelayed(this, captureIntervalMs)
            }
        })
    }

    private fun processImage(image: Image) {
        try {
            val bitmap = imageToBitmap(image)
            val recognizedCards = cardRecognizer.recognizeCards(bitmap)

            if (recognizedCards.isNotEmpty()) {
                val tracker = CardRecordsApp.instance.cardTracker
                for (card in recognizedCards) {
                    if (!tracker.isCardPlayed(card)) {
                        tracker.recordPlayedCard(card, 0)
                    }
                }

                // Update overlay
                val updateIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UPDATE
                }
                startService(updateIntent)
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
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun stopCapture() {
        isCapturing = false
        try {
            mediaProjection?.stop()
            imageReader?.close()
            handlerThread?.quitSafely()
        } catch (_: Exception) {}
        mediaProjection = null
        imageReader = null
        handlerThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = CardRecordsApp.CHANNEL_OVERLAY
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("拖拉机记牌器")
            .setContentText("正在截取屏幕…")
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
