package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.ui.Routes

private data class LicensedLibrary(val name: String, val license: String)

private val OPEN_SOURCE_LIBRARIES = listOf(
    LicensedLibrary("Jetpack Compose, AndroidX (Core, Lifecycle, Navigation, DataStore, Room)", "Apache License 2.0"),
    LicensedLibrary("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    LicensedLibrary("OkHttp, Okio", "Apache License 2.0"),
    LicensedLibrary("osmdroid", "GNU Lesser General Public License 2.1"),
    LicensedLibrary("OpenStreetMap data (via Nominatim, Photon, OSRM, osmdroid tiles)", "Open Database License (ODbL)"),
)

@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("About Knock Me", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Text(
                "Version ${com.joyel.tripguard.BuildConfig.VERSION_NAME}",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                "A travel alarm that wakes you before your stop and warns you when your ride " +
                    "leaves the route. No account, no tracking, no ads.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text("Privacy", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "Your location is used on your phone only. See the full privacy policy below.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { navController.navigate(Routes.PRIVACY) }
            ) { Text("Privacy policy", color = Color.White) }

            Text("Map data", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "Map data © OpenStreetMap contributors, available under the Open Database License.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text("Open-source licenses", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            OPEN_SOURCE_LIBRARIES.forEach { lib ->
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(lib.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(lib.license, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Contact", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                com.joyel.tripguard.BuildConfig.CONTACT_EMAIL,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )
        }
    }
}
