package com.humblecoders.stationary.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class DocumentItem(
    val id: String = "",
    val uri: android.net.Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val fileType: FileType = FileType.PDF,
    val pageCount: Int = 0,
    val needsUserPageInput: Boolean = false,
    val userInputPageCount: Int = 0,
    val printSettings: PrintSettings = PrintSettings(),
    val calculatedPrice: Double = 0.0,
    val previewBitmap: android.graphics.Bitmap? = null,
    val isExpanded: Boolean = false // For UI expansion state
) {
    // Helper to get effective page count
    fun getEffectivePageCount(): Int {
        return when (fileType) {
            FileType.PDF -> {
                if (needsUserPageInput) userInputPageCount else pageCount
            }
            FileType.DOCX, FileType.DOC, FileType.PPTX, FileType.PPT, 
            FileType.XLSX, FileType.XLS, FileType.TXT, FileType.RTF, FileType.IMAGE -> 1
        }
    }
}

data class PrintOrder(
    val orderId: String = "",
    val customerId: String = "",
    val customerPhone: String = "",
    val shopId: String = "", // GBLOCK or COS
    val fileType: String = "PDF",
    val individualDocuments: List<Map<String, Any>> = emptyList(), // Changed to List<Map<String, Any>>
    val documentCount: Int = 0, // NEW FIELD - number of documents
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val paymentAmount: Double = 0.0,
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val orderStatus: OrderStatus = OrderStatus.SUBMITTED,

    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

// Rest of the existing models remain the same...

data class ShopSettings(
    val shopId: String = "GBLOCK",

    @PropertyName("isShopOpen")
    val shopOpen: Boolean = true,

    @PropertyName("pricing")
    val pricePerPage: PricePerPage = PricePerPage()
) {
    constructor() : this(
        shopId = "GBLOCK",
        shopOpen = true,
        pricePerPage = PricePerPage()
    )
}

// In models.kt - Replace the PrintSettings data class

data class PrintSettings(
    val colorMode: ColorMode = ColorMode.BW,
    val pagesToPrint: PageSelection = PageSelection.ALL,
    val customPages: String = "", // Keep for backward compatibility
    val customBWPages: String = "", // NEW: Black and white custom pages
    val customColorPages: String = "", // NEW: Color custom pages
    val copies: Int = 1,
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val quality: Quality = Quality.NORMAL,
    val printOnBothSides: Boolean = false
) {
    // Helper function to get effective pages for black and white
    fun getEffectiveBWPages(totalPages: Int): List<Int> {
        return when {
            customBWPages.isNotEmpty() -> parsePageRange(customBWPages, totalPages)
            colorMode == ColorMode.BW && pagesToPrint == PageSelection.ALL -> (1..totalPages).toList()
            colorMode == ColorMode.BW && customPages.isNotEmpty() -> parsePageRange(customPages, totalPages)
            else -> emptyList()
        }
    }

    // Helper function to get effective pages for color
    fun getEffectiveColorPages(totalPages: Int): List<Int> {
        return when {
            customColorPages.isNotEmpty() -> parsePageRange(customColorPages, totalPages)
            colorMode == ColorMode.COLOR && pagesToPrint == PageSelection.ALL -> (1..totalPages).toList()
            colorMode == ColorMode.COLOR && customPages.isNotEmpty() -> parsePageRange(customPages, totalPages)
            else -> emptyList()
        }
    }


    private fun parsePageRange(pageRange: String, totalPages: Int): List<Int> {
        if (pageRange.isEmpty()) return emptyList()

        try {
            val pages = mutableSetOf<Int>()
            val parts = pageRange.split(",")

            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("-")) {
                    val range = trimmed.split("-")
                    if (range.size == 2) {
                        val start = range[0].trim().toInt().coerceIn(1, totalPages)
                        val end = range[1].trim().toInt().coerceIn(1, totalPages)
                        for (i in start..end) {
                            pages.add(i)
                        }
                    }
                } else {
                    val page = trimmed.toInt()
                    if (page in 1..totalPages) {
                        pages.add(page)
                    }
                }
            }

            return pages.toList().sorted()
        } catch (e: Exception) {
            return emptyList()
        }
    }
}

