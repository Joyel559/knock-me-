package com.joyel.tripguard.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.history.MemoryMirror
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.service.AlarmController
import com.joyel.tripguard.service.AlarmKind
import com.joyel.tripguard.trip.Mode
import com.joyel.tripguard.trip.SettingsRepository
import com.joyel.tripguard.ui.Routes
import com.joyel.tripguard.ui.permissions.PermissionsChecklist
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val settings by repo.observe().collectAsState(initial = com.joyel.tripguard.trip.AppSettings())

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                scope.launch {
                    MemoryMirror.importFromFile(context, stream)
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Text("Default radius (km)", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp))
            Row {
                listOf(1, 2, 3, 5).forEach { km ->
                    FilterChip(
                        selected = settings.defaultRadiusKm == km,
                        onClick = { scope.launch { repo.update { it.copy(defaultRadiusKm = km) } } },
                        label = { Text("$km", color = Color.White) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Text("Default mode", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp))
            Row {
                FilterChip(
                    selected = settings.defaultMode == Mode.CAB,
                    onClick = { scope.launch { repo.update { it.copy(defaultMode = Mode.CAB) } } },
                    label = { Text("Cab", color = Color.White) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = settings.defaultMode == Mode.BUS_TRAIN,
                    onClick = { scope.launch { repo.update { it.copy(defaultMode = Mode.BUS_TRAIN) } } },
                    label = { Text("Bus / Train", color = Color.White) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.maxVolumeOnAlarm,
                    onCheckedChange = { v -> scope.launch { repo.update { it.copy(maxVolumeOnAlarm = v) } } }
                )
                Text("Max volume on alarm", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.vibrateOnAlarm,
                    onCheckedChange = { v -> scope.launch { repo.update { it.copy(vibrateOnAlarm = v) } } }
                )
                Text("Vibrate", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }

            var thresholdText by remember { mutableStateOf("") }
            var consecutiveText by remember { mutableStateOf("") }
            LaunchedEffect(settings.routeThresholdM, settings.routeConsecutiveCount) {
                thresholdText = settings.routeThresholdM.toString()
                consecutiveText = settings.routeConsecutiveCount.toString()
            }

            Text("Route threshold (m)", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { text ->
                    thresholdText = text.filter { it.isDigit() }
                    thresholdText.toIntOrNull()?.let { v -> scope.launch { repo.update { s -> s.copy(routeThresholdM = v) } } }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Text("Route consecutive readings", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = consecutiveText,
                onValueChange = { text ->
                    consecutiveText = text.filter { it.isDigit() }
                    consecutiveText.toIntOrNull()?.let { v -> scope.launch { repo.update { s -> s.copy(routeConsecutiveCount = v) } } }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            // MEMORY FILE SECTION
            Text("Memory File (Shared Storage)", color = Color.Yellow, modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleMedium)
            Text("Path: Documents/Knock Me Memory/knock_me_memory.json", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text("After reinstalling, old file may not be auto-readable, so use Import.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())
            ) {
                OutlinedButton(onClick = {
                    scope.launch { MemoryMirror.writeNow(context) }
                }) {
                    Text("Export now", color = Color.White)
                }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }) {
                    Text("Import from file", color = Color.White)
                }
                OutlinedButton(onClick = {
                    runCatching {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                            )
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text("Open folder", color = Color.White)
                }
            }

            Button(
                modifier = Modifier.padding(top = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                onClick = {
                    AlarmController.raiseLoudAlarm(
                        context, AlarmKind.ARRIVAL, "WAKE UP", "Test alarm",
                        maxVolume = settings.maxVolumeOnAlarm, vibrate = settings.vibrateOnAlarm
                    )
                }
            ) {
                Text("Test alarm now", color = Color.White)
            }
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { AlarmController.dismiss(context) }
            ) {
                Text("Stop test alarm", color = Color.White)
            }

            Button(
                modifier = Modifier.padding(top = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                onClick = { navController.navigate(Routes.HISTORY) }
            ) { Text("Trip history", color = Color.White) }
            Button(
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                onClick = { navController.navigate(Routes.SAVED_PLACES) }
            ) { Text("Saved places", color = Color.White) }

            var showClearConfirm by remember { mutableStateOf(false) }
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { showClearConfirm = true }
            ) { Text("Clear all history", color = Color.White) }
            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    containerColor = Color.Black,
                    title = { Text("Clear all trip history?", color = Color.White) },
                    text = { Text("This cannot be undone.", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearConfirm = false
                            scope.launch {
                                HistoryRepository(context).clearAllTrips()
                                MemoryMirror.requestWrite(context)
                            }
                        }) { Text("Clear", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = Color.White) }
                    }
                )
            }

            PermissionsChecklist(modifier = Modifier.padding(top = 24.dp))
        }
    }
}
