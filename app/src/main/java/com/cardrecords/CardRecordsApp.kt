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
        cardTracker = CardTracker(gameConfig)
    }

    fun updateConfig(config: GameConfig) {
        gameConfig = config
        gameConfig.save(this)
        cardTracker = CardTracker(config)
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
