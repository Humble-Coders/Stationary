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

    suspend fun uploadDocument(uri: Uri): String {
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString()
        val fileName = "${timestamp}_${uniqueId}.pdf"
        val documentRef = storageRef.child(fileName)

        val uploadTask = documentRef.putFile(uri).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }

    private fun PrintOrder.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "orderId" to orderId,
            "customerId" to customerId,
            "customerPhone" to customerPhone,
            "shopId" to shopId,
            "individualDocuments" to individualDocuments, // Now array of maps
            "documentCount" to documentCount, // NEW FIELD
            "paymentStatus" to paymentStatus,
            "paymentAmount" to paymentAmount,
            "razorpayOrderId" to razorpayOrderId,
            "razorpayPaymentId" to razorpayPaymentId,
            "orderStatus" to orderStatus,
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
            "razorpayOrderId" to paymentData.razorpayOrderId,
            "razorpayPaymentId" to paymentData.razorpayPaymentId,
            "paymentAmount" to paymentData.amount,
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


    /**
     * Calculate price using only customBWPages and customColorPages ranges
     * Parses the ranges and counts pages, then multiplies by price and copies
     */
    fun calculatePrice(settings: PrintSettings, shopSettings: ShopSettings): Double {
        val bwPageCount = parsePageRangeCount(settings.customBWPages)
        val colorPageCount = parsePageRangeCount(settings.customColorPages)

        val bwPrice = shopSettings.pricePerPage.bw
        val colorPrice = shopSettings.pricePerPage.color
        
        android.util.Log.d("PrintOrderRepository", "Calculating price - BW: $bwPageCount pages @ ₹$bwPrice, Color: $colorPageCount pages @ ₹$colorPrice, Copies: ${settings.copies}")

        val bwCost = bwPageCount * bwPrice
        val colorCost = colorPageCount * colorPrice

        return (bwCost + colorCost) * settings.copies
    }

    /**
     * Parse page range string and return count of pages
     * Supports formats: "1,2,3" or "1-5" or "1,2,5-10"
     */
    private fun parsePageRangeCount(pageRange: String): Int {
        if (pageRange.isEmpty()) return 0

        try {
            val pages = mutableSetOf<Int>()
            val parts = pageRange.split(",")

            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("-")) {
                    val range = trimmed.split("-")
                    if (range.size == 2) {
                        val start = range[0].trim().toInt().coerceAtLeast(1)
                        val end = range[1].trim().toInt().coerceAtLeast(1)
                        if (start <= end) {
                            for (i in start..end) {
                                pages.add(i)
                            }
                        }
                    }
                } else {
                    val page = trimmed.toInt()
                    if (page >= 1) {
                        pages.add(page)
                    }
                }
            }

            return pages.size
        } catch (e: Exception) {
            return 0
        }
    }

}