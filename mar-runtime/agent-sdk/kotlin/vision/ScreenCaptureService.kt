package com.mar.runtime.agent.vision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.mar.demo.FileLogger

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "ScreenCaptureChannel"
        private const val NOTIFICATION_ID = 2001
        
        @Volatile
        var latestBitmap: Bitmap? = null
            private set

        var isRunning = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate() {
        super.onCreate()
        FileLogger.log("ScreenCaptureService onCreate called")
        try {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            createNotificationChannel()
        } catch (e: Exception) {
            FileLogger.log("Error in onCreate: ${e.message}")
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FileLogger.log("ScreenCaptureService onStartCommand called. action=${intent?.action}")
        try {
            if (intent?.action == "STOP") {
                stopSelf()
                return START_NOT_STICKY
            }

            val resultCode = intent?.getIntExtra("RESULT_CODE", android.app.Activity.RESULT_CANCELED) ?: android.app.Activity.RESULT_CANCELED
            val resultData: Intent? = intent?.getParcelableExtra("RESULT_DATA")

            FileLogger.log("onStartCommand resultCode: $resultCode, resultData present: ${resultData != null}")

            if (resultCode == android.app.Activity.RESULT_OK && resultData != null) {
                // ** CRITICAL FIX FOR ANDROID 14 CRASH **
                // MediaProjections aggressively demand the explicit service type flag.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                        FileLogger.log("startForeground called with MediaProjection type")
                    } catch (e: Exception) {
                        FileLogger.log("Failed to startForeground with type: ${e.message}\n${e.stackTraceToString()}")
                        throw e
                    }
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    FileLogger.log("startForeground called without spec type")
                }

                FileLogger.log("Calling startProjection()")
                startProjection(resultCode, resultData)
                isRunning = true
                Log.i(TAG, "Screen capture service started actively.")
            } else {
                Log.e(TAG, "Failed to start capture service due to missing intent data.")
                FileLogger.log("Failed to start capture service due to missing intent data.")
                stopSelf()
            }
        } catch (e: Exception) {
            FileLogger.log("Fatal error orchestrating screen capture: ${e.message}")
            FileLogger.log(e.stackTraceToString())
            Log.e(TAG, "Fatal error orchestrating screen capture: ${e.message}")
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Vision Engine Error: Failed to bind Virtual Display. Please try again.", Toast.LENGTH_LONG).show()
            }
            stopSelf()
        }

        return START_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        try {
            FileLogger.log("startProjection called with resultCode: $resultCode")
            mediaProjection = projectionManager?.getMediaProjection(resultCode, resultData)
            
            if (mediaProjection == null) {
                FileLogger.log("mediaProjection is null!")
                throw IllegalStateException("MediaProjection token rejected by Android.")
            }
            FileLogger.log("getMediaProjection successful")
            
            // Ensure graceful teardown if user manually clicks "Stop sharing" from system tray
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection killed by system UI.")
                    FileLogger.log("MediaProjection callback onStop triggered")
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            val displayMetrics = resources.displayMetrics
            var width = displayMetrics.widthPixels
            var height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            if (width <= 0) width = 1080
            if (height <= 0) height = 1920

            FileLogger.log("Display metrics: ${width}x${height} density=$density")

            // Setup ImageReader
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        
                        val bitmapWidth = width + rowPadding / pixelStride
                        
                        if (bitmapWidth > 0 && height > 0) {
                            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(buffer)
                            
                            // Crop internal padding buffer injected by hardware encoders
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            latestBitmap = cropped
                        }
                        image.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring or parsing image frame: ${e.message}")
                }
            }, null)

            FileLogger.log("Creating virtual display")
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MAR_ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            FileLogger.log("Virtual display created")
        } catch (e: Exception) {
            FileLogger.log("Exception inside startProjection: ${e.message}")
            FileLogger.log(e.stackTraceToString())
            throw e
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("MAR Screen Vision")
            .setContentText("Agent is observing the screen layout")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleanly destroying projection: ${e.message}")
        } finally {
            isRunning = false
            Log.i(TAG, "Screen capture service destroyed.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
