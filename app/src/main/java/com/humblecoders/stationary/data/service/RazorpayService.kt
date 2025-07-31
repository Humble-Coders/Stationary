// Replace the entire RazorpayService.kt

package com.humblecoders.stationary.data.service

import android.app.Activity
import com.razorpay.Checkout
import org.json.JSONObject

class RazorpayService {
    private val razorpayKeyId = "rzp_test_rjEoTLxTkiviFI" // Replace with actual key

    fun initiatePayment(
        activity: Activity,
        amount: Double,
        orderId: String,
        customerPhone: String
    ) {
        val checkout = Checkout()

        // Preload for better performance
        Checkout.preload(activity.applicationContext)

        // Set the key dynamically
        checkout.setKeyID(razorpayKeyId)

        val options = JSONObject().apply {
            put("name", "Print Shop")
            put("description", "Document Printing")
            put("currency", "INR")
            put("amount", (amount * 100).toInt()) // Convert to paise

            // Prefill customer details
            put("prefill", JSONObject().apply {
                put("contact", customerPhone)
            })

            // Enable payment methods
            put("method", JSONObject().apply {
                put("upi", true)
                put("card", true)
                put("netbanking", true)
                put("wallet", true)
            })

            // Theme customization
            put("theme", JSONObject().apply {
                put("color", "#1976D2")
            })

            // Additional notes (optional)
            put("notes", JSONObject().apply {
                put("order_id", orderId)
            })
        }

        try {
            checkout.open(activity, options)
        } catch (e: Exception) {
            throw Exception("Failed to open Razorpay: ${e.message}")
        }
    }
}