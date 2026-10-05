package com.openchat.app.core.extensions

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

fun ContentResolver.getFileName(uri: Uri): String {
    var name = ""
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

fun ContentResolver.getFileSize(uri: Uri): Long {
    var size = 0L
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0) {
                size = it.getLong(sizeIndex)
            }
        }
    }
    return size
}

fun ContentResolver.copyToFile(uri: Uri, destination: File): Boolean {
    return try {
        // Previously `true` was returned even when openInputStream() was null and
        // nothing was copied, so callers reported success for a 0-byte file.
        val input = openInputStream(uri) ?: return false
        input.use { inStream ->
            FileOutputStream(destination).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}
