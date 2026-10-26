package com.joyel.tripguard.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joyel.tripguard.ui.permissions.PermCheckId
import com.joyel.tripguard.ui.permissions.buildPermissionChecklist
import kotlinx.coroutines.launch

private data class Step(val id: PermCheckId, val title: String, val sentence: String)

private val ALL_STEPS = listOf(
    Step(PermCheckId.FINE_LOCATION, "Location", "Knock Me needs your location to know when you're close to your destination."),
    Step(PermCheckId.NOTIFICATIONS, "Notifications", "Knock Me uses a notification to show the trip is being tracked and to ring the alarm."),
    Step(PermCheckId.BACKGROUND_LOCATION, "Location in the background", "Android shows this as \"Allow all the time\" on its own settings page, so alarms keep working with the screen off."),
    Step(PermCheckId.FULL_SCREEN_INTENT, "Alarms over the lock screen", "So the arrival alarm can wake the screen even while it's locked."),
    Step(PermCheckId.BATTERY_OPTIMIZATION, "Run in the background", "Let Knock Me keep tracking your trip without the system pausing it.")
)

/** First-run permission onboarding: one step at a time, calm language, resumable (PRD N3). */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { OnboardingRepository(context) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { skipped = repo.skippedSteps() }

    val checklist = remember(refreshKey) { buildPermissionChecklist(context) }
    val grantedIds = checklist.filter { it.granted }.map { it.id }.toSet()

    val pending = remember(refreshKey, skipped) {
        ALL_STEPS.filter { step ->
            when (step.id) {
                PermCheckId.NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
                PermCheckId.FULL_SCREEN_INTENT -> Build.VERSION.SDK_INT >= 34
                else -> true
            } && step.id !in grantedIds && step.id.name !in skipped
        }
    }

    if (pending.isEmpty()) {
        LaunchedEffect(Unit) {
            repo.setComplete()
            onFinished()
        }
        return
    }
    val step = pending.first()

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val requestMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }
    val returnFromSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    fun allow() {
        when (step.id) {
            PermCheckId.FINE_LOCATION -> requestMultiple.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            PermCheckId.NOTIFICATIONS -> requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            PermCheckId.BACKGROUND_LOCATION -> requestPermission.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            PermCheckId.FULL_SCREEN_INTENT -> returnFromSettings.launch(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            )
            PermCheckId.BATTERY_OPTIMIZATION -> returnFromSettings.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            )
            PermCheckId.GPS_ENABLED -> {}
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(step.title, textAlign = TextAlign.Center)
            Text(step.sentence, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            Button(modifier = Modifier.padding(top = 24.dp), onClick = { allow() }) { Text("Allow") }
            TextButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { scope.launch { repo.skip(step.id.name); skipped = repo.skippedSteps() } }
            ) { Text("Not now") }
        }
    }
}
