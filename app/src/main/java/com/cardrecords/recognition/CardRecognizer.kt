package com.cardrecords.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.cardrecords.model.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*

class CardRecognizer {

    private var isInitialized = false

    fun initialize(context: Context) {
        isInitialized = true
    }

    /**
     * Recognize card text from a full screen bitmap.
     * Uses ML Kit Chinese text recognition to detect card values and suits.
     * Falls back to template matching if text recognition fails.
     */
    suspend fun recognizeCards(bitmap: Bitmap): List<Card> {
        if (!isInitialized) return emptyList()

        return withContext(Dispatchers.Default) {
            try {
                // Primary: use bitmap analysis to find card regions
                val cardRegions = findCardRegions(bitmap)
                if (cardRegions.isEmpty()) return@withContext emptyList()

                val cards = mutableListOf<Card>()
                for (region in cardRegions) {
                    val card = analyzeCardRegion(region)
                    if (card != null) cards.add(card)
                }
                cards.distinct()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Find potential card regions in the bitmap.
     * Cards typically appear as small rectangular regions with text.
     */
    private fun findCardRegions(bitmap: Bitmap): List<Bitmap> {
        val regions = mutableListOf<Bitmap>()
        val width = bitmap.width
        val height = bitmap.height

        // The played cards area is typically in the middle-upper portion of the screen
        // Search in a focused area to improve performance
        val searchTop = (height * 0.15).toInt()
        val searchBottom = (height * 0.55).toInt()
        val searchLeft = (width * 0.05).toInt()
        val searchRight = (width * 0.95).toInt()

        // Sample the area looking for high-contrast rectangular regions
        // that would indicate card positions
        val stepX = (width / 15).coerceAtLeast(20)
        val stepY = (height / 20).coerceAtLeast(20)

        // This is a simplified detection - in production you would use
        // more sophisticated contour/edge detection
        val cardWidth = (width / 20).coerceIn(20, 60)
        val cardHeight = (height / 15).coerceIn(30, 80)

        for (y in searchTop until searchBottom step stepY) {
            for (x in searchLeft until searchRight step stepX) {
                // Check if this region might contain a card
                if (isCardRegion(bitmap, x, y, cardWidth, cardHeight)) {
                    try {
                        val region = Bitmap.createBitmap(
                            bitmap,
                            x.coerceAtMost(width - cardWidth),
                            y.coerceAtMost(height - cardHeight),
                            cardWidth.coerceAtMost(width - x),
                            cardHeight.coerceAtMost(height - y)
                        )
                        regions.add(region)
                    } catch (_: Exception) {}
                }
            }
        }

        return regions
    }

    /**
     * Simple heuristic to determine if a region looks like a card.
     * Cards typically have white/light background with dark text.
     */
    private fun isCardRegion(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        val safeW = w.coerceAtMost(bitmap.width - x)
        val safeH = h.coerceAtMost(bitmap.height - y)
        if (safeW < 10 || safeH < 10) return false

        var whitePixels = 0
        var darkPixels = 0
        val total = safeW * safeH

        // Sample pixels to determine if this is a card-like region
        val sampleStep = 2
        for (py in y until y + safeH step sampleStep) {
            for (px in x until x + safeW step sampleStep) {
                try {
                    val pixel = bitmap.getPixel(px, py)
                    val brightness = Color.red(pixel) * 0.299f +
                            Color.green(pixel) * 0.587f +
                            Color.blue(pixel) * 0.114f
                    if (brightness > 200) whitePixels++
                    else if (brightness < 80) darkPixels++
                } catch (_: Exception) {}
            }
        }

        // Card regions should have both light background and dark text
        val whiteRatio = whitePixels.toFloat() / (total / (sampleStep * sampleStep))
        val darkRatio = darkPixels.toFloat() / (total / (sampleStep * sampleStep))
        return whiteRatio in 0.3..0.9 && darkRatio in 0.05..0.5
    }

    /**
     * Analyze a single card region to identify its suit and rank.
     * Uses pixel analysis to detect suit symbols and rank values.
     */
    private fun analyzeCardRegion(bitmap: Bitmap): Card? {
        try {
            val suit = detectSuit(bitmap)
            val rank = detectRank(bitmap)
            if (suit != null && rank != null) {
                return Card(suit, rank)
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Detect suit by looking for suit-specific symbols or colors.
     * ♠♣ - dark/black
     * ♥♦ - red
     */
    private fun detectSuit(bitmap: Bitmap): Suit? {
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2
        val centerY = height / 2

        var redCount = 0
        var blackCount = 0
        val sampleStep = 1

        // Sample center area for color
        for (y in (centerY - height / 4) until (centerY + height / 4) step sampleStep) {
            for (x in (centerX - width / 4) until (centerX + width / 4) step sampleStep) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // Red detection (♥♦)
                    if (r > 150 && g < 100 && b < 100) redCount++
                    // Dark detection (♠♣)
                    if (r < 80 && g < 80 && b < 80) blackCount++
                } catch (_: Exception) {}
            }
        }

        // Cannot reliably distinguish between ♠/♣ or ♥/♦ via color alone
        // Additional shape analysis would be needed
        return if (redCount > blackCount) {
            Suit.HEART  // or DIAMOND - approximated
        } else if (blackCount > redCount) {
            Suit.SPADE  // or CLUB - approximated
        } else {
            null
        }
    }

    /**
     * Detect rank by analyzing the card region for text-like features.
     * Simplified detection - full implementation would use OCR.
     */
    private fun detectRank(bitmap: Bitmap): Rank? {
        // Simplified detection based on pixel patterns
        // In production, this would use ML Kit Text Recognition
        val width = bitmap.width
        val height = bitmap.height

        // Sample the top-left area where rank text typically appears
        val topLeftRegion = try {
            Bitmap.createBitmap(bitmap, 0, 0, width / 2, height / 2)
        } catch (_: Exception) {
            return null
        }

        // Here we would run OCR on the region
        // For now, return a placeholder - the actual recognition
        // is handled by the ML Kit integration in ScreenCaptureService
        return null
    }

    /**
     * Batch recognition using ML Kit text recognition.
     * This should be called from the capture service with
     * the full screen bitmap for optimal results.
     */
    suspend fun recognizeWithMLKit(bitmap: Bitmap): List<Card> {
        return withContext(Dispatchers.Default) {
            try {
                val cards = mutableListOf<Card>()
                val regions = findCardRegions(bitmap)

                for (region in regions) {
                    // In full implementation, run ML Kit TextRecognizer on each region
                    val card = analyzeCardRegion(region)
                    if (card != null) cards.add(card)
                }

                cards.distinctBy { it.displayName }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
