package com.mar.demo.ui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.models.ModelRepository
import com.mar.demo.R
import com.mar.demo.ui.*
import kotlinx.coroutines.launch

class ActiveAgentsFragment : Fragment() {

    private val statusViews = mutableMapOf<String, TextView>()
    private lateinit var cardsContainer: LinearLayout

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.mar.agent.PROGRESS" -> {
                    val agentId = intent.getStringExtra("agentId") ?: return
                    val status = intent.getStringExtra("status") ?: return
                    statusViews[agentId]?.let {
                        it.text = status
                        it.setTextColor(resources.getColor(R.color.kite_blue, null))
                    }
                }
                "com.mar.agent.COMPLETE" -> refreshList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        statusViews.clear()

        val swipeRefresh = SwipeRefreshLayout(inflater.context).apply {
            setBackgroundResource(R.color.kite_background)
            setOnRefreshListener { refreshList() }
        }

        val scrollView = ScrollView(inflater.context)
        cardsContainer = LinearLayout(inflater.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        scrollView.addView(cardsContainer)
        swipeRefresh.addView(scrollView)

        refreshList()
        return swipeRefresh
    }

    private fun refreshList() {
        val ctx = requireContext()
        (view as? SwipeRefreshLayout)?.isRefreshing = true
        cardsContainer.removeAllViews()
        statusViews.clear()

        lifecycleScope.launch {
            val repo = WorkflowRepository(ctx)
            repo.importFromFilesDir()
            val modelRepo = ModelRepository(ctx)
            val workflows = repo.getEnabled()
            (view as? SwipeRefreshLayout)?.isRefreshing = false

            if (workflows.isEmpty()) {
                cardsContainer.addView(emptyState(ctx, "No enabled agents. Enable one in Library."))
                return@launch
            }

            workflows.forEach { wf ->
                val modelId = extractModelFromYaml(wf.yaml)
                val modelPath = modelId?.let { modelRepo.findLocalModelPath(it) }
                val card = cardFrame(ctx)

                card.addView(row(ctx, wf.name) { r ->
                    r.addView(badge(ctx, "Active", R.color.kite_green))
                })

                modelId?.let {
                    val colorId = if (modelPath != null) R.color.kite_blue else R.color.kite_red
                    val text = if (modelPath != null) it else "$it (not found)"
                    card.addView(detailRow(ctx, "Model", text, colorId))
                }

                if (wf.lastRunAt > 0) {
                    card.addView(detailRow(ctx, "Last run", relativeTime(wf.lastRunAt), R.color.kite_text_secondary))
                }

                if (wf.errorCount > 0) {
                    card.addView(detailRow(ctx, "Errors", "$wf.errorCount", R.color.kite_red))
                }

                val statusLabel = TextView(ctx).apply {
                    text = "Idle — tap to run"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                    setPadding(0, 8, 0, 0)
                }
                statusViews[wf.name] = statusLabel
                card.addView(statusLabel)

                card.setOnClickListener {
                    Toast.makeText(ctx, "Running ${wf.name}...", Toast.LENGTH_SHORT).show()
                    statusViews[wf.name]?.let {
                        it.text = "Running..."
                        it.setTextColor(resources.getColor(R.color.kite_blue, null))
                    }
                    MarWorkScheduler(ctx).executeAgentNow(wf.id, yamlWorkflow = wf.yaml)
                }

                cardsContainer.addView(card)
            }
        }
    }

    private fun emptyState(ctx: Context, msg: String): View {
        return TextView(ctx).apply {
            text = msg
            gravity = android.view.Gravity.CENTER
            setPadding(0, 64, 0, 0)
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.mar.agent.PROGRESS")
            addAction("com.mar.agent.COMPLETE")
        }
        ContextCompat.registerReceiver(requireContext(), progressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(progressReceiver)
    }
}
