package com.cardrecords.model

import android.content.Context

enum class Suit(val symbol: String, val displayName: String) {
    SPADE("♠", "黑桃"),
    HEART("♥", "红心"),
    CLUB("♣", "梅花"),
    DIAMOND("♦", "方块"),
    JOKER("🃏", "王");

    companion object {
        fun fromSymbol(s: String): Suit? = entries.find { it.symbol == s }
        fun nonJokerSuits(): List<Suit> = entries.filter { it != JOKER }
    }
}

enum class Rank(val value: Int, val display: String, val sortOrder: Int) {
    TWO(2, "2", 0),
    THREE(3, "3", 1),
    FOUR(4, "4", 2),
    FIVE(5, "5", 3),
    SIX(6, "6", 4),
    SEVEN(7, "7", 5),
    EIGHT(8, "8", 6),
    NINE(9, "9", 7),
    TEN(10, "10", 8),
    JACK(11, "J", 9),
    QUEEN(12, "Q", 10),
    KING(13, "K", 11),
    ACE(14, "A", 12),
    SMALL_JOKER(15, "小王", 13),
    BIG_JOKER(16, "大王", 14);

    fun isScoreCard(): Boolean = this == FIVE || this == TEN || this == KING
    fun scoreValue(): Int = when (this) {
        FIVE -> 5
        TEN -> 10
        KING -> 10
        else -> 0
    }

    companion object {
        fun fromDisplay(s: String): Rank? = entries.find { it.display == s }
    }
}

data class Card(
    val suit: Suit,
    val rank: Rank,
    val deckIndex: Int = 0
) {
    val displayName: String get() = "${suit.symbol}${rank.display}"

    companion object {
        private var idCounter = 0
        fun nextId(): Int = idCounter++
    }
}

enum class PlayerPosition(val label: String) {
    SELF("我"),
    LEFT("上家"),
    OPPOSITE("对家"),
    RIGHT("下家");

    companion object {
        fun fromIndex(index: Int): PlayerPosition = entries[index % entries.size]
    }
}

data class PlayedCard(
    val card: Card,
    val playerIndex: Int,
    val roundNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class GameConfig(
    val numberOfDecks: Int = 2,
    val currentLevel: Int = 2,
    val playerCount: Int = 4,
    val transparencyPercent: Int = 35,
    val passThrough: Boolean = false,
    val autoCapture: Boolean = false
) {
    val totalScore: Int get() = numberOfDecks * 100

    val cardsPerSuit: Int get() = 13 * numberOfDecks

    val totalCards: Int get() = numberOfDecks * 54

    val bottomCards: Int get() = when (numberOfDecks) {
        1 -> 6
        2 -> 8
        3 -> 12
        4 -> 16
        else -> 8
    }

    val cardsPerPlayer: Int get() = (totalCards - bottomCards) / playerCount

    companion object {
        private const val PREFS_NAME = "game_config"
        private const val KEY_DECKS = "number_of_decks"
        private const val KEY_LEVEL = "current_level"
        private const val KEY_PLAYERS = "player_count"
        private const val KEY_TRANSPARENCY = "transparency"
        private const val KEY_PASS_THROUGH = "pass_through"
        private const val KEY_AUTO_CAPTURE = "auto_capture"

        fun load(context: Context): GameConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return GameConfig(
                numberOfDecks = prefs.getInt(KEY_DECKS, 2),
                currentLevel = prefs.getInt(KEY_LEVEL, 2),
                playerCount = prefs.getInt(KEY_PLAYERS, 4),
                transparencyPercent = prefs.getInt(KEY_TRANSPARENCY, 35),
                passThrough = prefs.getBoolean(KEY_PASS_THROUGH, false),
                autoCapture = prefs.getBoolean(KEY_AUTO_CAPTURE, false)
            )
        }

        fun saveConfig(context: Context, config: GameConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putInt(KEY_DECKS, config.numberOfDecks)
                putInt(KEY_LEVEL, config.currentLevel)
                putInt(KEY_PLAYERS, config.playerCount)
                putInt(KEY_TRANSPARENCY, config.transparencyPercent)
                putBoolean(KEY_PASS_THROUGH, config.passThrough)
                putBoolean(KEY_AUTO_CAPTURE, config.autoCapture)
                apply()
            }
        }
    }
}
