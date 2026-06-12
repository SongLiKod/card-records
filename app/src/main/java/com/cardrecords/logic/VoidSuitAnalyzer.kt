package com.cardrecords.logic

import com.cardrecords.model.*

class VoidSuitAnalyzer(private val tracker: CardTracker) {

    data class VoidAnalysis(
        val playerIndex: Int,
        val playerLabel: String,
        val voidSuits: Set<Suit>,
        val confidence: Float
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
            val ratio = cardsInSuit.toFloat() / com.cardrecords.CardRecordsApp.instance.gameConfig.cardsPerSuit.toFloat()
            totalWeight += minOf(ratio * 1.5f, 1.0f)
        }
        return (totalWeight / voidSuits.size).coerceIn(0.1f, 1.0f)
    }

    fun getAnalysisText(): String {
        val analyses = analyze()
        return analyses.joinToString("\n") { analysis ->
            val voidText = if (analysis.voidSuits.isEmpty()) {
                "no void"
            } else {
                analysis.voidSuits.joinToString(" ") { it.symbol }
            }
            val confidenceText = when {
                analysis.confidence >= 0.8f -> "[OK]"
                analysis.confidence >= 0.5f -> "[?]"
                else -> "[low]"
            }
            "${analysis.playerLabel}: ${voidText} ${confidenceText}"
        }
    }
}