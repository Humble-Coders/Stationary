package com.humblecoders.stationary.ui.viewmodel

// ui/viewmodel/DocumentUploadViewModel.kt

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.humblecoders.stationary.data.model.FileType
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.ShopSettings
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
    val fileType: FileType? = null,
    val pageCount: Int = 0,
    val needsUserPageInput: Boolean = false,
    val userInputPageCount: Int = 0,
    val printSettings: PrintSettings = PrintSettings(),
    val calculatedPrice: Double = 0.0,
    val isUploading: Boolean = false,
    val isShopOpen: Boolean = true,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val orderId: String? = null,
    val customerId: String = "",
    val customerPhone: String = "",
    val showPreview: Boolean = true,
    val currentPreviewPage: Int = 0
)

class DocumentUploadViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUploadUiState())
    val uiState: StateFlow<DocumentUploadUiState> = _uiState.asStateFlow()

    init {
        observeShopStatus()
        initializeUserInfo()
    }

    private fun initializeUserInfo() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("DocumentUploadVM", "Initializing user info: ${currentUser.uid}")
            _uiState.value = _uiState.value.copy(
                customerId = currentUser.uid,
                customerPhone = currentUser.phoneNumber ?: ""
            )
        } else {
            Log.w("DocumentUploadVM", "No current user found during initialization")
        }
    }

    // Also update the setCustomerInfo method
    fun setCustomerInfo(customerId: String, customerPhone: String) {
        Log.d("DocumentUploadVM", "Setting customer info: $customerId, phone: $customerPhone")
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            customerPhone = customerPhone
        )
    }

