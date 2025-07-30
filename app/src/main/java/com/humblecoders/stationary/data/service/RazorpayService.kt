package com.humblecoders.stationary.data.service

import android.app.Activity
import com.humblecoders.stationary.data.model.PaymentResult
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RazorpayService {
    private val razorpayKeyId = "rzp_test_rjEoTLxTkiviFI" // Replace with actual key

    suspend fun initiatePayment(
        activity: Activity,
        amount: Double,
        orderId: String,
        customerPhone: String
    ): PaymentResult = suspendCoroutine { continuation ->

        val checkout = Checkout()
        checkout.setKeyID(razorpayKeyId)

        val options = JSONObject().apply {
            put("name", "Print Shop")
            put("description", "Document Printing")
            put("order_id", orderId)
            put("currency", "INR")
            put("amount", (amount * 100).toInt()) // Convert to paise
            put("prefill", JSONObject().apply {
                put("contact", customerPhone)
            })
            put("method", JSONObject().apply {
                put("upi", true)
                put("card", false)
                put("netbanking", false)
                put("wallet", false)
            })
        }

        val paymentListener = object : PaymentResultWithDataListener {
            override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
                continuation.resume(
                    PaymentResult.Success(
                        paymentId = razorpayPaymentId ?: "",
                        orderId = orderId,
                        amount = amount
                    )
                )
            }

            override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
                continuation.resume(
                    PaymentResult.Error(
                        errorCode = errorCode,
                        errorMessage = response ?: "Payment failed"
                    )
                )
            }
        }

        checkout.open(activity, options)
    }
}