package com.joyel.tripguard.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.joyel.tripguard.trip.ReliabilityRepository
import com.joyel.tripguard.trip.ReliabilityState
import com.joyel.tripguard.ui.permissions.isIgnoringBatteryOptimizations
import kotlinx.coroutines.launch

private data class BrandStep(val title: String, val steps: List<String>, val intent: () -> Intent?)

/** Plain, brand-specific "don't let the system kill this app" steps. Best effort: component
 * names are the commonly-documented ones for each OEM's autostart/battery screen and may not
 * exist on every model/OS version, so every launch is wrapped and falls back to app settings. */
private fun brandStep(context: android.content.Context): BrandStep? {
    val manufacturer = Build.MANUFACTURER.lowercase()
    fun componentIntent(pkg: String, cls: String) = Intent().apply {
        component = android.content.ComponentName(pkg, cls)
    }
    return when {
        "xiaomi" in manufacturer -> BrandStep(
            "Xiaomi / Redmi / Poco",
            listOf("Autostart: turn ON for Knock Me", "Battery saver: set to \"No restrictions\""),
            { componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }
        )
        "oppo" in manufacturer -> BrandStep(
            "Oppo",
            listOf("Startup Manager: allow Knock Me", "Battery: set to \"Allow background activity\""),
            { componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") }
        )
        "realme" in manufacturer -> BrandStep(
            "Realme",
            listOf("Startup Manager: allow Knock Me", "Battery: set to \"Allow background activity\""),
            { componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") }
        )
        "vivo" in manufacturer -> BrandStep(
            "Vivo",
            listOf("i-Manager > App Manager > Autostart: turn ON for Knock Me"),
            { componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity") }
        )
        "oneplus" in manufacturer -> BrandStep(
            "OnePlus",
            listOf("Battery optimization: set Knock Me to \"Don't optimize\""),
            { null } // OnePlus battery settings moved into standard Android settings on recent OxygenOS
        )
        "samsung" in manufacturer -> BrandStep(
            "Samsung",
            listOf("Battery > Background usage limits: remove Knock Me from \"Put unused apps to sleep\"", "Set Knock Me to \"Never sleeping apps\""),
            { componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity") }
        )
        "huawei" in manufacturer -> BrandStep(
            "Huawei",
            listOf("App launch: switch Knock Me to Manage manually, turn on Auto-launch, Secondary launch, Run in background"),
            { componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity") }
        )
        "honor" in manufacturer -> BrandStep(
            "Honor",
            listOf("App launch: switch Knock Me to Manage manually, turn on Auto-launch, Run in background"),
            { componentIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity") }
        )
        else -> null
    }
}

@Composable
fun ReliabilityScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ReliabilityRepository(context) }
    val state by repo.observe().collectAsState(initial = ReliabilityState())

    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundLocationGranted = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= 29) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }
    val batteryExempt = remember(refreshKey) { isIgnoringBatteryOptimizations(context) }
    val brand = remember { brandStep(context) }

    val requestBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Max reliability", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Text(
                "Android may stop apps that run in the background. This option keeps Knock Me " +
                    "alive during long trips. It asks for: location all the time and permission " +
                    "to ignore battery limits. Your location never leaves the phone.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Max reliability", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = state.maxReliabilityEnabled,
                    onCheckedChange = { enabled -> scope.launch { repo.setMaxReliability(enabled) } }
                )
            }

            if (state.maxReliabilityEnabled) {
                Text(
                    "Turning this on uses a geofence as a backup for the arrival alarm, and " +
                        "restarts your trip automatically after the phone reboots.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Step 1: background location
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Location, all the time", color = Color.White)
                            Text(if (backgroundLocationGranted) "Allowed" else "Not allowed", color = if (backgroundLocationGranted) Color.White else Color.Gray)
                        }
                        Text(
                            "Android calls this \"Allow all the time\" on its own permission screen.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (!backgroundLocationGranted) {
                            Button(
                                modifier = Modifier.padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        requestBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                }
                            ) { Text("Allow", color = Color.White) }
                        }
                    }
                }

                // Step 2: battery exemption
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Ignore battery limits", color = Color.White)
                            Text(if (batteryExempt) "Allowed" else "Not allowed", color = if (batteryExempt) Color.White else Color.Gray)
                        }
                        if (!batteryExempt) {
                            Button(
                                modifier = Modifier.padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }
                                }
                            ) { Text("Allow", color = Color.White) }
                        }
                    }
                }

                // Step 3: brand-specific autostart (only on supported brands)
                brand?.let { b ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(b.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            b.steps.forEach {
                                Text("• $it", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                    onClick = {
                                        val intent = b.intent()
                                        val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
                                        if (!opened) openAppSettings()
                                    }
                                ) { Text("Open settings", color = Color.White) }
                                TextButton(onClick = { openAppSettings() }) { Text("Open app settings", color = Color.White) }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Turning this off just stops using location-all-the-time and the battery " +
                        "exemption — Knock Me won't ask for them again. You can revoke them " +
                        "yourself any time in Android's own app settings.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
