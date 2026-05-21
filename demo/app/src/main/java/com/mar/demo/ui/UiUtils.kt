package com.mar.demo.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import com.mar.demo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun cardFrame(ctx: Context): LinearLayout {
    val card = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.card_background)
        setPadding(16, 16, 16, 16)
        val dp8 = ctx.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp8) }
        elevation = 4f
    }
    return card
}

fun badge(ctx: Context, text: String, @ColorRes colorId: Int): TextView {
    return TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ctx.resources.getColor(colorId, null))
    }
}

fun detailRow(ctx: Context, label: String, value: String, @ColorRes colorId: Int, bottomPx: Int = 4): TextView {
    return TextView(ctx).apply {
        text = "$label: $value"
        textSize = 13f
        setTextColor(ctx.resources.getColor(colorId, null))
        setPadding(0, bottomPx, 0, 0)
    }
}

fun row(ctx: Context, title: String, addTrailing: (LinearLayout) -> Unit): LinearLayout {
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    row.addView(TextView(ctx).apply {
        text = title
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ctx.resources.getColor(R.color.kite_text_primary, null))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })

    addTrailing(row)
    return row
}

fun sectionTitle(ctx: Context, text: String, topPaddingDp: Int = 0): TextView {
    return TextView(ctx).apply {
        this.text = text
        textSize = 20f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ctx.resources.getColor(R.color.kite_text_primary, null))
        val dp = ctx.resources.displayMetrics.density
        setPadding(0, (topPaddingDp * dp).toInt(), 0, (12 * dp).toInt())
    }
}

fun captionRow(ctx: Context, text: String): TextView {
    return TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTextColor(ctx.resources.getColor(R.color.kite_text_secondary, null))
        setPadding(0, 4, 0, 0)
    }
}

fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}

fun extractModelFromYaml(yaml: String): String? {
    try {
        val lines = yaml.lines()
        var inHardware = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "hardware_requirements:") { inHardware = true; continue }
            if (inHardware) {
                if (trimmed.startsWith("-")) return null
                if (trimmed.startsWith("model:")) {
                    return trimmed.split(":")[1].trim().replace("\"", "")
                }
                if (!trimmed.startsWith(" ") && !trimmed.startsWith("min_") && trimmed.contains(":")) {
                    inHardware = false
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("MAR_UiUtils", "Failed to extract model from YAML: ${e.message}")
    }
    return null
}

fun setModelInYaml(yaml: String, newModelId: String): String {
    val lines = yaml.lines()
    val result = mutableListOf<String>()
    var inHardware = false
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "hardware_requirements:") { inHardware = true }
        if (inHardware && trimmed.startsWith("model:")) {
            val indent = line.takeWhile { it == ' ' }
            result.add("${indent}model: \"$newModelId\"")
            inHardware = false
        } else {
            result.add(line)
        }
    }
    return result.joinToString("\n")
}
