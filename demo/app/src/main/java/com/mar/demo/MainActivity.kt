package com.mar.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import androidx.core.app.ActivityCompat
import com.mar.agent.sdk.db.WorkflowEntity
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.models.ModelEntity
import com.mar.agent.sdk.models.DownloadProgress
import com.mar.agent.sdk.models.HfFileInfo
import com.mar.agent.sdk.models.HuggingFaceApi
import com.mar.agent.sdk.models.ModelRepository
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.demo.ui.components.GlassCard
import com.mar.demo.ui.components.GlassCardCompact
import com.mar.demo.ui.screens.ActiveAgentsScreen
import com.mar.demo.ui.screens.AgentExecutionScreen
import com.mar.demo.ui.screens.AgentItem
import com.mar.demo.ui.screens.LibraryItem
import com.mar.demo.ui.viewmodel.ExecutionState
import com.mar.demo.ui.screens.LibraryScreen
import com.mar.demo.ui.screens.MainScreen
import com.mar.demo.ui.extractModelFromYaml
import com.mar.demo.ui.setModelInYaml
import com.mar.demo.ui.relativeTime
import com.mar.demo.ui.theme.MarColors
import com.mar.demo.ui.theme.MarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.init(applicationContext)
        FileLogger.log("MainActivity Compose onCreate")

        checkPermissions()
        seedDefaultWorkflows(this)
        WorkManager.getInstance(this).cancelAllWork()
        MarWorkScheduler(this).scheduleTriggerSync()

        CoroutineScope(Dispatchers.IO).launch {
            ModelRepository(this@MainActivity).scanAndRegisterModels()

            val prefs = this@MainActivity.getSharedPreferences("mar_execution", Context.MODE_PRIVATE)
            val wasExecuting = prefs.getBoolean("is_executing", false)
            if (wasExecuting) {
                val crashedAgent = prefs.getString("executing_agent", "Unknown") ?: "Unknown"
                prefs.edit().clear().apply()
                ExecutionState.start(crashedAgent, 0, modelLoaded = false)
                ExecutionState.complete(false)
                FileLogger.log("Recovered from crash: $crashedAgent was RUNNING")
            }
        }

        setContent {
            MarTheme {
                MainScreen(
                    activeContent = { ActiveTab() },
                    libraryContent = { LibraryTab() },
                    executionContent = { AgentExecutionScreen() },
                    settingsContent = { SettingsTab() }
                )
            }
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }
}

@Composable
private fun ActiveTab() {
    val ctx = LocalContext.current
    val repo = remember { WorkflowRepository(ctx) }
    val workflows by repo.getAllFlow().collectAsState(initial = emptyList())
    val modelRepo = remember { ModelRepository(ctx) }
    val allModels by modelRepo.getAllFlow().collectAsState(initial = emptyList())
    var pickerWfId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val enabledAgents = workflows.filter { it.enabled }.map { wf ->
        val modelId = extractModelFromYaml(wf.yaml)
        val modelPath = modelId?.let { modelRepo.findLocalModelPath(it) }
        AgentItem(
            id = wf.id,
            name = wf.name,
            description = wf.description,
            modelName = modelId,
            modelReady = modelPath != null,
            lastRun = if (wf.lastRunAt > 0) relativeTime(wf.lastRunAt) else null,
            errorCount = wf.errorCount
        )
    }

    ModelPickerDialog(
        pickerWfId = pickerWfId,
        workflows = workflows,
        allModels = allModels,
        onDismiss = { pickerWfId = null },
        onSelect = { wfId, newModelId ->
            scope.launch {
                val wf = workflows.find { it.id == wfId } ?: return@launch
                val newYaml = setModelInYaml(wf.yaml, newModelId)
                repo.save(wfId, wf.name, wf.description, newYaml)
            }
            pickerWfId = null
        }
    )

    ActiveAgentsScreen(
        enabledAgents = enabledAgents,
        onRunAgent = { id ->
            val wf = workflows.find { it.id == id }
            if (wf != null) {
                MarWorkScheduler(ctx).executeAgentNow(id, yamlWorkflow = wf.yaml)
            }
        },
        onViewExecution = { },
        onChangeModel = { pickerWfId = it }
    )
}

@Composable
private fun LibraryTab() {
    val ctx = LocalContext.current
    val repo = remember { WorkflowRepository(ctx) }
    val workflows by repo.getAllFlow().collectAsState(initial = emptyList())
    val modelRepo = remember { ModelRepository(ctx) }
    val allModels by modelRepo.getAllFlow().collectAsState(initial = emptyList())
    var pickerWfId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repo.importFromFilesDir() }

    val items = workflows.map { wf ->
        val modelId = extractModelFromYaml(wf.yaml)
        LibraryItem(
            id = wf.id,
            name = wf.name,
            description = wf.description,
            modelName = modelId,
            enabled = wf.enabled,
            lastRun = if (wf.lastRunAt > 0) relativeTime(wf.lastRunAt) else null,
            errorCount = wf.errorCount
        )
    }

    ModelPickerDialog(
        pickerWfId = pickerWfId,
        workflows = workflows,
        allModels = allModels,
        onDismiss = { pickerWfId = null },
        onSelect = { wfId, newModelId ->
            scope.launch {
                val wf = workflows.find { it.id == wfId } ?: return@launch
                val newYaml = setModelInYaml(wf.yaml, newModelId)
                repo.save(wfId, wf.name, wf.description, newYaml)
            }
            pickerWfId = null
        }
    )

    LibraryScreen(
        items = items,
        onRun = { id ->
            val wf = workflows.find { it.id == id }
            if (wf != null) {
                MarWorkScheduler(ctx).executeAgentNow(id, yamlWorkflow = wf.yaml)
            }
        },
        onEdit = { id ->
            val intent = Intent(ctx, YamlShareActivity::class.java).apply {
                putExtra("agent_id", id)
            }
            ctx.startActivity(intent)
        },
        onDelete = { id ->
            scope.launch { repo.delete(id) }
        },
        onCreateNew = {
            val intent = Intent(ctx, YamlShareActivity::class.java)
            ctx.startActivity(intent)
        },
        onChangeModel = { pickerWfId = it }
    )
}

