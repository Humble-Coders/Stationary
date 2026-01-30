package com.humblecoders.stationary.data.service

import android.app.Activity
import android.util.Log
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class RazorpayService(
    private val activity: Activity
) : PaymentResultWithDataListener {

    private var paymentCallback: PaymentCallback? = null

    interface PaymentCallback {
        fun onPaymentSuccess(razorpayPaymentId: String, razorpaySignature: String)
        fun onPaymentError(errorCode: Int, errorMessage: String)
    }

    init {
        Checkout.preload(activity.applicationContext)
    }

    fun startPayment(
        razorpayOrderId: String,
        amount: Double,
        keyId: String,
        customerName: String = "Customer",
        customerEmail: String = "",
        customerPhone: String = "",
        callback: PaymentCallback
    ) {
        this.paymentCallback = callback

        try {
            val checkout = Checkout()
            checkout.setKeyID(keyId)

            val options = JSONObject()
            options.put("name", "PrintQ")
            options.put("description", "Print Order Payment")
            options.put("image", "")
            options.put("order_id", razorpayOrderId)
            options.put("currency", "INR")
            options.put("amount", (amount * 100).toInt())

            val prefill = JSONObject()
            prefill.put("name", customerName)
            prefill.put("email", customerEmail)
            prefill.put("contact", customerPhone)
            options.put("prefill", prefill)

            val theme = JSONObject()
            theme.put("color", "#3F51B5")
            options.put("theme", theme)

            Log.d("RazorpayService", "Starting payment with options: $options")

            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e("RazorpayService", "Error starting payment", e)
            paymentCallback?.onPaymentError(
                0,
                "Failed to start payment: ${e.message}"
            )
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        Log.d("RazorpayService", "Payment successful: $razorpayPaymentId")

        if (razorpayPaymentId == null || paymentData == null) {
            paymentCallback?.onPaymentError(0, "Payment data is null")
            return
        }

        try {
            val data = paymentData.data as Map<*, *>
            val signature = data["razorpay_signature"]?.toString() ?: ""

            Log.d("RazorpayService", "Payment signature: $signature")

            paymentCallback?.onPaymentSuccess(razorpayPaymentId, signature)
        } catch (e: Exception) {
            Log.e("RazorpayService", "Error parsing payment data", e)
            paymentCallback?.onPaymentError(0, "Error processing payment: ${e.message}")
        }
    }

    override fun onPaymentError(errorCode: Int, errorMessage: String?, paymentData: PaymentData?) {
        Log.e("RazorpayService", "Payment error: $errorCode - $errorMessage")

        paymentData?.let { data ->
            Log.e("RazorpayService", "Error data: ${data.data}")
        }

        paymentCallback?.onPaymentError(
            errorCode,
            errorMessage ?: "Payment failed"
        )
    }
}