package com.humblecoders.stationary.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.humblecoders.stationary.data.model.DocumentItem
import com.humblecoders.stationary.data.model.FileType
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.ShopSettings
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class DocumentUploadUiState(
    val currentFileType: FileType? = null,
    val documents: List<DocumentItem> = emptyList(),
    val totalCalculatedPrice: Double = 0.0,
    val isUploading: Boolean = false,
    val isShopOpen: Boolean = true,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val orderId: String? = null,
    val customerId: String = "",
    val customerPhone: String = "",
    val canAddMoreFiles: Boolean = true
)

class DocumentUploadViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUploadUiState())
    val uiState: StateFlow<DocumentUploadUiState> = _uiState.asStateFlow()

    private var currentShopSettings: ShopSettings = ShopSettings()

    companion object {
        private const val MAX_DOCUMENTS = 10 // Maximum documents per upload
    }

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
        }
    }

    fun setCustomerInfo(customerId: String, customerPhone: String) {
        Log.d("DocumentUploadVM", "Setting customer info: $customerId, phone: $customerPhone")
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            customerPhone = customerPhone
        )
    }

    fun selectFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(error = null)

                // Determine file type from first file
                val firstUri = uris.first()
                val detectedFileType = when {
                    FileUtils.isPdfFile(context, firstUri) -> FileType.PDF
                    FileUtils.isDocxFile(context, firstUri) -> FileType.DOCX
                    FileUtils.isPptxFile(context, firstUri) -> FileType.PPTX
                    FileUtils.isImageFile(context, firstUri) -> FileType.IMAGE // Add this line
                    else -> {
                        _uiState.value = _uiState.value.copy(error = "Unsupported file format")
                        return@launch
                    }
                }

                // Check if we already have documents and type consistency
                val currentDocuments = _uiState.value.documents
                if (currentDocuments.isNotEmpty() && _uiState.value.currentFileType != detectedFileType) {
                    _uiState.value = _uiState.value.copy(
                        error = "Cannot mix file types. Please upload only ${_uiState.value.currentFileType?.displayName} files."
                    )
                    return@launch
                }

                // Check maximum document limit
                if (currentDocuments.size + uris.size > MAX_DOCUMENTS) {
                    _uiState.value = _uiState.value.copy(
                        error = "Maximum $MAX_DOCUMENTS documents allowed. You can add ${MAX_DOCUMENTS - currentDocuments.size} more."
                    )
                    return@launch
                }

                val invalidFiles = uris.filter { uri ->
                    when (detectedFileType) {
                        FileType.PDF -> !FileUtils.isPdfFile(context, uri)
                        FileType.DOCX -> !FileUtils.isDocxFile(context, uri)
                        FileType.PPTX -> !FileUtils.isPptxFile(context, uri)
                        FileType.IMAGE -> !FileUtils.isImageFile(context, uri) // Add this line
                    }
                }

                if (invalidFiles.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "All files must be ${detectedFileType.displayName} files"
                    )
                    return@launch
                }

                // Process each file
                val newDocuments = mutableListOf<DocumentItem>()

                for (uri in uris) {
                    if (!FileUtils.isValidFile(context, uri)) {
                        _uiState.value = _uiState.value.copy(
                            error = "Invalid file: ${FileUtils.getFileName(context, uri)}"
                        )
                        return@launch
                    }

                    val documentItem = processFile(context, uri, detectedFileType)
                    if (documentItem != null) {
                        newDocuments.add(documentItem)
                    }
                }

                // Update state with new documents
                val updatedDocuments = currentDocuments + newDocuments
                _uiState.value = _uiState.value.copy(
                    currentFileType = detectedFileType,
                    documents = updatedDocuments,
                    canAddMoreFiles = updatedDocuments.size < MAX_DOCUMENTS
                )

                recalculateTotalPrice()

            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Error selecting files", e)
                _uiState.value = _uiState.value.copy(error = "Error processing files: ${e.message}")
            }
        }
    }

    private suspend fun processFile(context: Context, uri: Uri, fileType: FileType): DocumentItem? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = FileUtils.getFileName(context, uri)
                val fileSize = FileUtils.getFileSize(context, uri)
                val documentId = UUID.randomUUID().toString()

                when (fileType) {
                    FileType.PDF -> {
                        val pageCount = FileUtils.getPdfPageCount(context, uri)
                        val previewBitmap = generatePdfPreview(context, uri)

                        DocumentItem(
                            id = documentId,
                            uri = uri,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileType = fileType,
                            pageCount = pageCount ?: 0,
                            needsUserPageInput = pageCount == null,
                            userInputPageCount = 0,
                            printSettings = PrintSettings(),
                            previewBitmap = previewBitmap
                        )
                    }

                    FileType.DOCX -> {
                        DocumentItem(
                            id = documentId,
                            uri = uri,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileType = fileType,
                            pageCount = 1,
                            needsUserPageInput = false,
                            userInputPageCount = 0,
                            printSettings = PrintSettings(pagesToPrint = PageSelection.ALL)
                        )
                    }
                    FileType.PPTX -> { // Add this entire case
                        DocumentItem(
                            id = documentId,
                            uri = uri,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileType = fileType,
                            pageCount = 1,
                            needsUserPageInput = false,
                            userInputPageCount = 0,
                            printSettings = PrintSettings(pagesToPrint = PageSelection.ALL)
                        )
                    }
                    FileType.IMAGE -> { // Add this entire case
                        val previewBitmap = generateImagePreview(context, uri)
                        DocumentItem(
                            id = documentId,
                            uri = uri,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileType = fileType,
                            pageCount = 1,
                            needsUserPageInput = false,
                            userInputPageCount = 0,
                            printSettings = PrintSettings(
                                pagesToPrint = PageSelection.ALL,
                                copies = 1 // Default to 1 copy for images
                            ),
                            previewBitmap = previewBitmap
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Error processing file", e)
                null
            }
        }
    }

    private fun generateImagePreview(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                if (originalBitmap != null) {
                    val width = 200
                    val height = (width * originalBitmap.height / originalBitmap.width.toFloat()).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, width, height, true)
                } else null
            }
        } catch (e: Exception) {
            Log.w("DocumentUploadVM", "Cannot generate image preview: ${e.message}")
            null
        }
    }

    private fun generatePdfPreview(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val width = 200
                            val height = (width * page.height / page.width.toFloat()).toInt()
                            val bitmap = createBitmap(width, height)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.w("DocumentUploadVM", "Cannot generate PDF preview: ${e.message}")
            null
        }
    }

    fun removeDocument(documentId: String) {
        val updatedDocuments = _uiState.value.documents.filter { it.id != documentId }

        _uiState.value = _uiState.value.copy(
            documents = updatedDocuments,
            currentFileType = if (updatedDocuments.isEmpty()) null else _uiState.value.currentFileType,
            canAddMoreFiles = updatedDocuments.size < MAX_DOCUMENTS
        )

        recalculateTotalPrice()
    }

    fun updateDocumentSettings(documentId: String, settings: PrintSettings) {
        val updatedDocuments = _uiState.value.documents.map { doc ->
            if (doc.id == documentId) {
                doc.copy(printSettings = settings)
            } else doc
        }

        _uiState.value = _uiState.value.copy(documents = updatedDocuments)
        recalculateDocumentPrice(documentId)
    }

    fun updateDocumentPageCount(documentId: String, pageCount: Int) {
        val updatedDocuments = _uiState.value.documents.map { doc ->
            if (doc.id == documentId) {
                doc.copy(
                    userInputPageCount = pageCount,
                    pageCount = pageCount
                )
            } else doc
        }

        _uiState.value = _uiState.value.copy(documents = updatedDocuments)
        recalculateDocumentPrice(documentId)
    }

    fun toggleDocumentExpansion(documentId: String) {
        val updatedDocuments = _uiState.value.documents.map { doc ->
            if (doc.id == documentId) {
                doc.copy(isExpanded = !doc.isExpanded)
            } else doc
        }

        _uiState.value = _uiState.value.copy(documents = updatedDocuments)
    }

    private fun recalculateDocumentPrice(documentId: String) {
        val updatedDocuments = _uiState.value.documents.map { doc ->
            if (doc.id == documentId) {
                val price = printOrderRepository.calculatePrice(
                    doc.printSettings,
                    doc.getEffectivePageCount(),
                    currentShopSettings,
                    doc.fileType
                )
                doc.copy(calculatedPrice = price)
            } else doc
        }

        _uiState.value = _uiState.value.copy(documents = updatedDocuments)
        recalculateTotalPrice()
    }

    private fun recalculateTotalPrice() {
        val totalPrice = _uiState.value.documents.sumOf { doc ->
            printOrderRepository.calculatePrice(
                doc.printSettings,
                doc.getEffectivePageCount(),
                currentShopSettings,
                doc.fileType
            )
        }

        // Update all document prices
        val updatedDocuments = _uiState.value.documents.map { doc ->
            val price = printOrderRepository.calculatePrice(
                doc.printSettings,
                doc.getEffectivePageCount(),
                currentShopSettings,
                doc.fileType
            )
            doc.copy(calculatedPrice = price)
        }

        _uiState.value = _uiState.value.copy(
            documents = updatedDocuments,
            totalCalculatedPrice = totalPrice
        )
    }

    fun submitOrderWithPayment(onOrderCreated: (String) -> Unit) {
        submitOrder(withPayment = true, onOrderCreated)
    }

    fun submitOrderWithoutPayment() {
        submitOrder(withPayment = false) { }
    }

