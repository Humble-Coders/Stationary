package com.humblecoders.stationary.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.humblecoders.stationary.data.model.DocumentItem
import kotlinx.coroutines.tasks.await

data class CreateOrderResponse(
    val success: Boolean,
    val orderId: String,
    val amount: Double
)

data class InitiatePaymentResponse(
    val success: Boolean,
    val razorpayOrderId: String,
    val amount: Double,
    val amountInPaise: Int,
    val currency: String,
    val keyId: String
)

data class VerifyPaymentResponse(
    val success: Boolean,
    val status: String,
    val message: String
)

class CloudFunctionsRepository {
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun createOrder(
        documents: List<DocumentItem>,
        totalAmount: Double,
        customerPhone: String,
        shopId: String
    ): Result<CreateOrderResponse> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            Log.d("CloudFunctions", "Creating order for user: ${currentUser.uid}")

            val documentsData = ArrayList<HashMap<String, Any>>()

            documents.forEach { doc ->
                // Simplified printSettings - only customBWPages, customColorPages, copies, and printOnBothSides
                val printSettingsMap = hashMapOf<String, Any>(
                    "customBWPages" to doc.printSettings.customBWPages,
                    "customColorPages" to doc.printSettings.customColorPages,
                    "copies" to doc.printSettings.copies,
                    "printOnBothSides" to if (doc.printSettings.printOnBothSides) "true" else "false"
                )

                // Individual document data - fileName, fileType, fileUrl, and printSettings
                val docMap = hashMapOf<String, Any>(
                    "fileName" to doc.fileName,
                    "fileType" to doc.fileType.extension,
                    "fileUrl" to (doc.uri?.toString() ?: ""),
                    "printSettings" to printSettingsMap
                )

                documentsData.add(docMap)
            }

            val data = hashMapOf<String, Any>(
                "customerId" to currentUser.uid,
                "documents" to documentsData,
                "totalAmount" to totalAmount,
                "customerPhone" to customerPhone,
                "shopId" to shopId
            )

            Log.d("CloudFunctions", "Calling createOrder...")

            val result = functions
                .getHttpsCallable("createOrder")
                .call(data)
                .await()

            val responseData = result.getData() as? Map<*, *>

            if (responseData != null && responseData["success"] == true) {
                Log.d("CloudFunctions", "✅ Order created: ${responseData["orderId"]}")
                Result.success(
                    CreateOrderResponse(
                        success = true,
                        orderId = responseData["orderId"] as String,
                        amount = (responseData["amount"] as Number).toDouble()
                    )
                )
            } else {
                Result.failure(Exception("Failed to create order"))
            }
        } catch (e: Exception) {
            Log.e("CloudFunctions", "Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun initiatePayment(orderId: String): Result<InitiatePaymentResponse> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            Log.d("CloudFunctions", "Initiating payment for order: $orderId")

            val data = hashMapOf<String, Any>(
                "customerId" to currentUser.uid,
                "orderId" to orderId
            )

            val result = functions
                .getHttpsCallable("initiatePayment")
                .call(data)
                .await()

            val responseData = result.getData() as? Map<*, *>

            if (responseData != null && responseData["success"] == true) {
                Log.d("CloudFunctions", "✅ Payment initiated")
                Result.success(
                    InitiatePaymentResponse(
                        success = true,
                        razorpayOrderId = responseData["razorpayOrderId"] as String,
                        amount = (responseData["amount"] as Number).toDouble(),
                        amountInPaise = (responseData["amountInPaise"] as Number).toInt(),
                        currency = responseData["currency"] as String,
                        keyId = responseData["keyId"] as String
                    )
                )
            } else {
                Result.failure(Exception("Failed to initiate payment"))
            }
        } catch (e: Exception) {
            Log.e("CloudFunctions", "Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun verifyPayment(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
        orderId: String
    ): Result<VerifyPaymentResponse> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            Log.d("CloudFunctions", "Verifying payment: $razorpayPaymentId")

            val data = hashMapOf<String, Any>(
                "customerId" to currentUser.uid,
                "razorpayOrderId" to razorpayOrderId,
                "razorpayPaymentId" to razorpayPaymentId,
                "razorpaySignature" to razorpaySignature,
                "orderId" to orderId
            )

            val result = functions
                .getHttpsCallable("verifyPayment")
                .call(data)
                .await()

            val responseData = result.getData() as? Map<*, *>

            if (responseData != null) {
                Log.d("CloudFunctions", "✅ Verified: ${responseData["status"]}")
                Result.success(
                    VerifyPaymentResponse(
                        success = responseData["success"] as Boolean,
                        status = responseData["status"] as String,
                        message = responseData["message"] as? String ?: ""
                    )
                )
            } else {
                Result.failure(Exception("No response"))
            }
        } catch (e: Exception) {
            Log.e("CloudFunctions", "Error: ${e.message}", e)
            Result.failure(e)
        }
    }
}