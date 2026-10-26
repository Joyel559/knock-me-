package com.joyel.tripguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.io.File
import org.osmdroid.config.Configuration

const val CHANNEL_ALARM = "alarm"
const val CHANNEL_SERVICE = "service"

class TripGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
        configureOsmdroid()
    }

    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Arrival and route alarms"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Trip tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing trip tracking notification"
        }

        nm.createNotificationChannel(alarmChannel)
        nm.createNotificationChannel(serviceChannel)
    }
}
