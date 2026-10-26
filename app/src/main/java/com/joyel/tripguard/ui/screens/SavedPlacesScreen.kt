package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.SavedPlace
import kotlinx.coroutines.launch

/** List/rename/delete saved places (PRD N7). Adding a place from the map/search happens in Trip Setup. */
@Composable
fun SavedPlacesScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()
    val places by repo.observeSavedPlaces().collectAsState(initial = emptyList())
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold { padding ->
        if (places.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No saved places yet. Save one from Trip Setup or a trip in History.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(places, key = { it.id }) { place ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        if (renamingId == place.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                scope.launch { repo.updateSavedPlace(place.copy(name = renameText)) }
                                renamingId = null
                            }) { Text("Save") }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name)
                                Text(place.label)
                            }
                            TextButton(onClick = { renamingId = place.id; renameText = place.name }) { Text("Rename") }
                            TextButton(onClick = { scope.launch { repo.deleteSavedPlace(place) } }) { Text("Delete") }
                        }
                    }
                    Divider()
                }
            }
        }
    }
}
