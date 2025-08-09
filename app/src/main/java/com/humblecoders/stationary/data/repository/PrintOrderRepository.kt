package com.humblecoders.stationary.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.humblecoders.stationary.data.model.ColorMode
import com.humblecoders.stationary.data.model.FileType
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PaymentTransactionData
import com.humblecoders.stationary.data.model.PaymentStatus
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.*

class PrintOrderRepository(firestore: FirebaseFirestore, storage: FirebaseStorage) {

    private val ordersCollection = firestore.collection("print_orders")
    private val storageRef = storage.reference.child("documents")
    private val gson = Gson()

    suspend fun uploadDocument(uri: Uri): String {
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString()
        val fileName = "${timestamp}_${uniqueId}.pdf"
        val documentRef = storageRef.child(fileName)

        val uploadTask = documentRef.putFile(uri).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }

    // Updated method to handle multiple documents
    suspend fun uploadMultipleDocuments(uris: List<Uri>): List<String> {
        val urls = mutableListOf<String>()
        for (uri in uris) {
            val url = uploadDocument(uri)
            urls.add(url)
        }
        return urls
    }

    private fun PrintOrder.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "orderId" to orderId,
            "customerId" to customerId,
            "customerPhone" to customerPhone,
            "documentName" to documentName, // Now array
            "documentUrl" to documentUrl, // Now array
            "documentSize" to documentSize,
            "pageCount" to pageCount,
            "printSettings" to printSettings, // Now array of maps
            "individualDocuments" to individualDocuments, // Now array of maps
            "documentCount" to documentCount, // NEW FIELD
            "paymentStatus" to paymentStatus,
            "paymentAmount" to paymentAmount,
            "razorpayOrderId" to razorpayOrderId,
            "razorpayPaymentId" to razorpayPaymentId,
            "orderStatus" to orderStatus,
            "hasSettings" to hasSettings,
            "isPaid" to isPaid,
            "canAutoPrint" to canAutoPrint,
            "queuePriority" to queuePriority,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "fileType" to fileType
        )
    }

    suspend fun createOrder(order: PrintOrder): String {
        val orderId = if (order.orderId.isEmpty()) UUID.randomUUID().toString() else order.orderId
        val orderWithId = order.copy(orderId = orderId)

        ordersCollection.document(orderId).set(orderWithId.toFirestoreMap()).await()
        return orderId
    }

    suspend fun updateOrderPayment(orderId: String, paymentData: PaymentTransactionData) {
        val updates = mapOf(
            "paymentStatus" to PaymentStatus.PAID,
            "isPaid" to true,
            "razorpayOrderId" to paymentData.razorpayOrderId,
            "razorpayPaymentId" to paymentData.razorpayPaymentId,
            "paymentAmount" to paymentData.amount,
            "canAutoPrint" to true,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        ordersCollection.document(orderId).update(updates).await()
    }

    fun observeUserOrders(customerId: String): Flow<List<PrintOrder>> {
        return ordersCollection
            .whereEqualTo("customerId", customerId)
            .snapshots()
            .map { snapshot ->
                try {
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(PrintOrder::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.createdAt.toDate() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            .catch { e ->
                emit(emptyList())
            }
    }

    fun calculateTotalPrice(individualDocuments: List<Map<String, Any>>, shopSettings: ShopSettings): Double {
        return individualDocuments.sumOf { docMap ->
            val printSettingsMap = docMap["printSettings"] as? Map<String, Any>
            val printSettings = printSettingsMap?.let { parseMapToPrintSettings(it) } ?: PrintSettings()
            val pageCount = (docMap["pageCount"] as? Number)?.toInt() ?: 1
            val fileTypeStr = (docMap["fileType"] as? String) ?: ".pdf"
            val fileType = if (fileTypeStr == ".docx") FileType.DOCX else FileType.PDF

            calculatePrice(printSettings, pageCount, shopSettings, fileType)
        }
    }

    private fun parseMapToPrintSettings(map: Map<String, Any>): PrintSettings {
        return try {
            PrintSettings(
                colorMode = parseColorMode(map["colorMode"] as? String),
                pagesToPrint = parsePageSelection(map["pagesToPrint"] as? String),
                customPages = map["customPages"] as? String ?: "",
                copies = (map["copies"] as? Number)?.toInt() ?: 1,
                paperSize = parsePaperSize(map["paperSize"] as? String),
                orientation = parseOrientation(map["orientation"] as? String),
                quality = parseQuality(map["quality"] as? String)
            )
        } catch (e: Exception) {
            PrintSettings()
        }
    }

    private fun parseColorMode(value: String?): ColorMode {
        return when (value) {
            "COLOR" -> ColorMode.COLOR
            "BW" -> ColorMode.BW
            else -> ColorMode.BW
        }
    }

    private fun parsePageSelection(value: String?): PageSelection {
        return when (value) {
            "ALL" -> PageSelection.ALL
            "CUSTOM" -> PageSelection.CUSTOM
            else -> PageSelection.ALL
        }
    }

    private fun parsePaperSize(value: String?): com.humblecoders.stationary.data.model.PaperSize {
        return when (value) {
            "A4" -> com.humblecoders.stationary.data.model.PaperSize.A4
            "A3" -> com.humblecoders.stationary.data.model.PaperSize.A3
            "LETTER" -> com.humblecoders.stationary.data.model.PaperSize.LETTER
            else -> com.humblecoders.stationary.data.model.PaperSize.A4
        }
    }

    private fun parseOrientation(value: String?): com.humblecoders.stationary.data.model.Orientation {
        return when (value) {
            "PORTRAIT" -> com.humblecoders.stationary.data.model.Orientation.PORTRAIT
            "LANDSCAPE" -> com.humblecoders.stationary.data.model.Orientation.LANDSCAPE
            else -> com.humblecoders.stationary.data.model.Orientation.PORTRAIT
        }
    }

    private fun parseQuality(value: String?): com.humblecoders.stationary.data.model.Quality {
        return when (value) {
            "DRAFT" -> com.humblecoders.stationary.data.model.Quality.DRAFT
            "NORMAL" -> com.humblecoders.stationary.data.model.Quality.NORMAL
            "HIGH" -> com.humblecoders.stationary.data.model.Quality.HIGH
            else -> com.humblecoders.stationary.data.model.Quality.NORMAL
        }
    }

    fun calculatePrice(settings: PrintSettings, pageCount: Int, shopSettings: ShopSettings, fileType: FileType? = null): Double {
        val pricePerPage = when (settings.colorMode) {
            ColorMode.COLOR -> shopSettings.pricePerPage.color
            ColorMode.BW -> shopSettings.pricePerPage.bw
        }

        val actualPagesToPrint = when (fileType) {
            FileType.DOCX -> 1 // DOCX files are treated as single unit regardless of actual pages
            FileType.PDF, null -> calculateActualPagesToPrint(settings, pageCount)
        }

        return pricePerPage * actualPagesToPrint * settings.copies
    }

    private fun calculateActualPagesToPrint(settings: PrintSettings, totalPages: Int): Int {
        return when (settings.pagesToPrint) {
            PageSelection.ALL -> totalPages
            PageSelection.CUSTOM -> {
                if (settings.customPages.isEmpty()) {
                    totalPages
                } else {
                    parsePageRange(settings.customPages, totalPages)
                }
            }
        }
    }

    private fun parsePageRange(pageRange: String, totalPages: Int): Int {
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

            return pages.size.coerceAtLeast(1)
        } catch (e: Exception) {
            return totalPages
        }
    }
}