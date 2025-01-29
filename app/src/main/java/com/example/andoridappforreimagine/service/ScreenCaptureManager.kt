package com.example.andoridappforreimagine.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "ScreenCapture"
        const val REQUEST_MEDIA_PROJECTION = 1000
    }

    fun initialize(resultCode: Int, data: Intent, width: Int, height: Int, density: Int) {
        screenWidth = width
        screenHeight = height
        screenDensity = density

        // Start foreground service for media projection
        val serviceIntent = Intent(context, ScreenCaptureService::class.java)
        context.startForegroundService(serviceIntent)

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        setupVirtualDisplay()
    }

    fun cleanup() {
        release()
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            Handler(Looper.getMainLooper())
        )
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            try {
                if (mediaProjection == null || imageReader == null) {
                    throw IllegalStateException("Screen capture not initialized. Call initialize() first.")
                }

                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * screenWidth

                        // Create bitmap with correct dimensions
                        val bitmap = Bitmap.createBitmap(
                            screenWidth,  // Remove padding to get exact screen size
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        // Copy only the screen area, excluding padding
                        buffer.position(0)
                        for (row in 0 until screenHeight) {
                            buffer.position(row * rowStride)
                            bitmap.copyPixelsFromBuffer(buffer)
                            // Skip the padding
                            buffer.position(buffer.position() + rowPadding)
                        }

                        return@withContext bitmap
                    } finally {
                        image.close()
                    }
                } else {
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(500) // Wait before retry
                        continue
                    }
                }
            } catch (e: Exception) {
                retryCount++
                if (retryCount < maxRetries) {
                    delay(500) // Wait before retry
                    continue
                }
                throw e
            }
        }
        return@withContext null
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    fun getMediaProjectionIntent(): Intent {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }
}
