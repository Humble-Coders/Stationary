package com.humblecoders.stationary.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class PrintOrder(
    val orderId: String = "",
    val customerId: String = "",
    val customerPhone: String = "",
    val documentName: String = "",
    val documentUrl: String = "",
    val documentSize: Long = 0,
    val pageCount: Int = 0,
    val printSettings: PrintSettings? = null,
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
    // No-arg constructor for Firestore
    constructor() : this(
        orderId = "",
        customerId = "",
        customerPhone = "",
        documentName = "",
        documentUrl = "",
        documentSize = 0,
        pageCount = 0,
        printSettings = null,
        paymentStatus = PaymentStatus.UNPAID,
        paymentAmount = 0.0,
        razorpayOrderId = "",
        razorpayPaymentId = "",
        orderStatus = OrderStatus.SUBMITTED,
        hasSettings = false,
        isPaid = false,
        canAutoPrint = false,
        queuePriority = 0,
        inQueue = false,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now()
    )
}

data class ShopSettings(
    val shopId: String = "default",

    @PropertyName("shopOpen")
    val shopOpen: Boolean = true,

    @PropertyName("autoPrintEnabled")
    val autoPrintEnabled: Boolean = false,

    val pricePerPage: PricePerPage = PricePerPage()
) {
    // No-arg constructor for Firestore
    constructor() : this(
        shopId = "default",
        shopOpen = true,
        autoPrintEnabled = false,
        pricePerPage = PricePerPage()
    )
}

data class PrintSettings(
    val colorMode: ColorMode = ColorMode.BW,
    val pagesToPrint: PageSelection = PageSelection.ALL,
    val customPages: String = "",
    val copies: Int = 1,
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val quality: Quality = Quality.NORMAL
)



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
    SUBMITTED, QUEUED, PRINTING, COMPLETED, CANCELLED
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

