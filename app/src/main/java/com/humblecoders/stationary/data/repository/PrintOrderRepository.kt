package com.humblecoders.stationary.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun uploadDocument(uri: Uri, fileType: FileType, originalFileName: String? = null): String {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        Log.d("PrintOrderRepository", "=== Starting uploadDocument ===")
        Log.d("PrintOrderRepository", "URI: $uri")
        Log.d("PrintOrderRepository", "URI Scheme: ${uri.scheme}")
        Log.d("PrintOrderRepository", "URI Authority: ${uri.authority}")
        Log.d("PrintOrderRepository", "URI Path: ${uri.path}")
        Log.d("PrintOrderRepository", "File Type: $fileType (extension: ${fileType.extension})")
        Log.d("PrintOrderRepository", "Original File Name: $originalFileName")
        
        // Check authentication
        if (currentUser == null) {
            Log.e("PrintOrderRepository", "❌ User not authenticated!")
            throw Exception("User not authenticated")
        }
        
        Log.d("PrintOrderRepository", "✅ User authenticated: ${currentUser.uid}")
        Log.d("PrintOrderRepository", "User email: ${currentUser.email}")
        
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString()
        
        // For images, extract extension from original filename to preserve format (.png, .jpeg, etc.)
        // For other file types, use the extension from FileType enum
        val extension = if (fileType == FileType.IMAGE && originalFileName != null) {
            FileType.getImageExtension(originalFileName)
        } else {
            fileType.extension
        }
        
        Log.d("PrintOrderRepository", "Using extension: $extension")
        val fileName = "${timestamp}_${uniqueId}$extension"
        val documentRef = storageRef.child(fileName)
        
        Log.d("PrintOrderRepository", "Storage path: documents/$fileName")
        Log.d("PrintOrderRepository", "Full storage reference: ${documentRef.path}")
        
        try {
            Log.d("PrintOrderRepository", "Attempting to upload file to Firebase Storage...")
            Log.d("PrintOrderRepository", "Using putFile() with URI: $uri")
            val uploadTask = documentRef.putFile(uri).await()
            Log.d("PrintOrderRepository", "✅ File upload successful!")
            Log.d("PrintOrderRepository", "Upload task metadata: ${uploadTask.metadata}")
            Log.d("PrintOrderRepository", "Upload task bytes transferred: ${uploadTask.bytesTransferred}")
            
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
            Log.d("PrintOrderRepository", "✅ Download URL obtained: $downloadUrl")
            Log.d("PrintOrderRepository", "=== Upload completed successfully ===")
            
            return downloadUrl
        } catch (e: Exception) {
            Log.e("PrintOrderRepository", "❌ Upload failed!", e)
            Log.e("PrintOrderRepository", "Error type: ${e.javaClass.simpleName}")
            Log.e("PrintOrderRepository", "Error message: ${e.message}")
            Log.e("PrintOrderRepository", "Error cause: ${e.cause}")
            e.printStackTrace()
            throw e
        }
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