package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.share.LinkDebugLog

/** Debug builds only (gated by the Settings entry point that navigates here). Last 20
 * [com.joyel.tripguard.share.PlaceInputResolver] resolves: input, which resolver won, elapsed
 * ms, and the parsed result. Memory-only, never persisted. */
@Composable
fun LinkDebugScreen(navController: NavController) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var entries by remember { mutableStateOf(LinkDebugLog.entries) }

    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Link debug", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(
                        AnnotatedString(
                            entries.joinToString("\n") { "${it.resolverWon} ${it.elapsedMs}ms: ${it.input} -> ${it.result}" }
                        )
                    )
                }) { Text("Copy", color = Color.White) }
                OutlinedButton(modifier = Modifier.padding(start = 8.dp), onClick = {
                    LinkDebugLog.clear()
                    entries = LinkDebugLog.entries
                }) { Text("Clear", color = Color.White) }
            }

            if (entries.isEmpty()) {
                Text("No resolves yet.", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(entries) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(entry.input, color = Color.White, maxLines = 2)
                                Text(
                                    "resolver: ${entry.resolverWon} · ${entry.elapsedMs}ms",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(entry.result, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
