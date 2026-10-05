package com.openchat.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080,
        quality: Int = 85
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return@withContext Result.failure(Exception("Cannot open input stream or decode bitmap"))
            
            val rotatedBitmap = correctImageOrientation(context, uri, originalBitmap)
            val scaledBitmap = scaleBitmap(rotatedBitmap, maxWidth, maxHeight)

            // Compress BEFORE recycling anything. correctImageOrientation returns the
            // same instance when there is no EXIF rotation, and scaleBitmap returns the
            // same instance when the image is already within bounds - so for most photos
            // scaledBitmap === originalBitmap. Recycling the original here compressed an
            // already-dead Bitmap, silently producing a 0-byte file that was then
            // reported as Result.success and uploaded as an empty image.
            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            val compressed = FileOutputStream(outputFile).use { output ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            if (!compressed) {
                outputFile.delete()
                return@withContext Result.failure(Exception("Bitmap compression failed"))
            }

            // Only recycle bitmaps we actually created, and only after the write.
            if (scaledBitmap !== rotatedBitmap) scaledBitmap.recycle()
            if (rotatedBitmap !== originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun compressToMaxSize(
        context: Context,
        uri: Uri,
        maxSizeBytes: Long = 10 * 1024 * 1024
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var quality = 95
            var width = 2048
            var height = 2048
            
            while (quality >= 60) {
                val result = compressImage(context, uri, width, height, quality)
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    if (file.length() <= maxSizeBytes) {
                        return@withContext Result.success(file)
                    }
                    file.delete()
                }
                quality -= 10
                width = (width * 0.8).toInt()
                height = (height * 0.8).toInt()
            }
            
            Result.failure(Exception("Cannot compress image to target size"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    private fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = getImageRotation(context, uri)
        return if (rotation != 0) {
            rotateBitmap(bitmap, rotation)
        } else {
            bitmap
        }
    }
    
    private fun getImageRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    fun createThumbnail(bitmap: Bitmap, maxSize: Int = 200): Bitmap {
        // Without the 1f cap, an image smaller than maxSize produced a scale > 1 and
        // was upscaled - wasting memory and blurring an already-small thumbnail.
        val scale = minOf(1f, maxSize.toFloat() / maxOf(bitmap.width, bitmap.height))
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    fun bitmapToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 85): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }
}
