package com.mar.demo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.agent.sdk.telemetry.LiveMetricsTracker
import kotlinx.coroutines.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    private lateinit var scheduler: MarWorkScheduler
    private lateinit var metricsTracker: LiveMetricsTracker
    private lateinit var metricsView: TextView
    private lateinit var statusView: TextView
    private lateinit var consoleView: TextView
    private lateinit var mainScrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mainScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Mock simple UI programmatically to avoid layout XML coupling
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "MAR Runtime Explorer"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        
        statusView = TextView(this).apply {
            text = "Agent Status: Idle"
            textSize = 16f
            setTextColor(Color.parseColor("#1976D2")) // Material Blue
            setPadding(0, 16, 0, 16)
        }
        
        metricsView = TextView(this).apply {
            text = "Metrics initializing..."
            textSize = 14f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 16, 0, 32)
        }


        consoleView = TextView(this).apply {
            text = "> System Ready. Awaiting commands..."
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.parseColor("#4CAF50")) // Android Logcat Green
            setBackgroundColor(Color.parseColor("#121212")) // Dark theme bg
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT // Let it grow with the content
            ).apply {
                setMargins(0, 48, 0, 48)
            }
        }

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 32)
        }

        val promptInput = android.widget.EditText(this).apply {
            hint = "Ask the AI (e.g. 'set a 5 min timer')"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val runDynamicButton = Button(this).apply {
            text = "Run Agent"
            setOnClickListener { 
                val text = promptInput.text.toString()
                if (text.isNotBlank()) {
                    installAndRunAgent("VersatileAgent", text)
                } else {
                    installAndRunAgent("VersatileAgent", "set a timer for 10 minutes")
                }
            }
        }
        
        inputLayout.addView(promptInput)
        inputLayout.addView(runDynamicButton)

        layout.addView(titleView)
        layout.addView(statusView)
        layout.addView(metricsView)
        layout.addView(inputLayout)
        
        setupModelDownloaderUI(layout)
        
        // Add console at the very bottom
        val consoleTitle = TextView(this).apply { 
            text = "Live Execution Logs"
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 32, 0, 0)
        }
        layout.addView(consoleTitle)
        layout.addView(consoleView)

        mainScrollView.addView(layout)
        setContentView(mainScrollView)

        scheduler = MarWorkScheduler(this)
        metricsTracker = LiveMetricsTracker(this)
        
        startMetricsLoop()
    }

    private fun appendLog(msg: String) {
        val current = consoleView.text.toString()
        consoleView.text = "$current\n> $msg"
        mainScrollView.post { mainScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun setupModelDownloaderUI(layout: LinearLayout) {
        val downloadStatusView = TextView(this).apply {
            text = "Model Status: Not Downloaded"
            textSize = 14f
            setPadding(0, 32, 0, 16)
        }
        val downloadButton = Button(this).apply {
            text = "Download Qwen 0.5B (GGUF)"
            setOnClickListener {
                scope.launch {
                    ModelDownloader.downloadModel(this@MainActivity).collect { state ->
                        when(state) {
                            is ModelDownloader.DownloadState.Downloading -> {
                                downloadStatusView.text = "Downloading: ${state.progressPct}% (${String.format("%.1f", state.downloadedMb)} / ${String.format("%.1f", state.totalMb)} MB)"
                                this@apply.isEnabled = false
                            }
                            is ModelDownloader.DownloadState.Success -> {
                                downloadStatusView.text = "Model Status: Ready (${state.file.name})"
                                this@apply.isEnabled = false
                                this@apply.text = "Model Downloaded!"
                                appendLog("Downloaded model to: ${state.file.absolutePath}")
                            }
                            is ModelDownloader.DownloadState.Error -> {
                                downloadStatusView.text = "Error: ${state.message}"
                                this@apply.isEnabled = true
                                appendLog("Failed to download model: ${state.message}")
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
        layout.addView(downloadStatusView)
        layout.addView(downloadButton)
        
        // Initial check
        if (ModelDownloader.getLocalModelPath(this) != null) {
            downloadStatusView.text = "Model Status: Ready (qwen2.5-0.5b.gguf)"
            downloadButton.isEnabled = false
            downloadButton.text = "Model Downloaded!"
        }
    }

    private fun installAndRunAgent(agentId: String, userIntent: String? = null) {
        Toast.makeText(this, "Bootstrapping $agentId...", Toast.LENGTH_SHORT).show()
        appendLog("User Prompt: \"${userIntent ?: "N/A"}\"")
        appendLog("Triggering worker: mar_agent_$agentId")
        // Immediately kick off the scheduled work.
        scheduler.executeAgentNow(agentId, userIntent)

        // Observe the exact worker status
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("mar_agent_$agentId")
            .observe(this) { workInfos ->
                if (!workInfos.isNullOrEmpty()) {
                    val info = workInfos[0]
                    statusView.text = "Agent $agentId Status: " + info.state.name
                    
                    if (info.state.isFinished) {
                        val nativeLogs = info.outputData.getString("native_logs")
                        if (nativeLogs != null) {
                            appendLog("Native Trace Output:\n$nativeLogs")
                        } else {
                            appendLog("Worker finished, but no native logs returned.")
                        }
                    }
                }
            }
    }

    private fun startMetricsLoop() {
        scope.launch {
            while (isActive) {
                val metrics = metricsTracker.getMetricsReport()
                metricsView.text = """
                    [ LIVE TELEMETRY ]
                    RAM Usage: ${metrics.getLong("ram_usage_mb")} MB
                    Battery:   ${String.format("%.1f", metrics.getDouble("battery_level_pct"))}%
                    Charging:  ${metrics.getBoolean("is_charging")}
                    Avg TPS:   ${String.format("%.2f", metrics.getDouble("tokens_per_sec"))} tok/s
                """.trimIndent()
                delay(2000) // Update every 2 seconds
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
