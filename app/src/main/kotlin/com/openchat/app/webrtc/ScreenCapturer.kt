package com.openchat.app.webrtc

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.*
import org.webrtc.*

class ScreenCapturer(
    private val context: Context,
    private val videoSource: VideoSource,
    width: Int = 720,
    height: Int = 1280
) : ImageReader.OnImageAvailableListener {

    companion object {
        private const val TAG = "ScreenCapturer"
    }

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRunning = false
    private var captureJob: Job? = null
    private val videoWidth = width
    private val videoHeight = height

    fun start(mediaProjection: MediaProjection) {
        if (isRunning) return
        isRunning = true
        imageReader = ImageReader.newInstance(videoWidth, videoHeight, ImageFormat.YUV_420_888, 2)
        imageReader?.setOnImageAvailableListener(this, null)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "OpenChatScreenShare",
            videoWidth, videoHeight, 320,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        Log.d(TAG, "Screen capturer started: ${videoWidth}x${videoHeight}")
    }

    fun stop() {
        isRunning = false
        try { virtualDisplay?.release() } catch (e: Exception) { Log.w(TAG, "VirtualDisplay release failed", e) }
        virtualDisplay = null
        try { imageReader?.close() } catch (e: Exception) { Log.w(TAG, "ImageReader close failed", e) }
        imageReader = null
        captureJob?.cancel()
        captureJob = null
        Log.d(TAG, "Screen capturer stopped")
    }

    override fun onImageAvailable(reader: ImageReader) {
        if (!isRunning) return
        val image = reader.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            if (planes.size < 3) return

            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining() / 2
            val vSize = vBuffer.remaining() / 2

            val frameWidth = image.width
            val frameHeight = image.height

            // Allocate I420 buffer for WebRTC
            val buffer = JavaI420Buffer.allocate(frameWidth, frameHeight)
            val dataY = buffer.dataY
            val yStride = buffer.strideY
            val dataU = buffer.dataU
            val uStride = buffer.strideU
            val dataV = buffer.dataV
            val vStride = buffer.strideV

            val rowBytes = minOf(yStride, frameWidth)
            val yRowStride = planes[0].rowStride
            val yPixelStride = planes[0].pixelStride
            dataY.rewind()
            if (yPixelStride == 1 && yRowStride == frameWidth) {
                dataY.put(yBuffer)
            } else {
                for (row in 0 until frameHeight) {
                    yBuffer.position(row * yRowStride)
                    val savedLimit = yBuffer.limit()
                    yBuffer.limit(yBuffer.position() + frameWidth)
                    dataY.put(yBuffer)
                    yBuffer.limit(savedLimit)
                }
            }

            val uRowStride = planes[1].rowStride
            val uPixelStride = planes[1].pixelStride
            val uvHeight = frameHeight / 2
            val uvWidth = frameWidth / 2
            dataU.rewind()
            if (uPixelStride == 1 && uRowStride == uvWidth) {
                dataU.put(uBuffer)
            } else {
                for (row in 0 until uvHeight) {
                    uBuffer.position(row * uRowStride)
                    val savedLimit = uBuffer.limit()
                    uBuffer.limit(uBuffer.position() + uvWidth)
                    dataU.put(uBuffer)
                    uBuffer.limit(savedLimit)
                }
            }

            val vRowStride = planes[2].rowStride
            val vPixelStride = planes[2].pixelStride
            dataV.rewind()
            if (vPixelStride == 1 && vRowStride == uvWidth) {
                dataV.put(vBuffer)
            } else {
                for (row in 0 until uvHeight) {
                    vBuffer.position(row * vRowStride)
                    val savedLimit = vBuffer.limit()
                    vBuffer.limit(vBuffer.position() + uvWidth)
                    dataV.put(vBuffer)
                    vBuffer.limit(savedLimit)
                }
            }

            val frame = VideoFrame(buffer, 0, System.nanoTime())
            videoSource.capturerObserver.onFrameCaptured(frame)
            frame.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing screen frame", e)
        } finally {
            image.close()
        }
    }
}
