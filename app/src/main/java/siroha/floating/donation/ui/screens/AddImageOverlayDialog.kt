package siroha.floating.donation.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.model.OverlayType

@Composable
fun AddImageOverlayDialog(
    overlay: OverlayConfig? = null,
    onSave: (OverlayConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = overlay != null
    val context = LocalContext.current

    var name by remember { mutableStateOf(overlay?.name ?: "") }
    var imagePath by remember { mutableStateOf(overlay?.imagePath ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistent permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            imagePath = uri.toString()
            imageError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit Image Overlay" else "Add Image Overlay")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Overlay Name") },
                    placeholder = { Text("e.g., Logo") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = if (imagePath.isNotEmpty()) "Image selected" else "",
                    onValueChange = {},
                    label = { Text("Image") },
                    readOnly = true,
                    isError = imageError != null,
                    supportingText = imageError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (imagePath.isEmpty()) "Choose Image" else "Change Image")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        hasError = true
                    }
                    if (imagePath.isBlank()) {
                        imageError = "Please select an image"
                        hasError = true
                    }
                    if (!hasError) {
                        val config = (overlay ?: OverlayConfig()).copy(
                            name = name.trim(),
                            imagePath = imagePath,
                            type = OverlayType.IMAGE
                        )
                        onSave(config)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
