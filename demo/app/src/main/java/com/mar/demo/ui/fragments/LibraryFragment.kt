package com.mar.demo.ui.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mar.demo.R
import java.io.File
import org.yaml.snakeyaml.Yaml

class LibraryFragment : Fragment() {

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
            text = "Workflow Library"
            textSize = 28f
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val newWorkflowBtn = Button(context).apply {
            text = "+ Create New Workflow"
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            setTextColor(resources.getColor(R.color.kite_surface_white, null))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 48) }
            layoutParams = params
            setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, YamlEditorFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        layout.addView(titleView)
        layout.addView(newWorkflowBtn)

        val filesDir = context.filesDir
        val yamlFiles = filesDir.listFiles { _, name -> name.endsWith(".yaml") } ?: emptyArray()

        if (yamlFiles.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No workflows found. Create a new one above."
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
                var agentDesc = "No description provided."
                
                try {
                    val map = yamlParser.load<Map<String, Any>>(file.readText())
                    val agentMap = map["agent"] as? Map<*, *>
                    if (agentMap != null) {
                        agentMap["name"]?.toString()?.let { agentName = it }
                        agentMap["description"]?.toString()?.let { agentDesc = it }
                    }
                } catch (e: Exception) {
                    agentDesc = "Error parsing workflow details"
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
                    
                    setOnClickListener {
                        val fragment = YamlEditorFragment().apply {
                            arguments = Bundle().apply {
                                putString("DEFAULT_YAML", file.readText())
                                putString("FILE_NAME", file.name)
                            }
                        }
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
                
                val title = TextView(context).apply {
                    text = agentName
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.kite_text_primary, null))
                }
                val desc = TextView(context).apply {
                    text = agentDesc
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                    setPadding(0, 8, 0, 0)
                }
                card.addView(title)
                card.addView(desc)
                
                layout.addView(card)
            }
        }

        scrollView.addView(layout)

        return scrollView
    }
}
