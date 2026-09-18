package siroha.floating.donation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import siroha.floating.donation.util.UrlValidator

@Composable
fun AddEditOverlayDialog(
    overlay: OverlayConfig? = null,
    onSave: (OverlayConfig) -> Unit,
    onDismiss: () -> Unit,
    onPreview: ((String) -> Unit)? = null
) {
    val isEdit = overlay != null
    val context = LocalContext.current

    var name by remember { mutableStateOf(overlay?.name ?: "") }
    var url by remember { mutableStateOf(overlay?.url ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit Overlay" else "Add Overlay")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Overlay Name *") },
                    placeholder = { Text("e.g., Donation Alert") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // URL field
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = null
                    },
                    label = { Text("Overlay URL") },
                    placeholder = { Text("https://example.com/overlay") },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    },
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val error = UrlValidator.getErrorMessage(url)
                            if (error != null) {
                                urlError = error
                            } else {
                                Toast.makeText(context, "URL is valid!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test URL")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (onPreview != null) {
                        OutlinedButton(
                            onClick = {
                                if (UrlValidator.isValid(url)) {
                                    onPreview(url)
                                } else {
                                    urlError = UrlValidator.getErrorMessage(url)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate
                    var hasError = false
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        hasError = true
                    }
                    val urlErr = UrlValidator.getErrorMessage(url)
                    if (urlErr != null) {
                        urlError = urlErr
                        hasError = true
                    }
                    if (!hasError) {
                        val config = (overlay ?: OverlayConfig()).copy(
                            name = name.trim(),
                            url = UrlValidator.sanitize(url),
                            type = OverlayType.WEB
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
