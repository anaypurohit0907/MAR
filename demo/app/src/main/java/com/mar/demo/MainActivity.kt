package com.mar.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.agent.sdk.telemetry.LiveMetricsTracker
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private lateinit var scheduler: MarWorkScheduler
    private lateinit var metricsTracker: LiveMetricsTracker
    private lateinit var metricsView: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mock simple UI programmatically to avoid layout XML coupling
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val titleView = TextView(this).apply {
            text = "MAR Runtime Demo"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        metricsView = TextView(this).apply {
            text = "Metrics initializing..."
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }

        val installBirthdayButton = Button(this).apply {
            text = "1-Tap Install & Run 'Birthday Agent'"
            setOnClickListener { installAndRunAgent("BirthdayGreeter") }
        }

        layout.addView(titleView)
        layout.addView(metricsView)
        layout.addView(installBirthdayButton)

        setContentView(layout)

        scheduler = MarWorkScheduler(this)
        metricsTracker = LiveMetricsTracker(this)
        
        startMetricsLoop()
    }

    private fun installAndRunAgent(agentId: String) {
        Toast.makeText(this, "Bootstrapping $agentId...", Toast.LENGTH_SHORT).show()
        // Immediately kick off the scheduled work.
        scheduler.executeAgentNow(agentId)
    }

    private fun startMetricsLoop() {
        scope.launch {
            while (isActive) {
                val metrics = metricsTracker.getMetricsReport()
                metricsView.text = """
                    LIVE SYSTEM METRICS:
                    RAM Usage: ${metrics.getLong("ram_usage_mb")} MB
                    Battery: ${String.format("%.1f", metrics.getDouble("battery_level_pct"))}%
                    Charging: ${metrics.getBoolean("is_charging")}
                    Avg TPS: ${String.format("%.2f", metrics.getDouble("tokens_per_sec"))} tok/s
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
