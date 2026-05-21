package com.mar.demo.ui.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.yaml.parser.MarYamlParser
import com.mar.demo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File

class YamlEditorFragment : Fragment() {

    private lateinit var yamlInput: MultiAutoCompleteTextView
    private var fileName: String = "new_workflow.yaml"
    private var highlighting = false

    private val suggestions = arrayOf(
        "agent:",
        "  name: \"MyAgent\"",
        "  version: \"1.0.0\"",
        "  description: \"Agent description\"",
        "hardware_requirements:",
        "  min_ram_mb: 1024",
        "  model: \"qwen2.5-0.5b\"",
        "triggers:",
        "  - type: manual",
        "  - type: notification",
        "    package: \"com.example.app\"",
        "    text_match: \"(pattern)\"",
        "    on_trigger: \"step_1\"",
        "  - type: alarm_manager",
        "    schedule: \"0 8 * * *\"",
        "tools:",
        "  - name: CalendarQuery",
        "    type: content_resolver",
        "    uri: \"content://com.android.calendar/events\"",
        "  - name: ObserveScreen",
        "    type: media_projection",
        "workflow:",
        "  step_1:",
        "    action: \"notify\"",
        "    prompt: \"Write a greeting\"",
        "    params:",
        "    on_success: \"step_2\"",
        "    on_failure: \"exit\"",
        "    on_empty: \"exit\"",
        "log_success:",
        "  action: \"log_success\""
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
            setTextColor(resources.getColor(R.color.kite_surface, null))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        val validateButton = Button(context).apply {
            text = "Validate YAML"
            setBackgroundColor(resources.getColor(R.color.kite_green, null))
            setTextColor(resources.getColor(R.color.kite_surface, null))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
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
            setBackgroundResource(R.color.kite_surface)
            textSize = 13f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(32, 32, 32, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            setText(arguments?.getString("DEFAULT_YAML") ?: getDefaultYaml())
            if (arguments?.getString("FILE_NAME") != null) {
                fileName = arguments!!.getString("FILE_NAME")!!
            }

            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, suggestions)
            setAdapter(adapter)
            setTokenizer(YamlTokenizer())
            threshold = 1

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!highlighting) {
                        highlighting = true
                        applyYamlHighlighting(s)
                        highlighting = false
                    }
                }
            })
        }

        saveButton.setOnClickListener {
            val textToSave = yamlInput.text.toString()
            val safeName = getSafeNameFromYaml(textToSave)
            val fileToSaveName = if (safeName.isNotEmpty()) "$safeName.yaml" else fileName
            try {
                val file = File(context.filesDir, fileToSaveName)
                file.writeText(textToSave)
                val description = try {
                    textToSave.lines().firstOrNull { it.trim().startsWith("description:") }
                        ?.split(":")?.drop(1)?.joinToString(":")?.trim()?.replace("\"", "")
                        ?: "Custom agent"
                } catch (e: Exception) { "Custom agent" }
                val id = fileToSaveName.removeSuffix(".yaml")
                CoroutineScope(Dispatchers.IO).launch {
                    WorkflowRepository(context).save(id, safeName.replace("_", " "), description, textToSave)
                }
                Toast.makeText(context, "Saved as $fileToSaveName", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        validateButton.setOnClickListener {
            val text = yamlInput.text.toString()
            try {
                val parser = MarYamlParser()
                parser.parse(text.byteInputStream())
                Toast.makeText(context, "YAML valid", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = e.message ?: "Invalid YAML"
                Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(titleView)
        layout.addView(saveButton)
        layout.addView(validateButton)
        layout.addView(yamlInput)

        return layout
    }

    private fun applyYamlHighlighting(s: Editable?) {
        if (s == null || s.isEmpty()) return
        val text = s.toString()
        s.clearSpans()

        var pos = 0
        val lines = text.split("\n")
        for ((li, line) in lines.withIndex()) {
            val lineStart = if (li == 0) 0 else pos + 1
            val lineEnd = lineStart + line.length
            pos = lineEnd

            val trimmed = line.trimStart()
            if (trimmed.isEmpty()) continue

            val leadingSpaces = line.length - trimmed.length

            if (trimmed.startsWith("#")) {
                s.setSpan(ForegroundColorSpan(Color.GRAY), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx >= 0) {
                val keyStart = lineStart + leadingSpaces
                val keyEnd = lineStart + leadingSpaces + colonIdx
                if (keyEnd > keyStart) {
                    s.setSpan(ForegroundColorSpan(Color.rgb(66, 133, 244)), keyStart, keyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(StyleSpan(Typeface.BOLD), keyStart, keyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val value = trimmed.substring(colonIdx + 1).trim()
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    s.setSpan(ForegroundColorSpan(Color.rgb(52, 168, 83)), lineStart + leadingSpaces + colonIdx + 1 + trimmed.substring(colonIdx + 1).indexOf(value.first()), lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            if (trimmed.startsWith("- ")) {
                s.setSpan(ForegroundColorSpan(Color.rgb(251, 188, 4)), lineStart + leadingSpaces, lineStart + leadingSpaces + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun getSafeNameFromYaml(yaml: String): String {
        try {
            val lines = yaml.lines()
            for (line in lines) {
                if (line.trim().startsWith("name:")) {
                    return line.split(":")[1].trim().replace("\"", "").replace(" ", "_")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("YamlEditor", "Failed to extract name from YAML: ${e.message}")
        }
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
  model: "qwen2.5-0.5b"

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

    class YamlTokenizer : MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i > 0 && text[i - 1] != '\n') {
                i--
            }
            return i
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i < text.length && text[i] != '\n') {
                i++
            }
            return i
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            var i = text.length
            while (i > 0 && text[i - 1] == ' ') i--
            return if (i > 0 && text[i - 1] == '\n') text
            else text.substring(0, i) + "\n"
        }
    }
}
