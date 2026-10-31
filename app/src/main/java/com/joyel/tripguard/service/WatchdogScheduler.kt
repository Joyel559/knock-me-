package com.joyel.tripguard.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.joyel.tripguard.ui.MainActivity

private const val REQUEST_CODE = 9001

/**
 * Schedules the watchdog's next check via AlarmManager.setAlarmClock — this needs no special
 * permission and keeps firing in Doze, unlike setExactAndAllowWhileIdle on some OEMs.
 */
object WatchdogScheduler {
    private fun operationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + WatchdogLogic.CHECK_INTERVAL_MS
        val showIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operationPendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarmManager.cancel(operationPendingIntent(context)) }
    }
}
