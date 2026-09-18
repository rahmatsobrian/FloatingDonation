package siroha.floating.donation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.ui.components.AppSwitch

@Composable
fun OverlayCustomizationDialog(
    config: OverlayConfig,
    onSave: (OverlayConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var width by remember { mutableIntStateOf(config.width) }
    var height by remember { mutableIntStateOf(config.height) }
    var positionX by remember { mutableIntStateOf(config.positionX) }
    var positionY by remember { mutableIntStateOf(config.positionY) }
    var opacity by remember { mutableFloatStateOf(config.opacity) }
    var keepPosition by remember { mutableStateOf(config.keepPosition) }
    var alwaysOnTop by remember { mutableStateOf(config.alwaysOnTop) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize: ${config.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Size
                Text("Size", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width.toString(),
                        onValueChange = { width = it.toIntOrNull() ?: width },
                        label = { Text("Width") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height.toString(),
                        onValueChange = { height = it.toIntOrNull() ?: height },
                        label = { Text("Height") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Position
                Text("Position", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = positionX.toString(),
                        onValueChange = { positionX = it.toIntOrNull() ?: positionX },
                        label = { Text("X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = positionY.toString(),
                        onValueChange = { positionY = it.toIntOrNull() ?: positionY },
                        label = { Text("Y") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opacity
                Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Keep Position
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep Position")
                    AppSwitch(checked = keepPosition, onCheckedChange = { keepPosition = it })
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Always On Top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Always On Top")
                    AppSwitch(checked = alwaysOnTop, onCheckedChange = { alwaysOnTop = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = config.copy(
                        width = width.coerceAtLeast(100),
                        height = height.coerceAtLeast(100),
                        positionX = positionX.coerceAtLeast(0),
                        positionY = positionY.coerceAtLeast(0),
                        opacity = opacity,
                        keepPosition = keepPosition,
                        alwaysOnTop = alwaysOnTop
                    )
                    onSave(updated)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
