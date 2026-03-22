package com.mar.demo.ui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mar.agent.sdk.work.MarAgentWorker
import com.mar.demo.R
import java.io.File
import org.yaml.snakeyaml.Yaml

class ActiveAgentsFragment : Fragment() {

    private val statusViews = mutableMapOf<String, TextView>()

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mar.agent.PROGRESS") {
                val agentId = intent.getStringExtra("agentId") ?: return
                val status = intent.getStringExtra("status") ?: return
                
                statusViews[agentId]?.let { tv ->
                    tv.text = "Status: $status"
                    tv.setTextColor(resources.getColor(R.color.kite_blue, null)) // Active coloring
                }
            } else if (intent?.action == "com.mar.agent.COMPLETE") {
                val agentId = intent.getStringExtra("agentId") ?: return
                
                statusViews[agentId]?.let { tv ->
                    tv.text = "Status: Completed (Tap to Run Again)"
                    tv.setTextColor(resources.getColor(R.color.kite_green, null))
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        statusViews.clear() // Prevent memory leaks across fragment reconstruction
        val context = requireContext()
        val scrollView = ScrollView(context).apply {
            setBackgroundResource(R.color.kite_background)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(context).apply {
            text = "Active Agents"
            textSize = 28f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        layout.addView(titleView)

        val filesDir = context.filesDir
        val yamlFiles = filesDir.listFiles { _, name -> name.endsWith(".yaml") } ?: emptyArray()

        if (yamlFiles.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No active agents found."
                textSize = 16f
                setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                setPadding(0, 32, 0, 0)
                gravity = android.view.Gravity.CENTER
            }
            layout.addView(emptyText)
        } else {
            val yamlParser = Yaml()
            yamlFiles.forEach { file ->
                var agentName = file.nameWithoutExtension
                var agentStatus = "Status: Active (Tap to Run)"
                
                try {
                    val map = yamlParser.load<Map<String, Any>>(file.readText())
                    val agentMap = map["agent"] as? Map<*, *>
                    if (agentMap != null) {
                        agentMap["name"]?.toString()?.let { agentName = it }
                    }
                } catch (e: Exception) {
                    agentStatus = "Status: Parsing error"
                }

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.color.kite_surface_white)
                    setPadding(32, 32, 32, 32)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 24) }
                    layoutParams = params
                    elevation = 4f
                }
                
                val title = TextView(context).apply {
                    text = agentName
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.kite_text_primary, null))
                }
                
                val status = TextView(context).apply {
                    text = agentStatus
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.kite_green, null))
                    setPadding(0, 8, 0, 0)
                }
                
                // Store reference for dynamic updates
                statusViews[agentName] = status

                card.addView(title)
                card.addView(status)
                
                card.setOnClickListener {
                    Toast.makeText(context, "Running agent: $agentName...", Toast.LENGTH_SHORT).show()
                    status.text = "Status: Initializing worker..."
                    status.setTextColor(resources.getColor(R.color.kite_blue, null))
                    
                    try {
                        val workRequest = OneTimeWorkRequestBuilder<MarAgentWorker>()
                            .setInputData(workDataOf(
                                MarAgentWorker.KEY_AGENT_ID to agentName,
                                "yaml_workflow" to file.readText()
                            ))
                            .build()
                        WorkManager.getInstance(context).enqueue(workRequest)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error queuing worker: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                layout.addView(card)
            }
        }

        scrollView.addView(layout)

        return scrollView
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.mar.agent.PROGRESS")
            addAction("com.mar.agent.COMPLETE")
        }
        ContextCompat.registerReceiver(requireContext(), progressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(progressReceiver)
    }
}
