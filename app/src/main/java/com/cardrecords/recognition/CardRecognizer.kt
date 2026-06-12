package com.cardrecords.recognition
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.cardrecords.model.*
import kotlinx.coroutines.*
class CardRecognizer {
    private var isInitialized = false
    fun initialize(context: Context) {
        isInitialized = true
    }
    suspend fun recognizeCards(bitmap: Bitmap): List<Card> {
        if (!isInitialized) return emptyList()
        return withContext(Dispatchers.Default) {
            try {
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
    private fun findCardRegions(bitmap: Bitmap): List<Bitmap> {
        val regions = mutableListOf<Bitmap>()
        val width = bitmap.width
        val height = bitmap.height
        val searchTop = (height * 0.15).toInt()
        val searchBottom = (height * 0.55).toInt()
        val searchLeft = (width * 0.05).toInt()
        val searchRight = (width * 0.95).toInt()
        val stepX = (width / 15).coerceAtLeast(20)
        val stepY = (height / 20).coerceAtLeast(20)
        val cardWidth = (width / 20).coerceIn(20, 60)
        val cardHeight = (height / 15).coerceIn(30, 80)
        for (y in searchTop until searchBottom step stepY) {
            for (x in searchLeft until searchRight step stepX) {
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
    private fun isCardRegion(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        val safeW = w.coerceAtMost(bitmap.width - x)
        val safeH = h.coerceAtMost(bitmap.height - y)
        if (safeW < 10 || safeH < 10) return false
        var whitePixels = 0
        var darkPixels = 0
        val total = safeW * safeH
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
        val whiteRatio = whitePixels.toFloat() / (total / (sampleStep * sampleStep))
        val darkRatio = darkPixels.toFloat() / (total / (sampleStep * sampleStep))
        return whiteRatio in 0.3..0.9 && darkRatio in 0.05..0.5
    }
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
    private fun detectSuit(bitmap: Bitmap): Suit? {
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2
        val centerY = height / 2
        var redCount = 0
        var blackCount = 0
        val sampleStep = 1
        for (y in (centerY - height / 4) until (centerY + height / 4) step sampleStep) {
            for (x in (centerX - width / 4) until (centerX + width / 4) step sampleStep) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    if (r > 150 && g < 100 && b < 100) redCount++
                    if (r < 80 && g < 80 && b < 80) blackCount++
                } catch (_: Exception) {}
            }
        }
        return if (redCount > blackCount) {
            Suit.HEART
        } else if (blackCount > redCount) {
            Suit.SPADE
        } else {
            null
        }
    }
    private fun detectRank(bitmap: Bitmap): Rank? {
        val width = bitmap.width
        val height = bitmap.height
        try {
            val region = Bitmap.createBitmap(bitmap, 0, 0, width / 2, height / 2)
            val pixels = IntArray(region.width * region.height)
            region.getPixels(pixels, 0, region.width, 0, 0, region.width, region.height)
            var darkCount = 0
            var totalPixels = 0
            for (pixel in pixels) {
                val brightness = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                if (brightness < 100) darkCount++
                totalPixels++
            }
            val darkRatio = darkCount.toFloat() / totalPixels.toFloat()
            if (darkRatio < 0.02f) return null
            return Rank.entries.firstOrNull {
                it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER && it.value in 2..14
            }
        } catch (_: Exception) {
            return null
        }
    }
    suspend fun recognizeWithMLKit(bitmap: Bitmap): List<Card> {
        return withContext(Dispatchers.Default) {
            try {
                val cards = mutableListOf<Card>()
                val regions = findCardRegions(bitmap)
                for (region in regions) {
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