// In DocumentUploadViewModel.kt - Replace the submitOrder method

    // In DocumentUploadViewModel.kt - Replace the submitOrder method completely

    private fun submitOrder(withPayment: Boolean, onOrderCreated: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(error = "Please sign in to continue")
            return
        }

        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.documents.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please select at least one document")
            return
        }

        // Validate all documents have valid page counts
        val invalidDocuments = _uiState.value.documents.filter { doc ->
            doc.needsUserPageInput && doc.userInputPageCount <= 0
        }

        if (invalidDocuments.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Please enter page count for: ${invalidDocuments.joinToString(", ") { it.fileName }}"
            )
            return
        }

        // Validate page ranges for PDF documents
        val pageValidationErrors = mutableListOf<String>()
        _uiState.value.documents.forEach { document ->
            if (document.fileType == FileType.PDF) {
                val maxPages = document.getEffectivePageCount()

                // Validate B&W pages
                if (document.printSettings.customBWPages.isNotEmpty()) {
                    if (!isValidPageRangeForDocument(document.printSettings.customBWPages, maxPages)) {
                        pageValidationErrors.add("${document.fileName}: Invalid B&W page range (max: $maxPages)")
                    }
                }

                // Validate Color pages
                if (document.printSettings.customColorPages.isNotEmpty()) {
                    if (!isValidPageRangeForDocument(document.printSettings.customColorPages, maxPages)) {
                        pageValidationErrors.add("${document.fileName}: Invalid Color page range (max: $maxPages)")
                    }
                }

                // Check for overlap
                if (document.printSettings.customBWPages.isNotEmpty() &&
                    document.printSettings.customColorPages.isNotEmpty()) {
                    val overlapError = checkPageOverlapForDocument(
                        document.printSettings.customBWPages,
                        document.printSettings.customColorPages

                    )
                    if (overlapError != null) {
                        pageValidationErrors.add("${document.fileName}: $overlapError")
                    }
                }
            }
        }

        if (pageValidationErrors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Page validation errors:\n${pageValidationErrors.joinToString("\n")}"
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isUploading = true, error = null)

                // Upload all documents
                val documentUrls = mutableListOf<String>()
                val documentNames = mutableListOf<String>()
                val printSettingsArray = mutableListOf<Map<String, Any>>()
                val individualDocumentsArray = mutableListOf<Map<String, Any>>()
                val documents = _uiState.value.documents

                for ((index, document) in documents.withIndex()) {
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = (index.toFloat() / documents.size)
                    )

                    val documentUrl = printOrderRepository.uploadDocument(document.uri!!)
                    documentUrls.add(documentUrl)
                    documentNames.add(document.fileName)

                    // Convert PrintSettings to Map
                    val settingsMap = mapOf(
                        "colorMode" to document.printSettings.colorMode.name,
                        "pagesToPrint" to document.printSettings.pagesToPrint.name,
                        "customPages" to document.printSettings.customPages,
                        "customBWPages" to document.printSettings.customBWPages,
                        "customColorPages" to document.printSettings.customColorPages,
                        "copies" to document.printSettings.copies,
                        "paperSize" to document.printSettings.paperSize.name,
                        "orientation" to document.printSettings.orientation.name,
                        "quality" to document.printSettings.quality.name
                    )
                    printSettingsArray.add(settingsMap)

                    // Individual document data
                    val docData = mapOf(
                        "fileName" to document.fileName,
                        "fileSize" to document.fileSize,
                        "fileType" to document.fileType.extension,
                        "pageCount" to document.getEffectivePageCount(),
                        "printSettings" to settingsMap,
                        "calculatedPrice" to document.calculatedPrice
                    )
                    individualDocumentsArray.add(docData)
                }

                val totalSize = documents.sumOf { it.fileSize }
                val totalPages = documents.sumOf { it.getEffectivePageCount() }

                val order = PrintOrder(
                    customerId = _uiState.value.customerId,
                    customerPhone = _uiState.value.customerPhone,
                    documentName = documentNames, // Array of names
                    documentUrl = documentUrls, // Array of URLs
                    documentSize = totalSize,
                    fileType = _uiState.value.currentFileType?.extension ?: ".pdf",
                    pageCount = totalPages,
                    printSettings = printSettingsArray, // Array of settings maps
                    individualDocuments = individualDocumentsArray, // Array of document maps
                    documentCount = documents.size, // Document count
                    hasSettings = true,
                    isPaid = false,
                    canAutoPrint = _uiState.value.currentFileType == FileType.PDF
                )

                val orderId = printOrderRepository.createOrder(order)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    orderId = orderId,
                    uploadProgress = 1f
                )

                if (withPayment) {
                    onOrderCreated(orderId)
                }

            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Upload failed", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}",
                    uploadProgress = 0f
                )
            }
        }
    }

    fun clearState() {
        _uiState.value = DocumentUploadUiState().copy(
            isShopOpen = _uiState.value.isShopOpen,
            customerId = _uiState.value.customerId,
            customerPhone = _uiState.value.customerPhone
        )
    }
