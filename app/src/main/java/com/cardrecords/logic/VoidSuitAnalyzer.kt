package com.cardrecords.logic

import com.cardrecords.model.*

class VoidSuitAnalyzer(private val tracker: CardTracker) {

    data class VoidAnalysis(
        val playerIndex: Int,
        val playerLabel: String,
        val voidSuits: Set<Suit>,
        val confidence: Float  // 0.0 ~ 1.0
    )

    fun analyze(): List<VoidAnalysis> {
        val config = com.cardrecords.CardRecordsApp.instance.gameConfig
        return (0 until config.playerCount).map { index ->
            val voidSuits = tracker.getVoidSuitsForPlayer(index)
            val confidence = calculateConfidence(index, voidSuits)
            VoidAnalysis(
                playerIndex = index,
                playerLabel = PlayerPosition.fromIndex(index).label,
                voidSuits = voidSuits,
                confidence = confidence
            )
        }
    }

    fun getSelfVoidSuits(): Set<Suit> {
        return tracker.getVoidSuitsForPlayer(0)
    }

    private fun calculateConfidence(playerIndex: Int, voidSuits: Set<Suit>): Float {
        if (voidSuits.isEmpty()) return 1.0f

        val playerCards = tracker.playedCards.filter { it.playerIndex == playerIndex }
        if (playerCards.isEmpty()) return 0.1f

        var totalWeight = 0f
        for (suit in voidSuits) {
            val cardsInSuit = playerCards.count { it.card.suit == suit }
            val config = com.cardrecords.CardRecordsApp.instance.gameConfig
            val ratio = cardsInSuit.toFloat() / config.cardsPerSuit.toFloat()
            totalWeight += minOf(ratio * 1.5f, 1.0f)
        }
        return (totalWeight / voidSuits.size).coerceIn(0.1f, 1.0f)
    }

    fun getAnalysisText(): String {
        val analyses = analyze()
        return analyses.joinToString("\n") { analysis ->
            val voidText = if (analysis.voidSuits.isEmpty()) {
                "无缺门"
            } else {
                analysis.voidSuits.joinToString(" ") { it.symbol }
            }
            val confidenceText = when {
                analysis.confidence >= 0.8 -> "✓"
                analysis.confidence >= 0.5 -> "?"
                else -> "?"
            }
            "${analysis.playerLabel}：缺 $voidText $confidenceText"
        }
    }
}
