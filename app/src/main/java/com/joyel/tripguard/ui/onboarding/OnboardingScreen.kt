package com.joyel.tripguard.ui.onboarding

import android.os.Build
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

// Lean mode (v1.5): first launch asks only for these two. Background location, battery
// exemption, and full-screen-intent are never requested here — see docs/DECISIONS.md.
private val ALL_STEPS = listOf(
    Step(PermCheckId.FINE_LOCATION, "Location", "Location while you use Knock Me: needed to know where you are."),
    Step(PermCheckId.NOTIFICATIONS, "Notifications", "Needed to show your trip and ring the alarm.")
)

/** First-run permission onboarding: one step at a time, calm language, resumable (PRD N3, v1.5 lean mode). */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { OnboardingRepository(context) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf<Set<String>>(emptySet()) }
    var disclosureSeen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        skipped = repo.skippedSteps()
        disclosureSeen = repo.isDisclosureSeen()
    }

    // Prominent disclosure (Play policy): shown once, as its own screen, before any system
    // location-permission dialog — not combined with the Allow button that triggers it.
    if (disclosureSeen == false) {
        LocationDisclosureScreen(onContinue = {
            scope.launch { repo.setDisclosureSeen() }
            disclosureSeen = true
        })
        return
    }
    if (disclosureSeen == null) return // loading

    val checklist = remember(refreshKey) { buildPermissionChecklist(context) }
    val grantedIds = checklist.filter { it.granted }.map { it.id }.toSet()

    val pending = remember(refreshKey, skipped) {
        ALL_STEPS.filter { step ->
            when (step.id) {
                PermCheckId.NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
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

    fun allow() {
        when (step.id) {
            PermCheckId.FINE_LOCATION -> requestMultiple.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            PermCheckId.NOTIFICATIONS -> requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> {}
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Black) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(step.title, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
            Text(
                step.sentence,
                color = androidx.compose.ui.graphics.Color.LightGray,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center
            )
            Button(
                modifier = Modifier.padding(top = 24.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                onClick = { allow() }
            ) { Text("Allow", color = androidx.compose.ui.graphics.Color.White) }
            TextButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { scope.launch { repo.skip(step.id.name); skipped = repo.skippedSteps() } }
            ) { Text("Not now", color = androidx.compose.ui.graphics.Color.White) }
        }
    }
}

/** Shown once, before the very first system permission dialog of any kind — a plain-language
 * statement of what location data Knock Me uses and why, and that it never leaves the phone. */
@Composable
private fun LocationDisclosureScreen(onContinue: () -> Unit) {
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Black) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Before you continue",
                color = androidx.compose.ui.graphics.Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Knock Me uses your location to know when you're close to your destination and " +
                    "to ring an alarm. It's only used while you have a trip running.\n\n" +
                    "Your location stays on your phone. There's no account, no tracking, and no ads. " +
                    "If you search for a place or ask for a route, only the text you typed or your " +
                    "coordinates are sent to the map services that answer that one request — " +
                    "nothing else about you is sent anywhere.",
                color = androidx.compose.ui.graphics.Color.LightGray,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
            Button(
                modifier = Modifier.padding(top = 24.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                onClick = onContinue
            ) { Text("Continue", color = androidx.compose.ui.graphics.Color.White) }
            TextButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = onContinue
            ) { Text("Not now", color = androidx.compose.ui.graphics.Color.White) }
        }
    }
}
