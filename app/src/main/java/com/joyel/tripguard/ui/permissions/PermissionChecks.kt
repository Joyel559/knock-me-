package com.joyel.tripguard.ui.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

enum class PermCheckId {
    FINE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,
    BATTERY_OPTIMIZATION,
    GPS_ENABLED
}

data class PermCheck(val id: PermCheckId, val label: String, val granted: Boolean)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun isGpsEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.canUseFullScreenIntent()
}

fun buildPermissionChecklist(context: Context): List<PermCheck> {
    val fineOrCoarse = hasPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)

    val background = if (Build.VERSION.SDK_INT >= 29) {
        hasPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else true

    val notifications = if (Build.VERSION.SDK_INT >= 33) {
        hasPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
    } else true

    return listOf(
        PermCheck(PermCheckId.GPS_ENABLED, "GPS turned on", isGpsEnabled(context)),
        PermCheck(PermCheckId.FINE_LOCATION, "Location access", fineOrCoarse),
        PermCheck(PermCheckId.BACKGROUND_LOCATION, "Background location (\"Allow all the time\")", background),
        PermCheck(PermCheckId.NOTIFICATIONS, "Notifications", notifications),
        PermCheck(PermCheckId.FULL_SCREEN_INTENT, "Full-screen alarm", canUseFullScreenIntent(context)),
        PermCheck(PermCheckId.BATTERY_OPTIMIZATION, "Battery optimization exemption", isIgnoringBatteryOptimizations(context)),
    )
}
