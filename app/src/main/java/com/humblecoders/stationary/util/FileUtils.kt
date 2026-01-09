package com.humblecoders.stationary.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

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


    /**
     * Extract file extension from filename
     */
    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot).lowercase()
        } else {
            ""
        }
    }

    fun isPptxFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".pptx", ignoreCase = true)
    }

    fun isPptFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".ppt", ignoreCase = true)
    }

    fun isXlsxFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".xlsx", ignoreCase = true)
    }

    fun isXlsFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".xls", ignoreCase = true)
    }

    fun isDocFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".doc", ignoreCase = true)
    }

    fun isTxtFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".txt", ignoreCase = true)
    }

    fun isRtfFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".rtf", ignoreCase = true)
    }

    // Support all common image types
    fun isImageFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        val extension = getFileExtension(fileName)
        return extension in listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", 
            ".img", ".heic", ".heif", ".svg", ".ico", ".tiff", ".tif"
        )
    }

    fun isValidFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        val fileSize = getFileSize(context, uri)

        val isValidFormat = isPdfFile(context, uri) ||
                isDocxFile(context, uri) ||
                isDocFile(context, uri) ||
                isPptxFile(context, uri) ||
                isPptFile(context, uri) ||
                isXlsxFile(context, uri) ||
                isXlsFile(context, uri) ||
                isTxtFile(context, uri) ||
                isRtfFile(context, uri) ||
                isImageFile(context, uri)

        return isValidFormat &&
                fileSize > 0 &&
                fileSize < 50 * 1024 * 1024 // 50MB limit
    }

    fun isDocxFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".docx", ignoreCase = true)
    }

    fun isPdfFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri)
        return fileName.endsWith(".pdf", ignoreCase = true)
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PDF_UTILS", "Cannot read PDF: ${e.message}")
            null // Return null when PDF cannot be read
        }
    }
}