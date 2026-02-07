package com.humblecoders.stationary.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.humblecoders.stationary.data.model.FileType

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        // Handle file:// URIs directly
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists()) {
                    // Extract original filename from the cached filename (remove timestamp prefix)
                    val name = file.name
                    val underscoreIndex = name.indexOf('_')
                    return if (underscoreIndex > 0 && underscoreIndex < name.length - 1) {
                        // Check if prefix looks like a timestamp (all digits)
                        val prefix = name.substring(0, underscoreIndex)
                        if (prefix.all { it.isDigit() }) {
                            name.substring(underscoreIndex + 1)
                        } else {
                            name
                        }
                    } else {
                        name
                    }
                }
            }
        }

        // Handle content:// URIs via ContentResolver
        var fileName = "document.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FileUtils", "Could not query filename: ${e.message}")
        }

        return fileName
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        // Handle file:// URIs directly
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists()) {
                    return file.length()
                }
            }
        }

        // Handle content:// URIs via ContentResolver
        var fileSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FileUtils", "Could not query file size: ${e.message}")
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

    /**
     * Get MIME type from content provider (when available).
     * May return null or empty string for unknown/generic types.
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.getType(uri)?.takeIf { it != "*/*" }
        } catch (e: Exception) {
            android.util.Log.w("FileUtils", "Could not get MIME type: ${e.message}")
            null
        }
    }

    /**
     * Map MIME type string to FileType. Returns null if not a supported type.
     */
    fun getFileTypeFromMimeType(mimeType: String?): FileType? {
        if (mimeType.isNullOrBlank()) return null
        val mime = mimeType.lowercase().trim()
        return when {
            mime == "application/pdf" -> FileType.PDF
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileType.DOCX
            mime == "application/msword" -> FileType.DOC
            mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> FileType.PPTX
            mime == "application/vnd.ms-powerpoint" -> FileType.PPT
            mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileType.XLSX
            mime == "application/vnd.ms-excel" -> FileType.XLS
            mime == "text/plain" -> FileType.TXT
            mime == "application/rtf" || mime == "text/rtf" -> FileType.RTF
            mime.startsWith("image/") -> FileType.IMAGE
            else -> null
        }
    }

    /**
     * Detect file type from file content (magic bytes) when extension and MIME are unavailable.
     * Used for shared files that end up as file:// URIs without extension.
     */
    fun detectFileTypeFromMagicBytes(context: Context, uri: Uri): FileType? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = ByteArray(12)
                val read = inputStream.read(header)
                if (read < 4) return null
                when {
                    // PDF: %PDF
                    read >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                        header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> FileType.PDF
                    // JPEG: FF D8 FF
                    read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> FileType.IMAGE
                    // PNG: 89 50 4E 47 0D 0A 1A 0A
                    read >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                        header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> FileType.IMAGE
                    // GIF: GIF87a or GIF89a
                    read >= 6 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
                        header[2] == 0x46.toByte() && (header[3] == 0x38.toByte() || header[3] == 0x39.toByte()) -> FileType.IMAGE
                    else -> null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FileUtils", "Magic byte detection failed: ${e.message}")
            null
        }
    }

    /**
     * Detect file type from URI: use filename extension if present, then MIME type, then magic bytes.
     * When filename has no extension and type is detected from MIME or magic bytes, the returned name has the extension appended.
     * @return Pair(detected FileType or null, filename to use for display/storage)
     */
    fun detectFileTypeAndFileName(context: Context, uri: Uri): Pair<FileType?, String> {
        val rawName = getFileName(context, uri)
        val ext = getFileExtension(rawName)

        if (ext.isNotEmpty()) {
            // Has extension: use existing extension-based detection
            val type = when {
                isPdfFile(context, uri) -> FileType.PDF
                isDocxFile(context, uri) -> FileType.DOCX
                isDocFile(context, uri) -> FileType.DOC
                isPptxFile(context, uri) -> FileType.PPTX
                isPptFile(context, uri) -> FileType.PPT
                isXlsxFile(context, uri) -> FileType.XLSX
                isXlsFile(context, uri) -> FileType.XLS
                isTxtFile(context, uri) -> FileType.TXT
                isRtfFile(context, uri) -> FileType.RTF
                isImageFile(context, uri) -> FileType.IMAGE
                else -> null
            }
            return Pair(type, rawName)
        }

        // No extension: try MIME type
        var type = getFileTypeFromMimeType(getMimeType(context, uri))
        if (type == null) {
            type = detectFileTypeFromMagicBytes(context, uri)
        }
        return if (type != null) {
            val suffix = if (type == FileType.IMAGE) ".jpg" else type.extension
            Pair(type, rawName + suffix)
        } else {
            Pair(null, rawName)
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
        val (type, _) = detectFileTypeAndFileName(context, uri)
        val fileSize = getFileSize(context, uri)
        return type != null && fileSize > 0 && fileSize < 50 * 1024 * 1024 // 50MB limit
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
            // Handle file:// URIs directly
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        android.os.ParcelFileDescriptor.open(
                            file,
                            android.os.ParcelFileDescriptor.MODE_READ_ONLY
                        )?.use { pfd ->
                            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                                renderer.pageCount
                            }
                        }
                    } else null
                } else null
            } else {
                // Handle content:// URIs via ContentResolver
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        renderer.pageCount
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PDF_UTILS", "Cannot read PDF: ${e.message}")
            null // Return null when PDF cannot be read
        }
    }
}