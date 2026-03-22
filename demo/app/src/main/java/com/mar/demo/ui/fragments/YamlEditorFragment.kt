package com.mar.demo.ui.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mar.demo.R
import java.io.File

class YamlEditorFragment : Fragment() {

    private lateinit var yamlInput: MultiAutoCompleteTextView
    private var fileName: String = "new_workflow.yaml" // We can generate based on time or read from actual Name field

    private val suggestions = arrayOf(
        "model: \"qwen3.5-0.8b-q4f16\"",
        "model: \"qwen2.5-0.5b\"",
        "triggers:\n  - type: alarm_manager\n    schedule: \"0 8 * * *\"",
        "action: \"llm_draft_message\"",
        "action: \"CalendarQuery\"",
        "action: \"UITapper\"",
        "action: \"log_success\"",
        "type: content_resolver",
        "type: accessibility_service",
        "hardware_requirements:\n  min_ram_mb: 1024",
        "on_success: \"step_2\"",
        "on_empty: \"exit\""
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundResource(R.color.kite_background)
        }

        val titleView = TextView(context).apply {
            text = "Workflow Editor"
            textSize = 28f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val saveButton = Button(context).apply {
            text = "Save Workflow"
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            setTextColor(resources.getColor(R.color.kite_surface_white, null))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        yamlInput = MultiAutoCompleteTextView(context).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 
                1f
            ).apply { setMargins(0, 32, 0, 0) }
            layoutParams = params
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setBackgroundResource(R.color.kite_surface_white)
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(32, 32, 32, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or 
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            
            setText(arguments?.getString("DEFAULT_YAML") ?: getDefaultYaml())
            if(arguments?.getString("FILE_NAME") != null) {
                fileName = arguments!!.getString("FILE_NAME")!!
            }
            
            // Setup Autocomplete
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, suggestions)
            setAdapter(adapter)
            setTokenizer(YamlTokenizer())
            threshold = 1
        }
        
        saveButton.setOnClickListener {
            val textToSave = yamlInput.text.toString()
            val safeName = getSafeNameFromYaml(textToSave)
            val fileToSaveName = if(safeName.isNotEmpty()) "$safeName.yaml" else fileName
            try {
                val file = File(context.filesDir, fileToSaveName)
                file.writeText(textToSave)
                Toast.makeText(context, "Saved as $fileToSaveName", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(titleView)
        layout.addView(saveButton)
        layout.addView(yamlInput)

        return layout
    }
    
    private fun getSafeNameFromYaml(yaml: String): String {
        try {
            val lines = yaml.lines()
            for (line in lines) {
                if(line.trim().startsWith("name:")) {
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
  version: "1.0.0"
  description: "A simple custom agent."
  
hardware_requirements:
  min_ram_mb: 1024
  model: "qwen3.5-0.8b-q4f16"

triggers:
  - type: manual
    
tools:
  - name: CalendarQuery
    type: content_resolver
    uri: "content://com.android.calendar/events"

workflow:
  step_1:
    action: "log_success"
""".trimIndent()
    }
    
    // Custom Tokenizer to pop suggestions after newline or spaces
    class YamlTokenizer : MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i > 0 && text[i - 1] != '\n' && text[i - 1] != ' ') {
                i--
            }
            return i
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var i = cursor
            val len = text.length
            while (i < len) {
                if (text[i] == '\n' || text[i] == ' ') {
                    return i
                }
                i++
            }
            return len
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            var i = text.length
            while (i > 0 && text[i - 1] == ' ') {
                i--
            }
            return if (i > 0 && text[i - 1] == '\n') {
                text
            } else {
                text.substring(0, i) + "\n"
            }
        }
    }
}
