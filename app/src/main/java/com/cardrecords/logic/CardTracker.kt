package com.cardrecords.logic

import com.cardrecords.model.*

class CardTracker(private var config: GameConfig) {

    private val _playedCards = mutableListOf<PlayedCard>()
    val playedCards: List<PlayedCard> get() = synchronized(this) { _playedCards.toList() }

    private val allCards: List<Card>
    private val totalCardCounts: Map<String, Int>

    var currentRound: Int = 0
        private set

    init {
        val generated = generateAllCards()
        allCards = generated
        totalCardCounts = generated.groupingBy { it.displayName }.eachCount()
    }

    fun updateConfig(newConfig: GameConfig) {
        config = newConfig
        reset()
    }

    fun updateConfigRef(newConfig: GameConfig) {
        config = newConfig
    }

    private fun generateAllCards(): List<Card> {
        val cards = mutableListOf<Card>()
        val decks = config.numberOfDecks
        val nonJokerSuits = Suit.nonJokerSuits()
        val nonJokerRanks = Rank.entries.filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }

        repeat(decks) { deckIndex ->
            for (suit in nonJokerSuits) {
                for (rank in nonJokerRanks) {
                    cards.add(Card(suit, rank, deckIndex))
                }
            }
            cards.add(Card(Suit.JOKER, Rank.SMALL_JOKER, deckIndex))
            cards.add(Card(Suit.JOKER, Rank.BIG_JOKER, deckIndex))
        }
        return cards
    }

    fun recordPlayedCard(card: Card, playerIndex: Int) {
        synchronized(this) {
            _playedCards.add(PlayedCard(card, playerIndex % config.playerCount, currentRound))
        }
    }

    fun recordPlayedCards(cards: List<Pair<Card, Int>>) {
        synchronized(this) {
            cards.forEach { (card, playerIndex) ->
                recordPlayedCard(card, playerIndex)
            }
            currentRound++
        }
    }

    fun finishRound() {
        synchronized(this) {
            currentRound++
        }
    }

    fun getRemainingCards(): List<Card> {
        val playedCounts: Map<String, Int>
        synchronized(this) {
            playedCounts = _playedCards
                .map { it.card }
                .groupingBy { it.displayName }
                .eachCount()
        }

        val remainingCounts = mutableMapOf<String, Int>()
        for ((name, total) in totalCardCounts) {
            val played = playedCounts[name] ?: 0
            remainingCounts[name] = (total - played).coerceAtLeast(0)
        }

        val result = mutableListOf<Card>()
        val addedCounts = mutableMapOf<String, Int>()
        for (card in allCards) {
            val name = card.displayName
            val remaining = remainingCounts[name] ?: 0
            val added = addedCounts[name] ?: 0
            if (added < remaining) {
                result.add(card)
                addedCounts[name] = added + 1
            }
        }
        return result
    }

    fun getRemainingCardsBySuit(): Map<Suit, List<Card>> {
        return getRemainingCards()
            .filter { it.suit != Suit.JOKER }
            .groupBy { it.suit }
            .mapValues { (_, cards) -> cards.sortedByDescending { it.rank.sortOrder } }
    }

    fun getRemainingCountBySuit(): Map<Suit, Int> {
        val all = getRemainingCardsBySuit()
        return Suit.nonJokerSuits().associateWith { suit ->
            all[suit]?.size ?: 0
        }
    }

    fun getRemainingScoreCounts(): Map<String, Int> {
        val remaining = getRemainingCards().filter { it.rank.isScoreCard() }
        return mapOf(
            "5" to remaining.count { it.rank == Rank.FIVE },
            "10" to remaining.count { it.rank == Rank.TEN },
            "K" to remaining.count { it.rank == Rank.KING }
        )
    }

    fun getRemainingScoreValue(): Int {
        return getRemainingCards()
            .filter { it.rank.isScoreCard() }
            .sumOf { it.rank.scoreValue() }
    }

    fun getPlayedScoreValue(): Int {
        synchronized(this) {
            return _playedCards
                .map { it.card }
                .filter { it.rank.isScoreCard() }
                .sumOf { it.rank.scoreValue() }
        }
    }

    fun getScoreBySuit(): Map<Suit, ScoreInfo> {
        val remaining = getRemainingCardsBySuit()
        val total = allCards.filter { it.suit != Suit.JOKER }.groupBy { it.suit }

        return Suit.nonJokerSuits().associateWith { suit ->
            val totalCardsInSuit = total[suit]?.size ?: 0
            val remainingCards = remaining[suit] ?: emptyList()
            val playedCount = totalCardsInSuit - remainingCards.size
            val remainingScore = remainingCards.filter { it.rank.isScoreCard() }
                .sumOf { it.rank.scoreValue() }
            ScoreInfo(
                totalCards = totalCardsInSuit,
                playedCount = playedCount,
                remainingCount = remainingCards.size,
                remainingScore = remainingScore
            )
        }
    }

    fun getVoidSuitsForPlayer(playerIndex: Int): Set<Suit> {
        val allPlayed: List<PlayedCard>
        val playerCards: List<PlayedCard>
        synchronized(this) {
            allPlayed = _playedCards.toList()
            playerCards = allPlayed.filter { it.playerIndex == playerIndex }
        }
        val nonJokerSuits = Suit.nonJokerSuits()

        val voidFromCount = nonJokerSuits.filter { suit ->
            val playedCount = playerCards.count { it.card.suit == suit }
            playedCount >= config.cardsPerSuit
        }.toSet()

        val voidFromLeading = mutableSetOf<Suit>()
        val roundsByNumber = allPlayed.groupBy { it.roundNumber }
        for ((_, roundCards) in roundsByNumber) {
            if (roundCards.size < 2) continue
            val leadCard = roundCards.first()
            val leadSuit = leadCard.card.suit
            if (leadSuit == Suit.JOKER) continue

            val playerInRound = roundCards.find { it.playerIndex == playerIndex }
            if (playerInRound != null && playerInRound.card.suit != leadSuit) {
                voidFromLeading.add(leadSuit)
            }
        }

        return voidFromCount + voidFromLeading
    }

    fun getAllVoidSuits(): Map<Int, Set<Suit>> {
        return (0 until config.playerCount).associateWith { getVoidSuitsForPlayer(it) }
    }

    fun getPlayedCardNames(): List<String> {
        synchronized(this) {
            return _playedCards
                .map { it.card }
                .distinct()
                .sortedBy { it.rank.sortOrder }
                .map { it.displayName }
        }
    }

    fun isCardPlayed(card: Card): Boolean {
        synchronized(this) {
            val playedCount = _playedCards.count { it.card.displayName == card.displayName }
            val totalCount = totalCardCounts[card.displayName] ?: 0
            return playedCount >= totalCount
        }
    }

    fun removeLastPlayedCards(roundCount: Int = 1) {
        if (roundCount <= 0) return
        synchronized(this) {
            val maxRound = _playedCards.maxOfOrNull { it.roundNumber } ?: return
            _playedCards.removeAll { it.roundNumber >= maxRound - roundCount + 1 }
            currentRound = maxOf(0, currentRound - roundCount)
        }
    }

    fun reset() {
        synchronized(this) {
            _playedCards.clear()
            currentRound = 0
        }
    }
}

data class ScoreInfo(
    val totalCards: Int,
    val playedCount: Int,
    val remainingCount: Int,
    val remainingScore: Int
)


