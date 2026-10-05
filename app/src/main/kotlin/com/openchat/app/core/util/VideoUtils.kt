package com.openchat.app.core.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoUtils {
    
    suspend fun getVideoDuration(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
    
    suspend fun getVideoDimensions(context: Context, uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            Pair(width, height)
        } catch (e: Exception) {
            Pair(0, 0)
        } finally {
            retriever.release()
        }
    }
    
    suspend fun getVideoThumbnail(context: Context, uri: Uri, timeUs: Long = 0): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
    
    suspend fun compressVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetWidth: Int = 1280,
        targetHeight: Int = 720,
        videoBitrate: Int = 2_000_000,
        audioBitrate: Int = 128_000
    ): Result<File> = withContext(Dispatchers.IO) {
        // This was a no-op that created an empty file and returned Result.success,
        // so every caller happily uploaded a 0-byte "compressed video". Actual
        // transcoding needs a MediaCodec pipeline; until that exists, fail loudly
        // rather than silently producing a broken attachment.
        Result.failure(
            UnsupportedOperationException(
                "Video compression is not implemented; refusing to return an empty file"
            )
        )
    }
}
