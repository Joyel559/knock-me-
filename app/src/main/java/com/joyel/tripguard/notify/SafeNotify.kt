package com.joyel.tripguard.notify

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts [notification] only if the app still holds POST_NOTIFICATIONS (API 33+; always true
 * below that). The user can revoke this permission at any time from system settings — never
 * crash if they have, just silently skip the notification (the alarm sound/vibration, which
 * don't need this permission, still happen).
 */
fun Context.notifySafely(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    NotificationManagerCompat.from(this).notify(id, notification)
}
