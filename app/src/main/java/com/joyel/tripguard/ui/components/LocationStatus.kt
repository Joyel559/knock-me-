package com.joyel.tripguard.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ResolvableApiException
import com.joyel.tripguard.location.LocationState
import com.joyel.tripguard.location.locationSettingsTask
import com.joyel.tripguard.search.NominatimClient

/** Calm status line + "Turn on GPS" button; never shows red/error wording (PRD N2/N4). */
@Composable
fun LocationStatusSection(state: LocationState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gpsResolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {}

    var placeName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        if (state is LocationState.Ready) {
            placeName = null
            placeName = NominatimClient().reverseGeocode(state.lat, state.lon)
        }
    }

    when (state) {
        is LocationState.NoPermission -> Text("Waiting for location permission", modifier = modifier)
        is LocationState.GpsOff -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Turn on GPS to find your location", modifier = Modifier.padding(end = 8.dp))
            Button(onClick = {
                locationSettingsTask(context)
                    .addOnFailureListener { e ->
                        if (e is ResolvableApiException) {
                            runCatching {
                                gpsResolutionLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                            }.onFailure {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                )
                            }
                        } else {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        }
                    }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("Turn on GPS", color = Color.White) }
        }
        is LocationState.Searching -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).then(Modifier))
            Text("Finding your location…")
        }
        is LocationState.Ready -> Text(
            placeName ?: "%.5f, %.5f".format(state.lat, state.lon),
            modifier = modifier
        )
    }
}
