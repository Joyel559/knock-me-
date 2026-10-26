package com.joyel.tripguard.ui.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joyel.tripguard.ui.theme.TgGrey
import com.joyel.tripguard.ui.theme.TgWhite

/** List of permission/state rows with a "Fix" button for each red one. Refreshes on resume. */
@Composable
fun PermissionsChecklist(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val checks = remember(refreshKey) { buildPermissionChecklist(context) }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val requestBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    fun fix(id: PermCheckId) {
        when (id) {
            PermCheckId.GPS_ENABLED ->
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

            PermCheckId.FINE_LOCATION ->
                requestPermissions.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )

            PermCheckId.BACKGROUND_LOCATION ->
                if (Build.VERSION.SDK_INT >= 29) {
                    requestBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }

            PermCheckId.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                }

            PermCheckId.FULL_SCREEN_INTENT ->
                if (Build.VERSION.SDK_INT >= 34) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }

            PermCheckId.BATTERY_OPTIMIZATION ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
        }
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        checks.forEach { check ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (check.granted) "● " else "○ ") + check.label,
                    color = if (check.granted) TgWhite else TgGrey
                )
                if (!check.granted) {
                    Button(onClick = { fix(check.id) }) {
                        Text("Fix")
                    }
                }
            }
        }
    }
}
