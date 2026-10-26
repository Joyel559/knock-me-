package com.joyel.tripguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TgBlack = Color(0xFF000000)
val TgWhite = Color(0xFFFFFFFF)
val TgGrey = Color(0xFF9E9E9E)
val TgRed = Color(0xFFFF3B30)

private val TripGuardColorScheme = darkColorScheme(
    primary = TgWhite,
    onPrimary = TgBlack,
    secondary = TgGrey,
    onSecondary = TgBlack,
    background = TgBlack,
    onBackground = TgWhite,
    surface = TgBlack,
    onSurface = TgWhite,
    error = TgRed,
    onError = TgWhite,
)

@Composable
fun TripGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TripGuardColorScheme,
        content = content
    )
}