// In DocumentUploadViewModel.kt - Add this new method

    fun submitOrderDirectly(onOrderCreated: () -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(error = "Please sign in to continue")
            return
        }

        if (!_uiState.value.isShopOpen) {
            _uiState.value = _uiState.value.copy(error = "Shop is currently closed")
            return
        }

        if (_uiState.value.documents.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please select at least one document")
            return
        }

        // For non-PDF files, skip payment validation and directly upload
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isUploading = true, error = null)

                // Upload all documents
                val documentUrls = mutableListOf<String>()
                val documentNames = mutableListOf<String>()
                val printSettingsArray = mutableListOf<Map<String, Any>>()
                val individualDocumentsArray = mutableListOf<Map<String, Any>>()
                val documents = _uiState.value.documents

                for ((index, document) in documents.withIndex()) {
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = (index.toFloat() / documents.size)
                    )

                    val documentUrl = printOrderRepository.uploadDocument(document.uri!!)
                    documentUrls.add(documentUrl)
                    documentNames.add(document.fileName)

                    // Convert PrintSettings to Map
                    val settingsMap = mapOf(
                        "colorMode" to document.printSettings.colorMode.name,
                        "pagesToPrint" to document.printSettings.pagesToPrint.name,
                        "customPages" to document.printSettings.customPages,
                        "customBWPages" to document.printSettings.customBWPages,
                        "customColorPages" to document.printSettings.customColorPages,
                        "copies" to document.printSettings.copies,
                        "paperSize" to document.printSettings.paperSize.name,
                        "orientation" to document.printSettings.orientation.name,
                        "quality" to document.printSettings.quality.name
                    )
                    printSettingsArray.add(settingsMap)

                    // Individual document data
                    val docData = mapOf(
                        "fileName" to document.fileName,
                        "fileSize" to document.fileSize,
                        "fileType" to document.fileType.extension,
                        "pageCount" to document.getEffectivePageCount(),
                        "printSettings" to settingsMap,
                        "calculatedPrice" to document.calculatedPrice
                    )
                    individualDocumentsArray.add(docData)
                }

                val totalSize = documents.sumOf { it.fileSize }
                val totalPages = documents.sumOf { it.getEffectivePageCount() }

                val order = PrintOrder(
                    customerId = _uiState.value.customerId,
                    customerPhone = _uiState.value.customerPhone,
                    documentName = documentNames,
                    documentUrl = documentUrls,
                    documentSize = totalSize,
                    fileType = _uiState.value.currentFileType?.extension ?: ".jpg",
                    pageCount = totalPages,
                    printSettings = printSettingsArray,
                    individualDocuments = individualDocumentsArray,
                    documentCount = documents.size,
                    hasSettings = true,
                    isPaid = true, // Mark as paid for non-PDF files
                    canAutoPrint = true, // Allow auto-print for non-PDF files
                    paymentStatus = com.humblecoders.stationary.data.model.PaymentStatus.PAID // Set as paid
                )

                val orderId = printOrderRepository.createOrder(order)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    orderId = orderId,
                    uploadProgress = 1f
                )

                // Clear state and call success callback
                clearState()
                onOrderCreated()

            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Upload failed", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${e.message}",
                    uploadProgress = 0f
                )
            }
        }
    }

    private fun observeShopStatus() {
        viewModelScope.launch {
            try {
                shopSettingsRepository.observeShopSettings().collect { settings ->
                    currentShopSettings = settings
                    _uiState.value = _uiState.value.copy(isShopOpen = settings.shopOpen)

                    if (_uiState.value.documents.isNotEmpty()) {
                        recalculateTotalPrice()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

// In DocumentUploadViewModel.kt - Add these helper functions at the end of the class

private fun isValidPageRangeForDocument(pageRange: String, maxPages: Int): Boolean {
    if (pageRange.isEmpty()) return true

    try {
        val parts = pageRange.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size != 2) return false
                val start = range[0].trim().toInt()
                val end = range[1].trim().toInt()
                if (start <= 0 || end <= 0 || start > end || start > maxPages || end > maxPages) return false
            } else {
                val page = trimmed.toInt()
                if (page <= 0 || page > maxPages) return false
            }
        }
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun checkPageOverlapForDocument(bwPages: String, colorPages: String): String? {
    if (bwPages.isEmpty() || colorPages.isEmpty()) return null

    try {
        val bwPagesList = parsePageRangeToListForDocument(bwPages)
        val colorPagesList = parsePageRangeToListForDocument(colorPages)

        val overlapping = bwPagesList.intersect(colorPagesList.toSet())

        return if (overlapping.isNotEmpty()) {
            "Pages ${overlapping.sorted().joinToString(", ")} specified in both B&W and Color"
        } else null
    } catch (e: Exception) {
        return "Invalid page format"
    }
}

private fun parsePageRangeToListForDocument(pageRange: String): List<Int> {
    if (pageRange.isEmpty()) return emptyList()

    val pages = mutableSetOf<Int>()
    val parts = pageRange.split(",")

    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toInt()
                val end = range[1].trim().toInt()
                for (i in start..end) {
                    pages.add(i)
                }
            }
        } else {
            pages.add(trimmed.toInt())
        }
    }

    return pages.toList()
}

