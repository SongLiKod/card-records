package com.cardrecords.logic

import com.cardrecords.model.*
import com.cardrecords.CardRecordsApp

class ScoreCalculator(private val tracker: CardTracker) {

    fun getTotalScore(): Int {
        return CardRecordsApp.instance.gameConfig.totalScore
    }

    fun getPlayedScore(): Int = tracker.getPlayedScoreValue()

    fun getRemainingScore(): Int = tracker.getRemainingScoreValue()

    fun getScoreProgress(): Float {
        val total = getTotalScore()
        if (total == 0) return 0f
        return getPlayedScore().toFloat() / total.toFloat()
    }

    fun getScoreStats(): ScoreStats {
        val remaining = tracker.getRemainingScoreCounts()
        val totalScore = getTotalScore()
        val playedScore = getPlayedScore()
        return ScoreStats(
            totalScore = totalScore,
            playedScore = playedScore,
            remainingScore = totalScore - playedScore,
            remaining5 = remaining["5"] ?: 0,
            remaining10 = remaining["10"] ?: 0,
            remainingK = remaining["K"] ?: 0
        )
    }
}

data class ScoreStats(
    val totalScore: Int,
    val playedScore: Int,
    val remainingScore: Int,
    val remaining5: Int,
    val remaining10: Int,
    val remainingK: Int
) {
    val progressText: String
        get() = "$playedScore / $totalScore 分"

    val remainingDetail: String
        get() = buildString {
            append("未出分数牌：")
            if (remaining5 > 0) append("5×$remaining5(${remaining5 * 5}分) ")
            if (remaining10 > 0) append("10×$remaining10(${remaining10 * 10}分) ")
            if (remainingK > 0) append("K×$remainingK(${remainingK * 10}分) ")
            if (remaining5 == 0 && remaining10 == 0 && remainingK == 0) append("无")
        }
}
