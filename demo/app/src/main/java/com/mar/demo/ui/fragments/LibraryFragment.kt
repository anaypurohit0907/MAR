package com.mar.demo.ui.fragments

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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.models.ModelRepository
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.demo.R
import com.mar.demo.ui.*
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val swipeRefresh = SwipeRefreshLayout(ctx).apply {
            setBackgroundResource(R.color.kite_background)
        }

        val scrollView = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        layout.addView(sectionTitle(ctx, "Workflow Library"))

        layout.addView(TextView(ctx).apply {
            text = "+ Create New"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.kite_surface, null))
            setBackgroundResource(R.drawable.card_background)
            setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 12, 0, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = params
            setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, YamlEditorFragment())
                    .addToBackStack(null)
                    .commit()
            }
        })

        val repo = WorkflowRepository(ctx)
        val modelRepo = ModelRepository(ctx)

        lifecycleScope.launch {
            repo.importFromFilesDir()
            repo.getAllFlow().collect { workflows ->
                layout.removeViews(2, maxOf(0, layout.childCount - 2))

                if (workflows.isEmpty()) {
                    layout.addView(TextView(ctx).apply {
                        text = "No workflows found"
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 48, 0, 0)
                        setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                    })
                } else {
                    workflows.forEach { wf ->
                        val modelId = extractModelFromYaml(wf.yaml)
                        val modelPath = modelId?.let { modelRepo.findLocalModelPath(it) }
                        val card = cardFrame(ctx)

                        card.setOnClickListener {
                            val frag = YamlEditorFragment().apply {
                                arguments = Bundle().apply {
                                    putString("DEFAULT_YAML", wf.yaml)
                                    putString("FILE_NAME", "${wf.id}.yaml")
                                }
                            }
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, frag)
                                .addToBackStack(null)
                                .commit()
                        }

                        card.addView(row(ctx, wf.name) { r ->
                            r.addView(TextView(ctx).apply {
                                text = if (wf.enabled) "ON" else "OFF"
                                textSize = 12f
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(resources.getColor(
                                    if (wf.enabled) R.color.kite_green else R.color.kite_text_secondary, null
                                ))
                                setPadding(8, 0, 0, 0)
                                setOnClickListener {
                                    lifecycleScope.launch { repo.setEnabled(wf.id, !wf.enabled) }
                                }
                            })
                        })

                        card.addView(captionRow(ctx, wf.description))

                        modelId?.let {
                            val colorId = if (modelPath != null) R.color.kite_blue else R.color.kite_red
                            val text = if (modelPath != null) it else "$it (not found)"
                            card.addView(detailRow(ctx, "Model", text, colorId))
                        }

                        val meta = mutableListOf<String>()
                        if (wf.lastRunAt > 0) meta.add("Last run: ${relativeTime(wf.lastRunAt)}")
                        if (wf.errorCount > 0) meta.add("${wf.errorCount} error${if (wf.errorCount > 1) "s" else ""}")
                        if (meta.isNotEmpty()) {
                            card.addView(captionRow(ctx, meta.joinToString(" | ")))
                        }

                        val actions = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 12, 0, 0)
                        }

                        actions.addView(TextView(ctx).apply {
                            text = "Delete"
                            setTextColor(resources.getColor(R.color.kite_red, null))
                            setPadding(0, 0, 24, 0)
                            setOnClickListener {
                                lifecycleScope.launch { repo.delete(wf.id) }
                            }
                        })

                        actions.addView(TextView(ctx).apply {
                            text = "Run"
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(resources.getColor(R.color.kite_blue, null))
                            setOnClickListener {
                                MarWorkScheduler(ctx).executeAgentNow(wf.id, yamlWorkflow = wf.yaml)
                                Toast.makeText(ctx, "Running ${wf.name}...", Toast.LENGTH_SHORT).show()
                            }
                        })

                        card.addView(actions)
                        layout.addView(card)
                    }
                }
                swipeRefresh.isRefreshing = false
            }
        }

        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch { repo.importFromFilesDir() }
        }

        scrollView.addView(layout)
        swipeRefresh.addView(scrollView)
        return swipeRefresh
    }
}
