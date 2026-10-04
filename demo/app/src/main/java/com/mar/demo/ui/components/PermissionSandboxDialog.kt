package com.mar.demo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.theme.MarColors

@Composable
fun PermissionSandboxDialog(
    agentName: String,
    requiredCapabilities: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MarColors.Orange)
                Spacer(Modifier.width(8.dp))
                Text("Permission Required", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "The agent '$agentName' is requesting access to the following sensitive capabilities:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                requiredCapabilities.forEach { cap ->
                    Text("• $cap", style = MaterialTheme.typography.bodyMedium, color = MarColors.Red)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Do you want to allow this execution?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarColors.TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Allow & Run", color = MarColors.Blue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MarColors.TextSecondary)
            }
        }
    )
}
