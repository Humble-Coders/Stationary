package com.humblecoders.stationary.util


import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "document.pdf"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }

        return fileName
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                fileSize = cursor.getLong(sizeIndex)
            }
        }

        return fileSize
    }

    fun isValidPdfFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        val fileSize = getFileSize(context, uri)

        return fileName.endsWith(".pdf", ignoreCase = true) &&
                fileSize > 0 &&
                fileSize < 50 * 1024 * 1024 // 50MB limit
    }
}