// Replace the selectFile method in DocumentUploadViewModel.kt

    fun selectFile(context: Context, uri: Uri) {
        if (!FileUtils.isValidFile(context, uri)) {
            _uiState.value = _uiState.value.copy(error = "Please select a valid PDF or Word document under 50MB")
            return
        }

        val fileName = FileUtils.getFileName(context, uri)
        val fileSize = FileUtils.getFileSize(context, uri)

        val fileType = when {
            FileUtils.isPdfFile(context, uri) -> FileType.PDF
            FileUtils.isDocxFile(context, uri) -> FileType.DOCX
            else -> {
                _uiState.value = _uiState.value.copy(error = "Unsupported file format")
                return
            }
        }

        when (fileType) {
            FileType.PDF -> {
                val pageCount = FileUtils.getPdfPageCount(context, uri)

                if (pageCount != null && pageCount > 0) {
                    // Success - use detected page count
                    _uiState.value = _uiState.value.copy(
                        selectedFile = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        fileType = fileType,
                        pageCount = pageCount,
                        needsUserPageInput = false,
                        userInputPageCount = 0,
                        showPreview = true, // Enable preview for PDF
                        error = null
                    )
                } else {
                    // Failed - ask user for page count
                    _uiState.value = _uiState.value.copy(
                        selectedFile = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        fileType = fileType,
                        pageCount = 0,
                        needsUserPageInput = true,
                        userInputPageCount = 0,
                        showPreview = false, // Disable preview if can't read PDF
                        error = null
                    )
                }
            }

            FileType.DOCX -> {
                _uiState.value = _uiState.value.copy(
                    selectedFile = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    fileType = fileType,
                    pageCount = 1, // Default to 1 for DOCX (not used for calculation)
                    needsUserPageInput = false,
                    userInputPageCount = 0,
                    showPreview = false, // No preview for DOCX
                    printSettings = _uiState.value.printSettings.copy(
                        pagesToPrint = PageSelection.ALL // Force ALL pages for DOCX
                    ),
                    error = null
                )
            }
        }

        recalculatePrice()
    }


    fun togglePreview() {
        _uiState.value = _uiState.value.copy(showPreview = !_uiState.value.showPreview)
    }

    fun updatePreviewPage(page: Int) {
        _uiState.value = _uiState.value.copy(currentPreviewPage = page)
    }

    // Replace these methods in DocumentUploadViewModel.kt

    // Replace the submitOrderWithPayment method in DocumentUploadViewModel.kt

    fun submitOrderWithPayment(onOrderCreated: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(error = "Please sign in to continue")
            return
        }

        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.customerId.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Customer information is required")
            return
        }

        val selectedFile = _uiState.value.selectedFile
        if (selectedFile == null) {
            _uiState.value = _uiState.value.copy(error = "Please select a file first")
            return
        }

        // Determine final page count based on file type
        val finalPageCount = when (_uiState.value.fileType) {
            FileType.PDF -> {
                if (_uiState.value.needsUserPageInput) {
                    if (_uiState.value.userInputPageCount <= 0) {
                        _uiState.value = _uiState.value.copy(error = "Please enter the number of pages")
                        return
                    }
                    _uiState.value.userInputPageCount
                } else {
                    if (_uiState.value.pageCount <= 0) {
                        _uiState.value = _uiState.value.copy(error = "Invalid page count")
                        return
                    }
                    _uiState.value.pageCount
                }
            }
            FileType.DOCX -> 1 // DOCX files always use 1 for page count
            null -> {
                _uiState.value = _uiState.value.copy(error = "File type not determined")
                return
            }
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
                    fileType = _uiState.value.fileType?.extension ?: ".pdf",
                    pageCount = finalPageCount,
                    printSettings = _uiState.value.printSettings,
                    hasSettings = true,
                    isPaid = false,
                    canAutoPrint = _uiState.value.fileType == FileType.PDF // Only PDFs can be auto-printed
                )

                val orderId = printOrderRepository.createOrder(order)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    orderId = orderId
                )

                onOrderCreated(orderId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun submitOrderWithoutPayment() {
        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.customerId.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Customer information is required")
            return
        }

        val selectedFile = _uiState.value.selectedFile
        if (selectedFile == null) {
            _uiState.value = _uiState.value.copy(error = "Please select a file first")
            return
        }

        // Determine final page count based on file type
        val finalPageCount = when (_uiState.value.fileType) {
            FileType.PDF -> {
                if (_uiState.value.needsUserPageInput) {
                    if (_uiState.value.userInputPageCount <= 0) {
                        _uiState.value = _uiState.value.copy(error = "Please enter the number of pages")
                        return
                    }
                    _uiState.value.userInputPageCount
                } else {
                    _uiState.value.pageCount
                }
            }
            FileType.DOCX -> 1
            null -> 1
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
                    fileType = _uiState.value.fileType?.extension ?: ".pdf",
                    pageCount = finalPageCount,
                    printSettings = _uiState.value.printSettings,
                    hasSettings = true,
                    isPaid = false,
                    canAutoPrint = _uiState.value.fileType == FileType.PDF
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

    fun clearState() {
        _uiState.value = DocumentUploadUiState().copy(
            isShopOpen = _uiState.value.isShopOpen,
            customerId = _uiState.value.customerId,
            customerPhone = _uiState.value.customerPhone
        )
    }

    private var currentShopSettings: ShopSettings = ShopSettings()

    private fun observeShopStatus() {
        viewModelScope.launch {
            try {
                shopSettingsRepository.observeShopSettings().collect { settings ->
                    currentShopSettings = settings
                    _uiState.value = _uiState.value.copy(isShopOpen = settings.shopOpen)

                    if (_uiState.value.selectedFile != null) {
                        recalculatePrice()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun recalculatePrice() {
        viewModelScope.launch {
            try {
                val price = printOrderRepository.calculatePrice(
                    _uiState.value.printSettings,
                    _uiState.value.pageCount,
                    currentShopSettings,
                    _uiState.value.fileType
                )
                _uiState.value = _uiState.value.copy(calculatedPrice = price)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Price calculation failed: ${e.message}")
            }
        }
    }

    fun updatePrintSettings(settings: PrintSettings) {
        _uiState.value = _uiState.value.copy(printSettings = settings)
        recalculatePrice()
    }

    fun updateUserPageCount(pageCount: Int) {
        _uiState.value = _uiState.value.copy(
            userInputPageCount = pageCount,
            pageCount = pageCount // Update the actual page count used for calculations
        )
        recalculatePrice()
    }
}