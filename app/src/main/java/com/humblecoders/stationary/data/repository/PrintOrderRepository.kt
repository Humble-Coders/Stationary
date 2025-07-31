package com.humblecoders.stationary.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.humblecoders.stationary.data.model.ColorMode
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PaymentTransactionData
import com.humblecoders.stationary.data.model.PaymentStatus
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.*

class PrintOrderRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val ordersCollection = firestore.collection("print_orders")
    private val storageRef = storage.reference.child("documents")

    suspend fun uploadDocument(uri: Uri): String {
        val orderId = UUID.randomUUID().toString()
        val documentRef = storageRef.child("$orderId/original.pdf")

        val uploadTask = documentRef.putFile(uri).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }

    suspend fun createOrder(order: PrintOrder): String {
        val orderId = if (order.orderId.isEmpty()) UUID.randomUUID().toString() else order.orderId
        val orderWithId = order.copy(orderId = orderId)

        ordersCollection.document(orderId).set(orderWithId).await()
        return orderId
    }

    suspend fun updateOrderPayment(orderId: String, paymentData: PaymentTransactionData) {
        val updates = mapOf(
            "paymentStatus" to PaymentStatus.PAID,
            "isPaid" to true,
            "razorpayOrderId" to paymentData.razorpayOrderId,
            "razorpayPaymentId" to paymentData.razorpayPaymentId,
            "paymentAmount" to paymentData.amount,
            "canAutoPrint" to true, // Will be true if hasSettings is also true
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        ordersCollection.document(orderId).update(updates).await()
    }

    suspend fun updateOrderSettings(orderId: String, settings: PrintSettings, pageCount: Int) {
        val canAutoPrint = true // Will be true if isPaid is also true
        val updates = mapOf(
            "printSettings" to settings,
            "hasSettings" to true,
            "canAutoPrint" to canAutoPrint,
            "pageCount" to pageCount,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        ordersCollection.document(orderId).update(updates).await()
    }

    fun calculatePrice(settings: PrintSettings, pageCount: Int): Double {
        val pricePerPage = when (settings.colorMode) {
            ColorMode.COLOR -> 5.0
            ColorMode.BW -> 2.0
        }
        return pricePerPage * pageCount * settings.copies
    }

    fun observeUserOrders(customerId: String): Flow<List<PrintOrder>> {
        return ordersCollection
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PrintOrder::class.java)
                }
            }
    }

    suspend fun getOrder(orderId: String): PrintOrder? {
        return try {
            val doc = ordersCollection.document(orderId).get().await()
            doc.toObject(PrintOrder::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Replace the calculatePrice method in PrintOrderRepository.kt

     fun calculatePrice(settings: PrintSettings, pageCount: Int, shopSettings: ShopSettings): Double {
        val pricePerPage = when (settings.colorMode) {
            ColorMode.COLOR -> shopSettings.pricePerPage.color
            ColorMode.BW -> shopSettings.pricePerPage.bw
        }

        val actualPagesToPrint = calculateActualPagesToPrint(settings, pageCount)
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
            return totalPages // Fallback to all pages if parsing fails
        }
    }
}
