package com.mar.demo.ui.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.mar.demo.FileLogger
import com.mar.demo.ModelDownloader
import com.mar.demo.R
import com.mar.demo.ui.sectionTitle
import com.mar.runtime.agent.vision.ScreenCaptureService

class SettingsFragment : Fragment() {

    private var visionToggle: TextView? = null
    private lateinit var prefs: SharedPreferences

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        FileLogger.log("screenCaptureLauncher resultCode: ${result.resultCode}")
        try {
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val intent = Intent(requireContext(), ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("RESULT_DATA", result.data)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
                visionToggle?.let {
                    it.text = "Vision Active"
                    it.setTextColor(resources.getColor(R.color.kite_green, null))
                }
                Toast.makeText(requireContext(), "Vision Engine Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            FileLogger.log("Vision start failed: ${e.message}")
            Toast.makeText(requireContext(), "Vision start failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        prefs = ctx.getSharedPreferences("mar_settings", 0)

        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundResource(R.color.kite_background)
            isFillViewport = true
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64) // Extra bottom padding for visibility
        }

        layout.addView(sectionTitle(ctx, "MAR Settings"))

        layout.addView(section(ctx, "Core Services"))
        layout.addView(settingItem(ctx, "Notification Access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        layout.addView(settingItem(ctx, "Accessibility Service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(settingItem(ctx, "Screen Vision Engine") {
            if (ScreenCaptureService.isRunning) {
                ctx.startService(Intent(ctx, ScreenCaptureService::class.java).apply { action = "STOP" })
                it.text = "Enable Vision Engine"
                it.setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            } else {
                val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }.also { visionToggle = it })

        layout.addView(section(ctx, "Local Engine"))
        
        val modelStatus = settingItem(ctx, "Check Local Model") { /* no-op */ }
        updateModelStatus(ctx, modelStatus)
        layout.addView(modelStatus)

        layout.addView(settingItem(ctx, "Browse HuggingFace Catalogue") {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ModelCatalogueFragment())
                .addToBackStack("model_catalogue")
                .commit()
        })

        layout.addView(section(ctx, "Developer Tools"))
        layout.addView(promptCopySection(ctx))

        layout.addView(section(ctx, "Remote API (Fallback)"))
        layout.addView(apiForm(ctx))

        scrollView.addView(layout)
        return scrollView
    }

    private fun promptCopySection(ctx: Context): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
        }

        card.addView(TextView(ctx).apply {
            text = "AGENT SYSTEM PROMPT"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
        })

        card.addView(TextView(ctx).apply {
            text = "Copy the system prompt to use with ChatGPT or Claude to generate your own YAML agents."
            textSize = 12f
            setPadding(0, 4, 0, 12)
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
        })

        card.addView(TextView(ctx).apply {
            text = "COPY TO CLIPBOARD"
            textSize = 14f
            setGravity(android.view.Gravity.CENTER)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(R.drawable.card_background) // Using drawable as a button bg
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                val prompt = com.mar.agent.sdk.core.executor.PromptBuilder.buildActionPrompt("{USER_INTENT}")
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("MAR System Prompt", prompt)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "Copied to Clipboard!", Toast.LENGTH_SHORT).show()
            }
        })

        return card
    }

    private fun section(ctx: Context, title: String): TextView {
        return TextView(ctx).apply {
            text = title.uppercase()
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 32, 0, 8)
        }
    }

    private fun settingItem(ctx: Context, label: String, onClick: (TextView) -> Unit): TextView {
        val tv = TextView(ctx).apply {
            text = label
            textSize = 16f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(16, 20, 16, 20)
            val bg = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
            setBackgroundResource(bg.resourceId)
            setOnClickListener { onClick(this) }
        }
        return tv
    }

    private fun apiForm(ctx: Context): View {
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(16, 16, 16, 16)
        }

        fun input(hint: String, prefKey: String, def: String = ""): EditText {
            return EditText(ctx).apply {
                this.hint = hint
                setText(prefs.getString(prefKey, def))
                setTextColor(resources.getColor(R.color.kite_text_primary, null))
                setHintTextColor(resources.getColor(R.color.kite_text_secondary, null))
                setPadding(0, 12, 0, 12)
            }
        }

        val keyInput = input("API Key", "api_key")
        val urlInput = input("Base URL", "api_base_url", "https://api.openai.com/v1")
        
        form.addView(keyInput)
        form.addView(urlInput)

        form.addView(TextView(ctx).apply {
            text = "SAVE CONFIG"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_blue, null))
            setPadding(0, 16, 0, 0)
            setOnClickListener {
                prefs.edit()
                    .putString("api_key", keyInput.text.toString().trim())
                    .putString("api_base_url", urlInput.text.toString().trim())
                    .apply()
                Toast.makeText(ctx, "API Saved", Toast.LENGTH_SHORT).show()
            }
        })
        return form
    }

    private fun updateModelStatus(ctx: Context, tv: TextView) {
        lifecycleScope.launch {
            val path = ModelDownloader.getLocalModelPath(ctx)
            if (path != null) {
                tv.text = "Model: READY"
                tv.setTextColor(resources.getColor(R.color.kite_green, null))
            } else {
                tv.text = "Model: MISSING (Tap to Download)"
                tv.setTextColor(resources.getColor(R.color.kite_orange, null))
                tv.setOnClickListener { downloadModel(tv) }
            }
        }
    }

    private fun downloadModel(tv: TextView) {
        tv.isEnabled = false
        lifecycleScope.launch {
            ModelDownloader.downloadModel(requireContext()).collect { state ->
                when (state) {
                    is ModelDownloader.DownloadState.Downloading -> {
                        tv.text = "Downloading: ${state.progressPct}%"
                    }
                    is ModelDownloader.DownloadState.Success -> {
                        tv.text = "Model: READY"
                        tv.setTextColor(resources.getColor(R.color.kite_green, null))
                        tv.isEnabled = true
                    }
                    is ModelDownloader.DownloadState.Error -> {
                        tv.text = "Error (Check Network) - Retry"
                        tv.setTextColor(resources.getColor(R.color.kite_red, null))
                        tv.isEnabled = true
                        tv.setOnClickListener { downloadModel(tv) }
                    }
                    else -> {}
                }
            }
        }
    }
}
