package com.cardrecords

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cardrecords.logic.CardTracker
import com.cardrecords.model.GameConfig

class CardRecordsApp : Application() {

    lateinit var cardTracker: CardTracker
        private set

    var gameConfig: GameConfig = GameConfig()
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        gameConfig = GameConfig.load(this)
        cardTracker = CardTracker(gameConfig)
        createNotificationChannels()
    }

    fun resetGame() {
        cardTracker.reset()
    }

    fun updateConfig(config: GameConfig) {
        val oldConfig = gameConfig
        gameConfig = config
        GameConfig.saveConfig(this, config)

        // Only recreate tracker if deck/player config actually changed.
        // Otherwise just update config reference to preserve recorded cards.
        if (config.numberOfDecks != oldConfig.numberOfDecks ||
            config.playerCount != oldConfig.playerCount ||
            config.currentLevel != oldConfig.currentLevel) {
            cardTracker = CardTracker(config)
        } else {
            cardTracker.updateConfigRef(config)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY = "overlay_service"
        lateinit var instance: CardRecordsApp
            private set
    }
}