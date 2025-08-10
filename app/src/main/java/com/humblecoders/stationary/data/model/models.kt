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
            FileType.DOCX -> 1
            FileType.PPTX -> 1
            FileType.IMAGE -> 1
        }
    }
}

data class PrintOrder(
    val orderId: String = "",
    val customerId: String = "",
    val customerPhone: String = "",
    val documentName: List<String> = emptyList(), // Changed to List<String>
    val documentUrl: List<String> = emptyList(), // Changed to List<String>
    val documentSize: Long = 0, // Total size of all documents
    val fileType: String = "PDF",
    val pageCount: Int = 0, // Total pages across all documents
    val printSettings: List<Map<String, Any>> = emptyList(), // Changed to List<Map<String, Any>>
    val individualDocuments: List<Map<String, Any>> = emptyList(), // Changed to List<Map<String, Any>>
    val documentCount: Int = 0, // NEW FIELD - number of documents
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val paymentAmount: Double = 0.0,
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val orderStatus: OrderStatus = OrderStatus.SUBMITTED,

    @PropertyName("hasSettings")
    val hasSettings: Boolean = false,

    @PropertyName("isPaid")
    val isPaid: Boolean = false,

    @PropertyName("canAutoPrint")
    val canAutoPrint: Boolean = false,

    val queuePriority: Int = 0,

    @PropertyName("isInQueue")
    val inQueue: Boolean = false,

    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    @PropertyName("isPaid")
    fun getIsPaid(): Boolean = isPaid
}

// Rest of the existing models remain the same...

data class ShopSettings(
    val shopId: String = "default",

    @PropertyName("shopOpen")
    val shopOpen: Boolean = true,

    @PropertyName("autoPrintEnabled")
    val autoPrintEnabled: Boolean = false,

    val pricePerPage: PricePerPage = PricePerPage()
) {
    constructor() : this(
        shopId = "default",
        shopOpen = true,
        autoPrintEnabled = false,
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
    val quality: Quality = Quality.NORMAL
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

enum class FileType(val displayName: String, val extension: String) {
    PDF("PDF Document", ".pdf"),
    DOCX("Word Document", ".docx"),
    PPTX("PowerPoint Presentation", ".pptx"), // Add this line
    IMAGE("Image", ".jpg") // Add this line

}