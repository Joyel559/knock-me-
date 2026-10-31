package com.joyel.tripguard.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Formats a distance in meters as "800 m" or "2.5 km", the way the app shows chosen radii. */
fun formatMeters(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    meters % 1000 == 0 -> "${meters / 1000} km"
    else -> "%.1f km".format(meters / 1000.0)
}

/** A row of preset distance chips (in meters) plus a trailing "Custom" chip. Selected chip is
 * filled white with black text, matching the app's plain black-and-white theme. */
@Composable
fun KnockDistanceChipsRow(
    presetsMeters: List<Int>,
    selectedMeters: Int,
    isCustom: Boolean,
    onSelectPreset: (Int) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        presetsMeters.forEach { m ->
            val selected = !isCustom && selectedMeters == m
            FilterChip(
                selected = selected,
                onClick = { onSelectPreset(m) },
                label = { Text(formatMeters(m), color = if (selected) Color.Black else Color.White) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White)
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = onCustomClick,
            label = {
                Text(
                    if (isCustom) formatMeters(selectedMeters) else "Custom",
                    color = if (isCustom) Color.Black else Color.White
                )
            },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White)
        )
    }
}

/** Custom-distance dialog. When [allowUnitToggle] is true the user can enter either km or m
 * (used for the main alarm radius); otherwise the field is meters-only (final alarm). */
@Composable
fun KnockCustomDistanceDialog(
    title: String,
    minMeters: Int,
    maxMeters: Int,
    allowUnitToggle: Boolean,
    initialMeters: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (allowUnitToggle) (initialMeters / 1000.0).let { if (it >= 1) "%.1f".format(it).removeSuffix(".0") else initialMeters.toString() } else initialMeters.toString()) }
    var useKm by remember { mutableStateOf(allowUnitToggle && initialMeters >= 1000) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        title = { Text(title, color = Color.White) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (useKm) "Kilometers" else "Meters", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (allowUnitToggle) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = !useKm,
                            onClick = { useKm = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray)
                        )
                        Text("Meters", color = Color.White, modifier = Modifier.padding(end = 16.dp))
                        RadioButton(
                            selected = useKm,
                            onClick = { useKm = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray)
                        )
                        Text("Kilometers", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toDoubleOrNull()
                if (value != null) {
                    val meters = (if (useKm) value * 1000 else value).toInt().coerceIn(minMeters, maxMeters)
                    onConfirm(meters)
                }
                onDismiss()
            }) { Text("Set", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        }
    )
}
