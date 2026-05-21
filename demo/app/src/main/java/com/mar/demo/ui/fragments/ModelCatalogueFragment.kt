package com.mar.demo.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mar.agent.sdk.models.DownloadProgress
import com.mar.agent.sdk.models.HfFileInfo
import com.mar.agent.sdk.models.HfSearchResult
import com.mar.agent.sdk.models.HuggingFaceApi
import com.mar.agent.sdk.models.ModelRepository
import com.mar.demo.R
import com.mar.demo.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelCatalogueFragment : Fragment() {

    private var searchJob: Job? = null
    private val downloadedIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scrollView = ScrollView(ctx).apply {
            setBackgroundResource(R.color.kite_background)
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        layout.addView(sectionTitle(ctx, "Model Catalogue"))

        layout.addView(TextView(ctx).apply {
            text = "Search HuggingFace for GGUF models"
            textSize = 14f
            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setPadding(0, 0, 0, 16)
        })

        val searchInput = EditText(ctx).apply {
            hint = "Search models (e.g. qwen, phi, llama)..."
            setTextColor(resources.getColor(R.color.kite_text_primary, null))
            setHintTextColor(resources.getColor(R.color.kite_text_secondary, null))
            setBackgroundResource(R.drawable.card_background)
            setPadding(16, 12, 16, 12)
        }
        layout.addView(searchInput)

        val resultsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 0) }
            layoutParams = params
        }
        layout.addView(resultsContainer)

        val repo = ModelRepository(ctx)

        lifecycleScope.launch {
            repo.getAllFlow().collect { models ->
                downloadedIds.clear()
                models.forEach { downloadedIds.add(it.id) }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString()?.trim() ?: return
                if (query.length < 2) return

                searchJob = lifecycleScope.launch {
                    delay(500)
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(TextView(ctx).apply {
                        text = "Searching..."
                        setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                    })

                    val results = HuggingFaceApi.searchModels(query)
                    resultsContainer.removeAllViews()

                    if (results.isEmpty()) {
                        resultsContainer.addView(TextView(ctx).apply {
                            text = "No GGUF models found for \"$query\""
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, 32, 0, 0)
                            setTextColor(resources.getColor(R.color.kite_text_secondary, null))
                        })
                        return@launch
                    }

                    results.forEach { result ->
                        resultsContainer.addView(resultCard(ctx, repo, result))
                    }
                }
            }
        })

        scrollView.addView(layout)
        return scrollView
    }

    private fun resultCard(
        ctx: android.content.Context, repo: ModelRepository, result: HfSearchResult
    ): View {
        val card = cardFrame(ctx)

        card.addView(row(ctx, result.id) { r ->
            r.addView(TextView(ctx).apply {
                text = "${result.downloads} downloads"
                textSize = 12f
                setTextColor(resources.getColor(R.color.kite_text_secondary, null))
            })
        })

        result.ggufFiles.forEach { file ->
            card.addView(fileRow(ctx, repo, result.id, file))
        }

        return card
    }

    private fun fileRow(
        ctx: android.content.Context, repo: ModelRepository,
        hfModelId: String, file: HfFileInfo
    ): View {
        val fileRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val sizeMb = file.size / (1024 * 1024)
        val infoText = TextView(ctx).apply {
            text = if (sizeMb > 0) "${file.quantization} · ${sizeMb}MB" else file.quantization
            textSize = 14f
            setTextColor(resources.getColor(R.color.kite_blue, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(infoText)

        if (file.size > 0) {
            val rating = HuggingFaceApi.rateHardware(ctx, file.size)
            topRow.addView(TextView(ctx).apply {
                text = rating.label
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(rating.color)
            })
            fileRow.addView(topRow)
            fileRow.addView(captionRow(ctx, rating.detail))
        } else {
            fileRow.addView(topRow)
        }

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8
            ).apply { setMargins(0, 8, 0, 0) }
            max = 100
        }
        fileRow.addView(progressBar)

        val statusText = TextView(ctx).apply {
            text = ""
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }
        fileRow.addView(statusText)

        val modelId = file.name.replace(".gguf", "")
        val actionBtn = Button(ctx).apply {
            if (downloadedIds.contains(modelId)) {
                text = "Delete"
                setBackgroundColor(android.graphics.Color.rgb(154, 160, 166))
            } else {
                text = "Download"
                setBackgroundColor(resources.getColor(R.color.kite_blue, null))
            }
            setTextColor(android.graphics.Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 0) }
            layoutParams = params
        }
        fileRow.addView(actionBtn)

        actionBtn.setOnClickListener {
            if (downloadedIds.contains(modelId)) {
                lifecycleScope.launch {
                    repo.delete(modelId)
                    downloadedIds.remove(modelId)
                    actionBtn.text = "Download"
                    actionBtn.setBackgroundColor(resources.getColor(R.color.kite_blue, null))
                    Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show()
                }
            } else {
                actionBtn.isEnabled = false
                actionBtn.text = "Downloading..."
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    repo.downloadModel(hfModelId, file).collect { state ->
                        when (state) {
                            is DownloadProgress.Progress -> {
                                progressBar.progress = state.pct
                                statusText.text = "${state.pct}% (${"%.1f".format(state.downloadedMb)} / ${"%.1f".format(state.totalMb)} MB)"
                                statusText.setTextColor(resources.getColor(R.color.kite_blue, null))
                            }
                            is DownloadProgress.Done -> {
                                progressBar.visibility = View.GONE
                                downloadedIds.add(modelId)
                                actionBtn.text = "Delete"
                                actionBtn.setBackgroundColor(android.graphics.Color.rgb(154, 160, 166))
                                statusText.text = "Ready"
                                statusText.setTextColor(resources.getColor(R.color.kite_green, null))
                                actionBtn.isEnabled = true
                            }
                            is DownloadProgress.Error -> {
                                progressBar.visibility = View.GONE
                                actionBtn.text = "Download"
                                actionBtn.setBackgroundColor(resources.getColor(R.color.kite_blue, null))
                                statusText.text = state.message
                                statusText.setTextColor(resources.getColor(R.color.kite_red, null))
                                actionBtn.isEnabled = true
                            }
                        }
                    }
                }
            }
        }

        return fileRow
    }
}
