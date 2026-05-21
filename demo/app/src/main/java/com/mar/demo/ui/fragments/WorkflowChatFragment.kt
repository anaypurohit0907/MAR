package com.mar.demo.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mar.agent.sdk.core.ChatMessage
import com.mar.agent.sdk.core.CloudClient
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.demo.R
import com.mar.demo.ui.sectionTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowChatFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var messagesContainer: LinearLayout
    private lateinit var inputField: EditText
    private val chatHistory = mutableListOf<ChatMessage>()
    private var savedYaml: String? = null
    private var saveBtn: Button? = null
    private var loadingView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        prefs = ctx.getSharedPreferences("mar_settings", 0)

        val scrollView = ScrollView(ctx).apply { setBackgroundResource(R.color.kite_background) }

        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        outer.addView(sectionTitle(ctx, "AI Workflow Chat"))

        messagesContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMessage("system", "Describe what you want the agent to do. I'll generate a YAML workflow.")

        inputField = EditText(ctx).apply {
            hint = "e.g. Check my calendar..."
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setHintTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setBackgroundResource(R.drawable.card_background)
            setPadding(16, 12, 16, 12)
        }

        val sendBtn = Button(ctx).apply {
            text = "Send"
            setTextColor(resources.getColor(R.color.kite_surface, null))
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            setPadding(24, 12, 24, 12)
            setOnClickListener { sendMessage() }
        }

        saveBtn = Button(ctx).apply {
            text = "Save as Workflow"
            setTextColor(resources.getColor(R.color.kite_surface, null))
            setBackgroundColor(resources.getColor(R.color.kite_green, null))
            setPadding(24, 12, 24, 12)
            visibility = View.GONE
            setOnClickListener { saveWorkflow() }
        }

        val copyBtn = Button(ctx).apply {
            text = "Copy All"
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setBackgroundResource(R.drawable.card_background)
            setPadding(24, 12, 24, 12)
            setOnClickListener { copyAll() }
        }

        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(sendBtn.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(saveBtn.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        outer.addView(messagesContainer)
        outer.addView(inputField)
        outer.addView(buttons)
        outer.addView(copyBtn)

        scrollView.addView(outer)
        return scrollView
    }

    private fun addMessage(role: String, text: String) {
        val isUser = role == "user"
        val bg = if (isUser) R.color.kite_blue else R.color.kite_surface
        val textColor = if (isUser) android.R.color.white else R.color.kite_text_primary

        val bubble = TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(R.drawable.card_background)
            setBackgroundColor(resources.getColor(bg, null))
            setTextColor(resources.getColor(textColor, null))
            textSize = 14f
            setPadding(12, 10, 12, 10)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 4) }
            layoutParams = params
        }
        messagesContainer.addView(bubble)
    }

    private fun copyAll() {
        val sb = StringBuilder()
        for (msg in chatHistory) {
            val label = if (msg.role == "user") "You" else "AI"
            sb.appendLine("$label: ${msg.text}")
        }
        val clip = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("MAR Chat", sb.toString()))
        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage() {
        val userInput = inputField.text.toString().trim()
        if (userInput.isEmpty()) return

        val apiKey = prefs.getString("api_key", null)
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Set API key in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        savedYaml = null
        saveBtn?.visibility = View.GONE
        addMessage("user", userInput)
        inputField.text.clear()

        loadingView = TextView(requireContext()).apply {
            this.text = "Thinking..."
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(12, 10, 12, 10)
        }
        messagesContainer.addView(loadingView)

        CoroutineScope(Dispatchers.Main).launch {
            val response = withContext(Dispatchers.IO) {
                val baseUrl = prefs.getString("api_base_url",
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent") ?: ""
                val model = prefs.getString("api_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
                val client = CloudClient(baseUrl, apiKey, model)
                if (chatHistory.size == 1) {
                    client.generateWorkflow(userInput, buildSystemPrompt())
                } else {
                    client.chat(chatHistory, buildSystemPrompt())
                }
            }

            loadingView?.let { messagesContainer.removeView(it) }
            loadingView = null

            if (response != null) {
                addMessage("ai", response)
                chatHistory.add(ChatMessage("model", response))
                if (response.contains("```") || response.contains("workflow:")) {
                    savedYaml = extractYamlBlock(response)
                    saveBtn?.visibility = View.VISIBLE
                }
            } else {
                addMessage("ai", "API error. Check key and endpoint in Settings.")
            }
        }
    }

    private fun buildSystemPrompt(): String {
        return """You are a YAML workflow generator for MAR (Mobile Agent Runtime) on Android.

AVAILABLE TOOLS:
- read_notifications: Params: count, max_text_length, package_filter. Returns {status, count, notifications, texts}
- llm_draft_message: On-device LLM. Put prompt at top level. Returns {draft_text}
- notify: Params: title, message, component. Returns {status, title}
- content_query: Generic ContentProvider. Params: uri, projection, selection, selection_args, sort_order. Returns {json, count}
- ui_compose: Opens app and types compose field. Params: target_app, contact, message. Returns {status, app, contact}
- web_fetch: Fetches URL plain text. Params: url. Returns {status, text, url}
- set_timer: Android timer. Params: seconds, message
- open_url: Opens URL via notification. Params: url
- hardware_flashlight: Toggle flashlight. Params: state
- read_sms: Read SMS inbox. Params: limit. Returns {json, count}
- query_contacts: Query contacts. Params: name. Returns {json, count}

TEMPLATE VARS: {today}, {now}, {today_start}, {today_end}, {step_x.output.KEY}

FORMAT:
```yaml
agent:
  name: "MyAgent"
  version: "1.0"
  description: "Description"
workflow:
  step_1:
    action: "tool_name"
    params: { key: value }
    on_success: "step_2"
    on_empty: "step_3"
    on_failure: "exit"
  step_2:
    action: "llm_draft_message"
    prompt: "Write about {step_1.output.notifications}"
    on_success: "exit"
```

Only output YAML in ```yaml block. No commentary. Chain steps with on_success/on_failure/on_empty."""
    }

    private fun extractYamlBlock(text: String): String? {
        val regex = Regex("```(?:yaml)?\\s*\\n([\\s\\S]*?)```")
        return regex.find(text)?.groupValues?.getOrElse(1) { null }?.trim()
    }

    private fun saveWorkflow() {
        val yaml = savedYaml ?: return
        val nameMatch = Regex("name:\\s*\"([^\"]+)\"").find(yaml)
        val descMatch = Regex("description:\\s*\"([^\"]+)\"").find(yaml)
        val name = nameMatch?.groupValues?.getOrElse(1) { "ai_generated" } ?: "ai_generated"
        val desc = descMatch?.groupValues?.getOrElse(1) { "Generated by AI Chat" } ?: "Generated by AI Chat"
        val id = name.replace(" ", "_")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkflowRepository(requireContext()).save(id, name, desc, yaml)
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Saved as $id.yaml", Toast.LENGTH_SHORT).show()
                    saveBtn?.visibility = View.GONE
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
