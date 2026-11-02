package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/** Same plain-language policy as PRIVACY.md at the project root, kept in sync by hand (short
 * enough that a build-time asset-copy step isn't worth the complexity). */
@Composable
fun PrivacyScreen(navController: NavController) {
    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Privacy policy", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Text(
                "Knock Me does not have accounts, does not use analytics, and does not show ads.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 16.dp)
            )

            section("Location") {
                "Your location is used only on your phone, only while a trip is running, to " +
                    "check your distance to the destination and your position on the planned " +
                    "route. It is never sent anywhere, never stored on a server, and never " +
                    "shared with anyone."
            }
            section("What's stored on your phone") {
                "Trip history, saved places, and settings are stored in a local database on " +
                    "your device, plus a readable backup file in Documents/Knock Me Memory/. " +
                    "None of it leaves your phone unless you choose to export or share it yourself."
            }
            section("Network requests") {
                "Searching for a place, pasting a link, or asking for a route sends only the " +
                    "text you typed or the coordinates involved to the keyless public map " +
                    "services that answer that one request (OpenStreetMap's Nominatim and " +
                    "Photon for search, OSRM for routing). No account identifier, device ID, or " +
                    "advertising ID is attached to those requests."
            }
            section("Deleting your data") {
                "Settings → Clear all history removes everything Knock Me has stored, including " +
                    "the backup file. Uninstalling the app removes everything else."
            }
            section("Contact") {
                com.joyel.tripguard.BuildConfig.CONTACT_EMAIL
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun section(title: String, body: () -> String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
    Text(body(), color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
}
