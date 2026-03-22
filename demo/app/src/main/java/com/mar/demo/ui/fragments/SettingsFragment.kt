package com.mar.demo.ui.fragments

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mar.demo.ModelDownloader
import com.mar.demo.R
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val scrollView = ScrollView(context).apply {
            setBackgroundResource(R.color.kite_background)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(context).apply {
            text = "MAR Settings"
            textSize = 28f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val permTitle = TextView(context).apply {
            text = "Permissions"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(0, 0, 0, 16)
        }
        val p1 = TextView(context).apply { text = "• Display over other apps (For UI Agent)"; setTextColor(resources.getColor(R.color.kite_text_secondary, null)) }
        val p2 = TextView(context).apply { text = "• Background Execution (Granted)"; setTextColor(resources.getColor(R.color.kite_green, null)) }

        val engineTitle = TextView(context).apply {
            text = "Runtime Config"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setPadding(0, 48, 0, 16)
        }
        
        val e1 = TextView(context).apply { 
            text = "• Execution Model: Sequential (Queued)"
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 16, 0, 16)
            isClickable = true
            val backgroundAttr = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, backgroundAttr, true)
            setBackgroundResource(backgroundAttr.resourceId)
            
            setOnClickListener {
                Toast.makeText(context, "Hardware Constraint: To prevent memory crashes (SIGSEGV), all agents naturally route through a single runtime Mutex.", Toast.LENGTH_LONG).show()
            }
        }
        
        val e2 = TextView(context).apply { 
            val modelPath = ModelDownloader.getLocalModelPath(context)
            val modelName = modelPath?.substringAfterLast("/") ?: "qwen2.5-0.5b"
            text = if (modelPath != null) "• Model: Ready 🟢 ($modelName)" else "• Model: Missing 🔴 (Tap to Download)"
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 16, 0, 16)
            isClickable = true
            val backgroundAttr = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, backgroundAttr, true)
            setBackgroundResource(backgroundAttr.resourceId)
            
            setOnClickListener {
                if (ModelDownloader.getLocalModelPath(context) != null) {
                    Toast.makeText(context, "Model is already downloaded and ready!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                isClickable = false
                lifecycleScope.launch {
                    ModelDownloader.downloadModel(context).collect { state ->
                        when(state) {
                            is ModelDownloader.DownloadState.Downloading -> {
                                text = "• Downloading: ${state.progressPct}% (${String.format("%.1f", state.downloadedMb)} / ${String.format("%.1f", state.totalMb)} MB)"
                            }
                            is ModelDownloader.DownloadState.Success -> {
                                text = "• Model: Ready 🟢 (qwen2.5-0.5b)"
                                isClickable = true
                                Toast.makeText(context, "Download Complete!", Toast.LENGTH_SHORT).show()
                            }
                            is ModelDownloader.DownloadState.Error -> {
                                text = "• Download Failed 🔴 (Tap to Retry)"
                                isClickable = true
                                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
        
        layout.addView(titleView)
        layout.addView(permTitle)
        layout.addView(p1)
        layout.addView(p2)
        layout.addView(engineTitle)
        layout.addView(e1)
        layout.addView(e2)

        scrollView.addView(layout)

        return scrollView
    }
}
