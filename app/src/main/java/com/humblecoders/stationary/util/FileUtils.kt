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

    // Add these methods to FileUtils.kt

    fun getPdfPageCount(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(1024)
                var pageCount = 0
                var totalRead = 0
                val maxReadBytes = 50 * 1024 // Read first 50KB to find page count

                while (totalRead < maxReadBytes) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    val content = String(buffer, 0, bytesRead)
                    // Simple regex to count /Type /Page occurrences
                    val pageMatches = Regex("/Type\\s*/Page[^s]").findAll(content)
                    pageCount += pageMatches.count()

                    totalRead += bytesRead
                }

                // Fallback to file size estimation if no pages found
                if (pageCount == 0) {
                    val fileSize = getFileSize(context, uri)
                    pageCount = estimatePageCountFromSize(fileSize)
                }

                pageCount.coerceAtLeast(1)
            } ?: estimatePageCountFromSize(getFileSize(context, uri))
        } catch (e: Exception) {
            // Fallback to size estimation
            estimatePageCountFromSize(getFileSize(context, uri))
        }
    }

    private fun estimatePageCountFromSize(fileSize: Long): Int {
        val avgBytesPerPage = 100_000L
        return (fileSize / avgBytesPerPage).toInt().coerceAtLeast(1)
    }
}