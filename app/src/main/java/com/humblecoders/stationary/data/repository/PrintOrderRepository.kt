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


    fun calculatePrice(settings: PrintSettings, pageCount: Int, shopSettings: ShopSettings, fileType: FileType? = null): Double {
        return when (fileType) {
            FileType.DOCX, FileType.PPTX, FileType.IMAGE -> { // Add FileType.IMAGE here
                // DOCX, PPTX and IMAGE files are treated as single unit
                val pricePerPage = when (settings.colorMode) {
                    ColorMode.COLOR -> shopSettings.pricePerPage.color
                    ColorMode.BW -> shopSettings.pricePerPage.bw
                }
                pricePerPage * settings.copies
            }
            FileType.PDF, null -> {
                calculatePdfPrice(settings, pageCount, shopSettings)
            }
        }
    }

    private fun calculatePdfPrice(settings: PrintSettings, totalPages: Int, shopSettings: ShopSettings): Double {
        val bwPages = settings.getEffectiveBWPages(totalPages)
        val colorPages = settings.getEffectiveColorPages(totalPages)

        val bwCost = bwPages.size * shopSettings.pricePerPage.bw
        val colorCost = colorPages.size * shopSettings.pricePerPage.color

        return (bwCost + colorCost) * settings.copies
    }

}