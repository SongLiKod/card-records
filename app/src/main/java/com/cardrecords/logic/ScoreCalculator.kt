package com.cardrecords.logic

import com.cardrecords.model.*
import com.cardrecords.CardRecordsApp

class ScoreCalculator(private val tracker: CardTracker) {
    fun getTotalScore(): Int = CardRecordsApp.instance.gameConfig.totalScore
    fun getPlayedScore(): Int = tracker.getPlayedScoreValue()
    fun getRemainingScore(): Int = tracker.getRemainingScoreValue()
    fun getScoreProgress(): Float { val t = getTotalScore(); return if (t == 0) 0f else getPlayedScore().toFloat() / t.toFloat() }

    fun getScoreStats(): ScoreStats {
        val remaining = tracker.getRemainingScoreCounts()
        val totalScore = getTotalScore()
        val playedScore = getPlayedScore()
        return ScoreStats(totalScore, playedScore, totalScore - playedScore, remaining["5"] ?: 0, remaining["10"] ?: 0, remaining["K"] ?: 0)
    }
}

data class ScoreStats(
    val totalScore: Int, val playedScore: Int, val remainingScore: Int,
    val remaining5: Int, val remaining10: Int, val remainingK: Int
) {
    val progressText: String get() = "$playedScore / $totalScore pts"
    val remainingDetail: String get() = buildString {
        append("Unplayed: ")
        if (remaining5 > 0) append("5x$remaining5(${remaining5 * 5}pts) ")
        if (remaining10 > 0) append("10x$remaining10(${remaining10 * 10}pts) ")
        if (remainingK > 0) append("Kx$remainingK(${remainingK * 10}pts) ")
        if (remaining5 == 0 && remaining10 == 0 && remainingK == 0) append("none")
    }
}