@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    val repo = remember { ModelRepository(ctx) }
    val models by repo.getAllFlow().collectAsState(initial = emptyList())
    val hasModel = models.isNotEmpty()
    val scope = rememberCoroutineScope()

    LaunchedEffect(models.isEmpty()) {
        if (models.isEmpty()) repo.scanAndRegisterModels()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MarColors.TextPrimary
        )

        Spacer(Modifier.height(16.dp))

        GlassCard {
            Text(
                "Permissions",
                style = MaterialTheme.typography.titleMedium,
                color = MarColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            SettingsButton("Notification Access") {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.height(8.dp))
            SettingsButton("Accessibility Service") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                "Downloaded Models (${models.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MarColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            if (models.isEmpty()) {
                Text(
                    "No models downloaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarColors.TextSecondary
                )
            } else {
                models.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassCardCompact(Modifier.weight(1f)) {
                            Text(
                                text = m.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MarColors.Green
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            scope.launch { repo.delete(m.id) }
                        }) {
                            Text("Delete", color = MarColors.Red, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                "Quick Download",
                style = MaterialTheme.typography.titleMedium,
                color = MarColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            val curatedModels = remember {
                listOf(
                    ModelDownloader.CuratedModel("Gemma 2 2B (Google)", "bartowski/gemma-2-2b-it-GGUF", "gemma-2-2b-it-Q4_K_M.gguf", 1_550_000_000L),
                    ModelDownloader.CuratedModel("Qwen2.5 0.5B", "Qwen/Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf", 370_000_000L),
                    ModelDownloader.CuratedModel("DeepSeek R1 1.5B", "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF", "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf", 1_000_000_000L),
                )
            }
            curatedModels.forEach { cm ->
                val dlState = remember(cm.hfFile) { mutableStateOf<ModelDownloader.DownloadState>(ModelDownloader.DownloadState.Idle) }
                val already = models.any { it.filePath.contains(cm.hfFile) }
                when (val s = dlState.value) {
                    is ModelDownloader.DownloadState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { s.progressPct / 100f },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            color = MarColors.Blue,
                            trackColor = MarColors.Blue.copy(alpha = 0.15f)
                        )
                        Text("${cm.label}: ${"%.0f".format(s.downloadedMb)} / ${"%.0f".format(s.totalMb)} MB (${s.progressPct}%)",
                            style = MaterialTheme.typography.bodySmall, color = MarColors.TextSecondary)
                    }
                    is ModelDownloader.DownloadState.Success -> {
                        Text("${cm.label} ✓", style = MaterialTheme.typography.bodySmall, color = MarColors.Green)
                    }
                    is ModelDownloader.DownloadState.Error -> {
                        Text("${cm.label}: ${s.message}", style = MaterialTheme.typography.bodySmall, color = MarColors.Red)
                    }
                    is ModelDownloader.DownloadState.Idle -> {
                        if (already) {
                            Text("${cm.label} ✓", style = MaterialTheme.typography.bodySmall, color = MarColors.Green)
                        } else {
                            SettingsButton("Download ${cm.label}") {
                                scope.launch {
                                    ModelDownloader.downloadModel(ctx, cm).collect { d ->
                                        dlState.value = d
                                    }
                                    repo.scanAndRegisterModels()
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MarColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "MAR — Mobile Agent Runtime v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MarColors.TextSecondary
            )
            Text(
                "A YAML-driven agent runtime for Android",
                style = MaterialTheme.typography.bodySmall,
                color = MarColors.TextTertiary
            )
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MarColors.Blue.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = MarColors.Blue)
    }
}

@Composable
private fun ModelPickerDialog(
    pickerWfId: String?,
    workflows: List<WorkflowEntity>,
    allModels: List<ModelEntity>,
    onDismiss: () -> Unit,
    onSelect: (wfId: String, newModelId: String) -> Unit
) {
    pickerWfId?.let { wfId ->
        val wf = workflows.find { it.id == wfId }
        if (wf == null) { onDismiss(); return }
        val currentModel = extractModelFromYaml(wf.yaml)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick model for ${wf.name}") },
            text = {
                if (allModels.isEmpty()) {
                    Text("No models downloaded. Go to Settings tab to download one.")
                } else {
                    LazyColumn {
                        items(allModels) { m ->
                            val isCurrent = m.id == currentModel || m.filePath.contains(currentModel ?: "")
                            TextButton(
                                onClick = {
                                    val modelId = m.id
                                    onSelect(wfId, modelId)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${m.name}${if (isCurrent) " ✓" else ""}",
                                    color = if (isCurrent) MarColors.Green else MarColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }
}

private fun seedDefaultWorkflows(context: Context) {
    val dir = context.filesDir ?: return
    val defaultWorkflows = listOf("BirthdayAgent.yaml", "TelegramBirthdayAgent.yaml")
    for (name in defaultWorkflows) {
        val target = File(dir, name)
        if (target.exists()) continue
        try {
            val text = context.assets.open(name).bufferedReader().use { it.readText() }
            target.writeText(text)
            FileLogger.log("Seeded default workflow: $name")
        } catch (e: Exception) {
            FileLogger.log("Skip seeding $name: ${e.message}")
        }
    }
}
