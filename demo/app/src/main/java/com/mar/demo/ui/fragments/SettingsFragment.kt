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
            setBackgroundResource(R.color.kite_background)
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        layout.addView(sectionTitle(ctx, "Settings"))

        layout.addView(section(ctx, "Permissions"))
        layout.addView(settingItem(ctx, "Notification Access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        layout.addView(settingItem(ctx, "Accessibility Service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(settingItem(ctx, "Vision / Screen Capture") {
            if (ScreenCaptureService.isRunning) {
                ctx.startService(Intent(ctx, ScreenCaptureService::class.java).apply { action = "STOP" })
                it.text = "Enable Vision"
                it.setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            } else {
                val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }.also { visionToggle = it })

        layout.addView(section(ctx, "Runtime"))
        layout.addView(settingItem(ctx, "Sequential (Queued) Execution") {
            Toast.makeText(ctx, "All agents route through single runtime mutex to prevent SIGSEGV", Toast.LENGTH_LONG).show()
        })

        val modelStatus = settingItem(ctx, "Default Model") { /* no-op, status only */ }
        updateModelStatus(ctx, modelStatus)
        layout.addView(modelStatus)

        layout.addView(settingItem(ctx, "Manage Models") {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ModelCatalogueFragment())
                .addToBackStack("model_catalogue")
                .commit()
        })

        layout.addView(section(ctx, "AI Workflow Chat"))
        layout.addView(apiForm(ctx))

        scrollView.addView(layout)
        return scrollView
    }

    private fun section(ctx: Context, title: String): TextView {
        return TextView(ctx).apply {
            text = title.uppercase()
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 20, 0, 8)
        }
    }

    private fun settingItem(ctx: Context, label: String, onClick: (TextView) -> Unit): TextView {
        val tv = TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(0, 14, 0, 14)
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
            setPadding(12, 12, 12, 12)
        }

        fun input(hint: String, prefKey: String, def: String = ""): EditText {
            return EditText(ctx).apply {
                this.hint = hint
                setText(prefs.getString(prefKey, def))
                setTextColor(resources.getColor(R.color.kite_text_primary, null))
                setHintTextColor(resources.getColor(R.color.kite_text_secondary, null))
                setBackgroundResource(android.R.color.transparent)
                setPadding(0, 8, 0, 8)
            }
        }

        val keyInput = input("API Key", "api_key")
        val urlInput = input("API Base URL", "api_base_url",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
        val modelInput = input("Model name", "api_model", "gemini-2.0-flash")

        form.addView(keyInput)
        form.addView(urlInput)
        form.addView(modelInput)

        form.addView(TextView(ctx).apply {
            text = "Save"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_blue, null))
            setPadding(0, 8, 0, 0)
            setOnClickListener {
                prefs.edit()
                    .putString("api_key", keyInput.text.toString().trim())
                    .putString("api_base_url", urlInput.text.toString().trim())
                    .putString("api_model", modelInput.text.toString().trim())
                    .apply()
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }
        })

        form.addView(TextView(ctx).apply {
            text = "Supports OpenAI-compatible & Gemini APIs. Format auto-detected."
            textSize = 11f
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 4, 0, 0)
        })

        return form
    }

    private fun updateModelStatus(ctx: Context, tv: TextView) {
        lifecycleScope.launch {
            val path = ModelDownloader.getLocalModelPath(ctx)
            if (path != null) {
                tv.text = "Model: Ready ($path)"
                tv.setTextColor(resources.getColor(R.color.kite_green, null))
            } else {
                tv.text = "Model: Download Required"
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
                        tv.text = "Model: Ready"
                        tv.setTextColor(resources.getColor(R.color.kite_green, null))
                        tv.isEnabled = true
                    }
                    is ModelDownloader.DownloadState.Error -> {
                        tv.text = "Download Failed — Tap to Retry"
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
