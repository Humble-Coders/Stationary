package com.humblecoders.stationary.ui.viewmodel

// ui/viewmodel/DocumentUploadViewModel.kt

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DocumentUploadUiState(
    val selectedFile: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val pageCount: Int = 0,
    val printSettings: PrintSettings = PrintSettings(),
    val calculatedPrice: Double = 0.0,
    val isUploading: Boolean = false,
    val isShopOpen: Boolean = true,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val orderId: String? = null,
    val customerId: String = "",
    val customerPhone: String = ""
)

class DocumentUploadViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUploadUiState())
    val uiState: StateFlow<DocumentUploadUiState> = _uiState.asStateFlow()

    init {
        observeShopStatus()
    }

    fun setCustomerInfo(customerId: String, customerPhone: String) {
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            customerPhone = customerPhone
        )
    }

    private fun observeShopStatus() {
        viewModelScope.launch {
            try {
                shopSettingsRepository.observeShopStatus().collect { isOpen ->
                    _uiState.value = _uiState.value.copy(isShopOpen = isOpen)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun selectFile(context: Context, uri: Uri) {
        if (!FileUtils.isValidPdfFile(context, uri)) {
            _uiState.value = _uiState.value.copy(error = "Please select a valid PDF file under 50MB")
            return
        }

        val fileName = FileUtils.getFileName(context, uri)
        val fileSize = FileUtils.getFileSize(context, uri)
        val pageCount = estimatePageCount(fileSize)
        val price = printOrderRepository.calculatePrice(_uiState.value.printSettings, pageCount)

        _uiState.value = _uiState.value.copy(
            selectedFile = uri,
            fileName = fileName,
            fileSize = fileSize,
            pageCount = pageCount,
            calculatedPrice = price,
            error = null
        )
    }

    fun updatePrintSettings(settings: PrintSettings) {
        val price = printOrderRepository.calculatePrice(settings, _uiState.value.pageCount)
        _uiState.value = _uiState.value.copy(
            printSettings = settings,
            calculatedPrice = price
        )
    }

    fun submitOrderWithoutPayment() {
        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.customerId.isEmpty() || _uiState.value.customerPhone.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Customer information is required")
            return
        }

        val selectedFile = _uiState.value.selectedFile
        if (selectedFile == null) {
            _uiState.value = _uiState.value.copy(error = "Please select a file first")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isUploading = true, error = null)

                val documentUrl = printOrderRepository.uploadDocument(selectedFile)

                val order = PrintOrder(
                    customerId = _uiState.value.customerId,
                    customerPhone = _uiState.value.customerPhone,
                    documentName = _uiState.value.fileName,
                    documentUrl = documentUrl,
                    documentSize = _uiState.value.fileSize,
                    pageCount = _uiState.value.pageCount,
                    printSettings = _uiState.value.printSettings,
                    hasSettings = true,
                    isPaid = false,
                    canAutoPrint = false
                )

                val orderId = printOrderRepository.createOrder(order)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    orderId = orderId
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun submitOrderWithoutSettings() {
        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.customerId.isEmpty() || _uiState.value.customerPhone.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Customer information is required")
            return
        }

        val selectedFile = _uiState.value.selectedFile
        if (selectedFile == null) {
            _uiState.value = _uiState.value.copy(error = "Please select a file first")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isUploading = true, error = null)

                val documentUrl = printOrderRepository.uploadDocument(selectedFile)

                val order = PrintOrder(
                    customerId = _uiState.value.customerId,
                    customerPhone = _uiState.value.customerPhone,
                    documentName = _uiState.value.fileName,
                    documentUrl = documentUrl,
                    documentSize = _uiState.value.fileSize,
                    pageCount = _uiState.value.pageCount,
                    hasSettings = false,
                    isPaid = false,
                    canAutoPrint = false
                )

                val orderId = printOrderRepository.createOrder(order)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    orderId = orderId
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }

    private fun estimatePageCount(fileSize: Long): Int {
        val avgBytesPerPage = 100_000L
        return (fileSize / avgBytesPerPage).toInt().coerceAtLeast(1)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetUpload() {
        _uiState.value = DocumentUploadUiState().copy(
            isShopOpen = _uiState.value.isShopOpen,
            customerId = _uiState.value.customerId,
            customerPhone = _uiState.value.customerPhone
        )
    }
}