data class PricePerPage(
    val bw: Double = 2.0,
    val color: Double = 5.0
)

data class PaymentTransactionData(
    val razorpayOrderId: String,
    val razorpayPaymentId: String,
    val amount: Double
)

enum class ColorMode(val displayName: String) {
    COLOR("Color"),
    BW("Black & White")
}

enum class PageSelection(val displayName: String) {
    ALL("All Pages"),
    CUSTOM("Custom Pages")
}

enum class PaperSize(val displayName: String) {
    A4("A4"),
    A3("A3"),
    LETTER("Letter")
}

enum class Orientation(val displayName: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class Quality(val displayName: String) {
    DRAFT("Draft"),
    NORMAL("Normal"),
    HIGH("High")
}

enum class PaymentStatus {
    UNPAID, PAID, FAILED
}

enum class OrderStatus {
    SUBMITTED, QUEUED, PRINTED
}

sealed class PaymentResult {
    data class Success(
        val paymentId: String,
        val orderId: String,
        val amount: Double
    ) : PaymentResult()

    data class Error(
        val errorCode: Int,
        val errorMessage: String
    ) : PaymentResult()
}

enum class FileType(val displayName: String, val extension: String, val mimeType: String) {
    PDF("PDF Document", ".pdf", "application/pdf"),
    DOCX("Word Document", ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    DOC("Word Document (Legacy)", ".doc", "application/msword"),
    PPTX("PowerPoint Presentation", ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PPT("PowerPoint Presentation (Legacy)", ".ppt", "application/vnd.ms-powerpoint"),
    XLSX("Excel Spreadsheet", ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    XLS("Excel Spreadsheet (Legacy)", ".xls", "application/vnd.ms-excel"),
    TXT("Text Document", ".txt", "text/plain"),
    RTF("Rich Text Format", ".rtf", "application/rtf"),
    IMAGE("Image", "", "image/jpeg");

    companion object {
        /**
         * Get file extension from filename for images
         * Returns the original extension from the filename
         */
        fun getImageExtension(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            return if (lastDot >= 0 && lastDot < fileName.length - 1) {
                fileName.substring(lastDot).lowercase()
            } else {
                ".jpg" // Default fallback
            }
        }

        /**
         * Get display name from fileType string stored in Firestore.
         * Handles both MIME type (from website / app) and extension (legacy from app).
         */
        fun getDisplayNameFromFileTypeString(fileType: String?): String {
            if (fileType.isNullOrBlank()) return "Document"
            val s = fileType.trim()
            return when {
                s.contains("/") -> when {
                    s == "application/pdf" -> "PDF"
                    s.startsWith("image/") -> "Image"
                    s == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "Word"
                    s == "application/msword" -> "Word (Legacy)"
                    s == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PowerPoint"
                    s == "application/vnd.ms-powerpoint" -> "PowerPoint (Legacy)"
                    s == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "Excel"
                    s == "application/vnd.ms-excel" -> "Excel (Legacy)"
                    s == "text/plain" -> "Text"
                    s == "application/rtf" || s == "text/rtf" -> "Rich Text"
                    else -> "Document"
                }
                else -> when (s) {
                    ".pdf" -> "PDF"
                    ".docx" -> "Word"
                    ".doc" -> "Word (Legacy)"
                    ".pptx" -> "PowerPoint"
                    ".ppt" -> "PowerPoint (Legacy)"
                    ".xlsx" -> "Excel"
                    ".xls" -> "Excel (Legacy)"
                    ".txt" -> "Text"
                    ".rtf" -> "Rich Text"
                    ".jpg", ".jpeg", ".png", ".webp" -> "Image"
                    else -> "Document"
                }
            }
        }
    }
}

enum class ShopId(val displayName: String) {
    GBLOCK("GBlock"),
    COS("Cos")
}

data class BugReport(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val subject: String = "",
    val description: String = "",
    val screenshotUrl: String = "",
    val status: BugReportStatus = BugReportStatus.PENDING,
    val createdAt: Timestamp = Timestamp.now()
)

enum class BugReportStatus {
    PENDING, REVIEWED, RESOLVED
}