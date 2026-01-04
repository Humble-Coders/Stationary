package com.humblecoders.stationary.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.repository.CloudFunctionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentUiState(
    val isProcessing: Boolean = false,
    val razorpayOrderId: String? = null,
    val razorpayKeyId: String? = null,
    val amount: Double = 0.0,
    val amountInPaise: Int = 0,
    val error: String? = null,
    val paymentSuccess: Boolean = false,
    val paymentVerified: Boolean = false
)

class PaymentViewModel : ViewModel() {
    private val cloudFunctionsRepository = CloudFunctionsRepository()

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var currentOrderId: String? = null

    fun initiatePayment(orderId: String) {
        currentOrderId = orderId

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    error = null
                )

                Log.d("PaymentViewModel", "Initiating payment for order: $orderId")

                val result = cloudFunctionsRepository.initiatePayment(orderId)

                result.fold(
                    onSuccess = { response ->
                        Log.d("PaymentViewModel", "Payment initiated: ${response.razorpayOrderId}")
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            razorpayOrderId = response.razorpayOrderId,
                            razorpayKeyId = response.keyId,
                            amount = response.amount,
                            amountInPaise = response.amountInPaise
                        )
                    },
                    onFailure = { exception ->
                        Log.e("PaymentViewModel", "Failed to initiate payment", exception)
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = "Failed to initiate payment: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("PaymentViewModel", "Error initiating payment", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun verifyPayment(
        razorpayPaymentId: String,
        razorpaySignature: String
    ) {
        val orderId = currentOrderId ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Order ID not found"
            )
            return
        }

        val razorpayOrderId = _uiState.value.razorpayOrderId ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Razorpay Order ID not found"
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    error = null
                )

                Log.d("PaymentViewModel", "Verifying payment: $razorpayPaymentId")

                // Note: Razorpay signature needs to be calculated from order_id|payment_id
                // For now, we'll fetch it from Razorpay API on backend
                val calculatedSignature = razorpaySignature.ifEmpty {
                    // Backend will fetch and verify
                    ""
                }

                val result = cloudFunctionsRepository.verifyPayment(
                    razorpayOrderId = razorpayOrderId,
                    razorpayPaymentId = razorpayPaymentId,
                    razorpaySignature = calculatedSignature,
                    orderId = orderId
                )

                result.fold(
                    onSuccess = { response ->
                        Log.d("PaymentViewModel", "Payment verified: ${response.status}")
                        if (response.success && response.status == "PAID") {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                paymentSuccess = true,
                                paymentVerified = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                error = response.message
                            )
                        }
                    },
                    onFailure = { exception ->
                        Log.e("PaymentViewModel", "Payment verification failed", exception)
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = "Verification failed: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("PaymentViewModel", "Error verifying payment", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun handlePaymentError(errorCode: Int, errorMessage: String) {
        Log.e("PaymentViewModel", "Payment error: $errorCode - $errorMessage")
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            error = "Payment failed: $errorMessage"
        )
    }

    fun resetPaymentState() {
        _uiState.value = PaymentUiState()
        currentOrderId = null
    }
}