package com.cardrecords.model

import android.content.Context

enum class Suit(val symbol: String, val displayName: String) {
    SPADE("S", "Spade"),
    HEART("H", "Heart"),
    CLUB("C", "Club"),
    DIAMOND("D", "Diamond"),
    JOKER("J", "Joker");

    companion object {
        fun nonJokerSuits(): List<Suit> = entries.filter { it != JOKER }
    }
}

enum class Rank(val value: Int, val display: String, val sortOrder: Int) {
    TWO(2, "2", 0), THREE(3, "3", 1), FOUR(4, "4", 2),
    FIVE(5, "5", 3), SIX(6, "6", 4), SEVEN(7, "7", 5),
    EIGHT(8, "8", 6), NINE(9, "9", 7), TEN(10, "10", 8),
    JACK(11, "J", 9), QUEEN(12, "Q", 10), KING(13, "K", 11),
    ACE(14, "A", 12), SMALL_JOKER(15, "SJ", 13), BIG_JOKER(16, "BJ", 14);

    fun isScoreCard(): Boolean = this == FIVE || this == TEN || this == KING
    fun scoreValue(): Int = when (this) { FIVE -> 5; TEN -> 10; KING -> 10; else -> 0 }
}

data class Card(val suit: Suit, val rank: Rank, val deckIndex: Int = 0) {
    val displayName: String get() = "${suit.symbol}${rank.display}"
}

enum class PlayerPosition(val label: String) {
    SELF("Self"), LEFT("Left"), OPPOSITE("Across"), RIGHT("Right");
    companion object { fun fromIndex(index: Int): PlayerPosition = entries[index % entries.size] }
}

data class PlayedCard(val card: Card, val playerIndex: Int, val roundNumber: Int, val timestamp: Long = System.currentTimeMillis())

data class GameConfig(
    val numberOfDecks: Int = 2, val currentLevel: Int = 2, val playerCount: Int = 4,
    val transparencyPercent: Int = 35, val passThrough: Boolean = false, val autoCapture: Boolean = false
) {
    val totalScore: Int get() = numberOfDecks * 100
    val cardsPerSuit: Int get() = 13 * numberOfDecks
    val totalCards: Int get() = numberOfDecks * 54
    val bottomCards: Int get() = when (numberOfDecks) { 1 -> 6; 2 -> 8; 3 -> 12; 4 -> 16; else -> 8 }
    val cardsPerPlayer: Int get() = (totalCards - bottomCards) / playerCount

    companion object {
        private const val PREFS_NAME = "game_config"
        fun load(context: Context): GameConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return GameConfig(
                numberOfDecks = prefs.getInt("number_of_decks", 2),
                currentLevel = prefs.getInt("current_level", 2),
                playerCount = prefs.getInt("player_count", 4),
                transparencyPercent = prefs.getInt("transparency", 35),
                passThrough = prefs.getBoolean("pass_through", false),
                autoCapture = prefs.getBoolean("auto_capture", false)
            )
        }
        fun saveConfig(context: Context, config: GameConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putInt("number_of_decks", config.numberOfDecks)
                putInt("current_level", config.currentLevel)
                putInt("player_count", config.playerCount)
                putInt("transparency", config.transparencyPercent)
                putBoolean("pass_through", config.passThrough)
                putBoolean("auto_capture", config.autoCapture)
                apply()
            }
        }
    }
}