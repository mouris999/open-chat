package com.openchat.app.data.repository

import android.content.Context
import android.net.Uri
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryRepository @Inject constructor(
    private val cloudinary: Cloudinary,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Upload an image to Cloudinary
     * @param uri The image URI
     * @param chatId The chat ID for folder organization
     * @return The uploaded image URL or null if failed
     */
    suspend fun uploadImage(uri: Uri, chatId: String): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("CloudinaryRepository", "Uploading image: $uri")
            
            // Copy to temp file. A null stream previously left the temp file empty
            // and still uploaded it, producing a valid-looking URL for a 0-byte image.
            val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
            val copied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                true
            } ?: false
            if (!copied) {
                tempFile.delete()
                android.util.Log.e("CloudinaryRepository", "Cannot open input stream for $uri")
                return@withContext null
            }

            // Upload to Cloudinary
            val safeChatId = chatId.replace(":", "_").replace("/", "_")
            val params = ObjectUtils.asMap(
                "folder", "openchat/images/$safeChatId",
                "resource_type", "image"
            )
            
            val result = cloudinary.uploader().upload(tempFile, params)
            val url = result["secure_url"] as? String
            
            // Cleanup
            tempFile.delete()
            
            android.util.Log.d("CloudinaryRepository", "Image upload success: $url")
            url
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryRepository", "Failed to upload image", e)
            null
        }
    }
    
    /**
     * Upload a video to Cloudinary
     * @param uri The video URI
     * @param chatId The chat ID for folder organization
     * @return The uploaded video URL or null if failed
     */
    suspend fun uploadVideo(uri: Uri, chatId: String): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("CloudinaryRepository", "Uploading video: $uri")
            
            // Get file extension
            val extension = context.contentResolver.getType(uri)?.let { mime ->
                when {
                    mime.contains("mp4") -> ".mp4"
                    mime.contains("3gp") -> ".3gp"
                    else -> ".mp4"
                }
            } ?: ".mp4"
            
            // Copy to temp file
            val tempFile = File.createTempFile("upload", extension, context.cacheDir)
            val copied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                true
            } ?: false
            if (!copied) {
                tempFile.delete()
                android.util.Log.e("CloudinaryRepository", "Cannot open input stream for $uri")
                return@withContext null
            }

            // Upload to Cloudinary
            val safeChatId = chatId.replace(":", "_").replace("/", "_")
            val params = ObjectUtils.asMap(
                "folder", "openchat/videos/$safeChatId",
                "resource_type", "video"
            )
            
            val result = cloudinary.uploader().upload(tempFile, params)
            val url = result["secure_url"] as? String
            
            // Cleanup
            tempFile.delete()
            
            android.util.Log.d("CloudinaryRepository", "Video upload success: $url")
            url
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryRepository", "Failed to upload video", e)
            null
        }
    }
    
    /**
     * Upload a voice message to Cloudinary
     * @param uri The audio URI
     * @param chatId The chat ID for folder organization
     * @return The uploaded audio URL or null if failed
     */
    suspend fun uploadVoice(uri: Uri, chatId: String): String? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("voice", ".3gp", context.cacheDir)
            // Previously a null stream left the temp file empty and still uploaded
            // it, returning a valid-looking URL for a 0-byte "voice message".
            val copied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                true
            } ?: false
            if (!copied) {
                tempFile.delete()
                return@withContext null
            }

            // The old limit was 500 KB, roughly 15 seconds of AAC at the recorder's
            // bitrate - so nearly every real voice note was rejected and every send
            // failed with "Failed to upload voice message".
            val maxVoiceBytes = 10L * 1024 * 1024
            if (tempFile.length() > maxVoiceBytes) {
                tempFile.delete()
                android.util.Log.w("CloudinaryRepository", "Voice file too large: ${tempFile.length()} bytes")
                return@withContext null
            }

            val safeChatId = chatId.replace(":", "_").replace("/", "_")
            val params = ObjectUtils.asMap(
                "folder", "openchat/voice/$safeChatId",
                "resource_type", "video"
            )

            val result = cloudinary.uploader().upload(tempFile, params)
            val url = result["secure_url"] as? String

            tempFile.delete()
            url
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryRepository", "Failed to upload voice message", e)
            null
        }
    }

    /**
     * Upload a file/document to Cloudinary
     * @param uri The file URI
     * @param chatId The chat ID for folder organization
     * @param fileName Original filename for display
     * @return The uploaded file URL or null if failed
     */
    suspend fun uploadFile(uri: Uri, chatId: String, fileName: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("CloudinaryRepository", "Uploading file: $uri")
            
            // Get file extension from name or URI
            val extension = fileName?.substringAfterLast(".", "")?.let { "." + it } 
                ?: context.contentResolver.getType(uri)?.let { mime ->
                    when {
                        mime.contains("pdf") -> ".pdf"
                        mime.contains("doc") -> ".doc"
                        mime.contains("docx") -> ".docx"
                        mime.contains("txt") -> ".txt"
                        mime.contains("zip") -> ".zip"
                        else -> ""
                    }
                } 
                ?: ""
            
            // Copy to temp file
            val tempFile = File.createTempFile("upload", extension.ifEmpty { ".file" }, context.cacheDir)
            val copied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                true
            } ?: false
            if (!copied) {
                tempFile.delete()
                android.util.Log.e("CloudinaryRepository", "Cannot open input stream for $uri")
                return@withContext null
            }

            // Upload to Cloudinary
            val safeChatId = chatId.replace(":", "_").replace("/", "_")
            val displayName = fileName ?: "file${extension}"
            
            val params = ObjectUtils.asMap(
                "folder", "openchat/files/$safeChatId",
                "resource_type", "raw",
                "public_id", displayName.substringBeforeLast(".") + "_${System.currentTimeMillis()}"
            )
            
            val result = cloudinary.uploader().upload(tempFile, params)
            val url = result["secure_url"] as? String
            
            // Cleanup
            tempFile.delete()
            
            android.util.Log.d("CloudinaryRepository", "File upload success: $url")
            url
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryRepository", "Failed to upload file", e)
            null
        }
    }
}
