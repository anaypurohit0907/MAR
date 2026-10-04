package com.mar.demo.ui.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.demo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class YamlEditorFragment : Fragment() {

    private lateinit var yamlInput: EditText
    private var existingAgentId: String? = null
    private var defaultFileName: String = "new_workflow.yaml"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        
        // Retrieve args
        existingAgentId = arguments?.getString("AGENT_ID")
        val defaultYaml = arguments?.getString("DEFAULT_YAML") ?: getDefaultYaml()
        defaultFileName = arguments?.getString("FILE_NAME") ?: "new_workflow.yaml"

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.color.kite_background)
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(context).apply {
            text = if (existingAgentId != null) "Edit Workflow" else "New Workflow"
            textSize = 20f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val saveButton = Button(context).apply {
            text = "Save"
            textSize = 12f
            setPadding(0, 0, 0, 0)
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            setTextColor(Color.WHITE)
        }

        topBar.addView(titleView)
        topBar.addView(saveButton)

        yamlInput = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            
            isFocusable = true
            isFocusableInTouchMode = true
            setText(defaultYaml)
        }

        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            addView(yamlInput)
        }

        saveButton.setOnClickListener {
            val textToSave = yamlInput.text.toString()
            val safeName = getSafeNameFromYaml(textToSave)
            
            // Use existing ID if we have one, otherwise generate from name
            val id = existingAgentId ?: if (safeName.isNotEmpty()) safeName else "custom_agent_${System.currentTimeMillis()}"
            
            val displayName = if (safeName.isNotEmpty()) safeName.replace("_", " ") else "Custom Agent"
            
            try {
                val description = try {
                    textToSave.lines().firstOrNull { it.trim().startsWith("description:") }
                        ?.split(":")?.drop(1)?.joinToString(":")?.trim()?.replace("\"", "")
                        ?: "Custom agent"
                } catch (e: Exception) { "Custom agent" }

                CoroutineScope(Dispatchers.IO).launch {
                    WorkflowRepository(context).save(id, displayName, description, textToSave)
                }
                Toast.makeText(context, "Saved changes to $id", Toast.LENGTH_SHORT).show()
                activity?.finish()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(topBar)
        layout.addView(scroll)

        return layout
    }

    private fun getSafeNameFromYaml(yaml: String): String {
        try {
            val lines = yaml.lines()
            for (line in lines) {
                if (line.trim().startsWith("name:")) {
                    return line.split(":")[1].trim().replace("\"", "").replace(" ", "_")
                }
            }
        } catch (e: Exception) {}
        return ""
    }

    private fun getDefaultYaml(): String {
        return """
agent:
  name: "New_Agent"
  description: "A simple custom agent."

workflow:
  step_1:
    action: "log_success"
""".trimIndent()
    }
}
