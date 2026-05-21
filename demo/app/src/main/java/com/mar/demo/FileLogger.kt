package com.mar.demo

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logFile: File? = null

    fun init(context: Context) {
        val dir = context.filesDir ?: return
        logFile = File(dir, "mar_crash_log.txt")
        if (!logFile!!.exists()) logFile!!.createNewFile()
        
        // Setup global uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("FATAL CRASH on thread ${thread.name}: ${throwable.message}")
            log(throwable.stackTraceToString())
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun log(message: String) {
        Log.d("FileLogger", message)
        try {
            logFile?.let { file ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                FileOutputStream(file, true).use {
                    it.write("[$timestamp] $message\n".toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
