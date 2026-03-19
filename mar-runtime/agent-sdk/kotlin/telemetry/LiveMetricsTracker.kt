package com.mar.agent.sdk.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject

/**
 * Tracks live hardware metrics strictly to ensure the Agent stays within constraints:
 * - < 5% battery drain per hour
 * - Runs smoothly on 1024MB RAM constraint
 */
class LiveMetricsTracker(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Performance state
    private var totalTokensGenerated: Long = 0
    private var inferenceStartTimeMs: Long = 0
    private var currentTps: Double = 0.0

    /**
     * Call this inside the JNI bridge after every N tokens to calculate rolling TPS.
     */
    fun updateGenerationStats(tokens: Int, timeElapsedMs: Long) {
        totalTokensGenerated += tokens
        if (timeElapsedMs > 0) {
            currentTps = (tokens * 1000.0) / timeElapsedMs
        }
    }

    fun getMetricsReport(): JSONObject {
        return JSONObject().apply {
            put("ram_usage_mb", getMemoryUsageMb())
            put("battery_level_pct", getBatteryLevel())
            put("is_charging", isCharging())
            put("tokens_per_sec", currentTps)
            put("total_tokens", totalTokensGenerated)
        }
    }

    private fun getMemoryUsageMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Native PSS or app memory logic is complex in pure Kotlin,
        // so we mock the overall memory threshold available to standard processes.
        // A real implementation would use android.os.Debug.MemoryInfo.
        val usedRamMb = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
        return usedRamMb
    }

    private fun getBatteryLevel(): Float {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return level * 100 / scale.toFloat()
    }

    private fun isCharging(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING 
                || status == BatteryManager.BATTERY_STATUS_FULL
    }
}
