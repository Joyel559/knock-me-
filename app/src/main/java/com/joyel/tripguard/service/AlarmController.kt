package com.joyel.tripguard.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.joyel.tripguard.CHANNEL_ALARM
import com.joyel.tripguard.R
import com.joyel.tripguard.ui.AlarmActivity

enum class AlarmKind { ARRIVAL, FINAL_ARRIVAL, OFF_ROUTE, GPS_OFF, NO_FIX, LOW_BATTERY }

private const val ALARM_NOTIFICATION_ID = 2001
private const val SHORT_ALERT_NOTIFICATION_ID = 2002

/**
 * Owns the loud, looping alarm (full-screen intent + sound + vibration) and the shorter,
 * non-looping alerts that share the same notification channel. A singleton: at most one
 * loud alarm rings at a time (arrival takes priority, enforced by [TripEngine]).
 */
object AlarmController {
    private var player: AlarmPlayer? = null

    fun raiseLoudAlarm(
        context: Context,
        kind: AlarmKind,
        title: String,
        message: String,
        maxVolume: Boolean = true,
        vibrate: Boolean = true,
        onTimeout: () -> Unit = {}
    ) {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_KIND, kind.name)
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, kind.ordinal, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissAction = when (kind) {
            AlarmKind.FINAL_ARRIVAL -> ACTION_DISMISS_FINAL_ARRIVAL
            AlarmKind.OFF_ROUTE -> ACTION_IGNORE_ROUTE
            else -> ACTION_DISMISS_ARRIVAL
        }
        val dismissIntent = Intent(context, TripService::class.java).setAction(dismissAction)
        val dismissPendingIntent = PendingIntent.getService(
            context, kind.ordinal + 100, dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, TripService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context, kind.ordinal + 200, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (kind == AlarmKind.OFF_ROUTE) {
            val rerouteIntent = Intent(context, TripService::class.java).setAction(ACTION_REROUTE)
            val reroutePending = PendingIntent.getService(context, 301, rerouteIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(R.drawable.ic_notification, "Re-route", reroutePending)
            builder.addAction(R.drawable.ic_notification, "Ignore 10m", dismissPendingIntent)
            builder.addAction(R.drawable.ic_notification, "Stop Trip", stopPendingIntent)
        } else {
            builder.addAction(R.drawable.ic_notification, "I'm Awake", dismissPendingIntent)
            builder.addAction(R.drawable.ic_notification, "Stop Alarm", stopPendingIntent)
        }

        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification)

        player?.stop() // never orphan a previous ringing player
        player = AlarmPlayer(context).also {
            it.start(onTimeout = onTimeout, maxVolume = maxVolume, vibrate = vibrate)
        }
    }

    fun dismiss(context: Context) {
        player?.stop()
        player = null
        NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
        context.sendBroadcast(Intent("com.joyel.tripguard.ACTION_DISMISS_ALARM"))
    }

    /** Short, single, non-looping heads-up alert (GPS off, no fix, low battery). */
    fun raiseShortAlert(context: Context, title: String, message: String) {
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SHORT_ALERT_NOTIFICATION_ID, notification)
    }
}
