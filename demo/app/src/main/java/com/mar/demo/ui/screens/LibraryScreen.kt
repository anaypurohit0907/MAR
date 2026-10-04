package com.mar.demo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.components.GlassCard
import com.mar.demo.ui.components.GlassCardCompact
import com.mar.demo.ui.theme.MarColors

data class LibraryItem(
    val id: String,
    val name: String,
    val description: String,
    val modelName: String?,
    val enabled: Boolean,
    val lastRun: String?,
    val errorCount: Int
)

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreateNew: () -> Unit,
    onShare: (String) -> Unit,
    onScanQr: () -> Unit,
    onChangeModel: ((workflowId: String) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Workflow Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MarColors.TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onScanQr,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MarColors.Green.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Scan", tint = MarColors.Green)
                    }
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MarColors.Blue.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create", tint = MarColors.Blue)
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = MarColors.Blue)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No workflows yet.\nTap + to create one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MarColors.TextSecondary
                    )
                }
            }
        }

        items(items) { item ->
            LibraryCard(
                item = item,
                onRun = { onRun(item.id) },
                onEdit = { onEdit(item.id) },
                onDelete = { onDelete(item.id) },
                onShare = { onShare(item.id) },
                onChangeModel = onChangeModel?.let { { it(item.id) } }
            )
        }
    }
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onChangeModel: (() -> Unit)? = null
) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MarColors.TextPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (item.enabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.enabled) MarColors.Green else MarColors.TextTertiary
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.modelName?.let { model ->
                GlassCardCompact(
                    modifier = if (onChangeModel != null) Modifier.clickable { onChangeModel() } else Modifier
                ) {
                    Text(model, style = MaterialTheme.typography.bodySmall, color = MarColors.TextSecondary)
                }
            }
            item.lastRun?.let { run ->
                GlassCardCompact {
                    Text(run, style = MaterialTheme.typography.bodySmall, color = MarColors.TextTertiary)
                }
            }
            if (item.errorCount > 0) {
                GlassCardCompact {
                    Text(
                        "${item.errorCount} err",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.Red
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MarColors.Red.copy(alpha = 0.7f))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Share", tint = MarColors.Blue.copy(alpha = 0.8f))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", tint = MarColors.TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onRun,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarColors.Blue.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, "Run", tint = MarColors.Blue)
                Spacer(Modifier.width(4.dp))
                Text("Run", color = MarColors.Blue)
            }
        }
    }
}
