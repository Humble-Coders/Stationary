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
import com.humblecoders.stationary.data.model.PaymentStatus
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.ShopSettings
import com.humblecoders.stationary.data.model.PricePerPage
import com.humblecoders.stationary.data.repository.CloudFunctionsRepository
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class DocumentUploadUiState(
    val currentFileType: FileType? = null,
    val documents: List<DocumentItem> = emptyList(),
    val totalCalculatedPrice: Double = 0.0,
    val isUploading: Boolean = false,
    val isLoadingFiles: Boolean = false, // Loading state for when files are being processed
    val isShopOpen: Boolean = true,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val orderId: String? = null,
    val customerId: String = "",
    val customerPhone: String = "",
    val shopId: String = "",
    val canAddMoreFiles: Boolean = true,
    val pricePerPage: PricePerPage = PricePerPage(), // Add prices from Firestore
    val filesWithMissingSettings: List<Pair<Int, String>> = emptyList(), // File number and name pairs
    val showNonPdfSuccessDialog: Boolean = false, // Show success dialog for non-PDF uploads
    val fileTypeMismatchError: String? = null // Error message when trying to mix file types
)

class DocumentUploadViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUploadUiState())
    val uiState: StateFlow<DocumentUploadUiState> = _uiState.asStateFlow()

    private var currentShopSettings: ShopSettings = ShopSettings()

    companion object {
        private const val MAX_DOCUMENTS = 10 // Maximum documents per upload for non-image files
        private const val MAX_IMAGES = 50 // Maximum images per upload
        
        // Static storage to survive ViewModel recreation when Activity is recreated
        private var persistedDocuments: List<DocumentItem> = emptyList()
        private var persistedFileType: FileType? = null
        private var persistedShopId: String = ""
        
        // Helper function to get max limit based on file type
        private fun getMaxDocuments(fileType: FileType?): Int {
            return if (fileType == FileType.IMAGE) MAX_IMAGES else MAX_DOCUMENTS
        }

        private fun countByType(documents: List<DocumentItem>): Pair<Int, Int> {
            val images = documents.count { it.fileType == FileType.IMAGE }
            val others = documents.size - images
            return Pair(images, others)
        }

        /** Sort order for mixed types: PDFs first, then images, then other documents. */
        private fun sortDocumentsByType(documents: List<DocumentItem>): List<DocumentItem> {
            return documents.sortedBy {
                when (it.fileType) {
                    FileType.PDF -> 0
                    FileType.IMAGE -> 1
                    else -> 2
                }
            }
        }

        fun clearPersistedData() {
            persistedDocuments = emptyList()
            persistedFileType = null
            persistedShopId = ""
        }
    }

    // Add auth state listener
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null && user.uid != _uiState.value.customerId) {
            Log.d("DocumentUploadVM", "Auth state changed, new user: ${user.uid}")
            initializeUserInfo(user.uid, user.phoneNumber ?: "")
        } else if (user == null) {
            Log.d("DocumentUploadVM", "User signed out, clearing all user data")
            clearAllUserData()
        }
    }

    init {
        // Shop status will be observed when shopId is set via setShopId()
        
        // Add auth state listener
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        
        // Restore persisted documents if any (survives Activity/ViewModel recreation)
        if (persistedDocuments.isNotEmpty()) {
            Log.d("DocumentUploadVM", "Restoring ${persistedDocuments.size} persisted documents")
            val sorted = sortDocumentsByType(persistedDocuments)
            _uiState.value = _uiState.value.copy(
                documents = sorted,
                currentFileType = persistedFileType,
                shopId = persistedShopId
            )
        }
        
        // Check if user is already signed in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("DocumentUploadVM", "User already signed in: ${currentUser.uid}")
            initializeUserInfo(currentUser.uid, currentUser.phoneNumber ?: "")
        }
    }

    private fun initializeUserInfo(customerId: String, customerPhone: String) {
        Log.d("DocumentUploadVM", "Initializing user info: $customerId, phone: $customerPhone")
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            customerPhone = customerPhone
        )
    }

    fun setCustomerInfo(customerId: String, customerPhone: String) {
        Log.d("DocumentUploadVM", "Setting customer info: $customerId, phone: $customerPhone")
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            customerPhone = customerPhone
        )
    }

    fun setShopId(shopId: String) {
        Log.d("DocumentUploadVM", "Setting shop ID: $shopId")
        _uiState.value = _uiState.value.copy(shopId = shopId)
        persistedShopId = shopId
        // Re-observe shop settings with the new shopId
        observeShopStatus()
    }

    /**
     * Check if there are existing documents with a shop ID already set.
     * Used to determine if new shared files should go to the same shop
     * or if shop selection is needed.
     */
    fun getExistingShopId(): String? {
        val hasDocuments = _uiState.value.documents.isNotEmpty() || persistedDocuments.isNotEmpty()
        val shopId = _uiState.value.shopId.takeIf { it.isNotEmpty() } 
            ?: persistedShopId.takeIf { it.isNotEmpty() }
        
        return if (hasDocuments && shopId != null) {
            Log.d("DocumentUploadVM", "Existing shop ID found: $shopId with ${_uiState.value.documents.size} documents")
            shopId
        } else {
            Log.d("DocumentUploadVM", "No existing shop ID (hasDocuments=$hasDocuments, shopId=$shopId)")
            null
        }
    }

    /**
     * Copy a file from content:// URI to the app's cache directory.
     * This ensures permanent access to the file even after URI permissions expire.
     * Returns the cached file:// URI, or null if copy fails.
     */
    private suspend fun copyToCache(context: Context, uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // Get the original filename
            val fileName = FileUtils.getFileName(context, uri)
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Use a unique filename to avoid conflicts
            val uniqueFileName = "${System.currentTimeMillis()}_$fileName"
            val cacheFile = File(cacheDir, uniqueFileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("DocumentUploadVM", "Copied file to cache: ${cacheFile.absolutePath}")
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e("DocumentUploadVM", "Failed to copy file to cache: ${e.message}", e)
            null
        }
    }

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

        if (_uiState.value.documents.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please select at least one document")
            return
        }

        // Validate all documents have page count if needed
        val invalidDocuments = _uiState.value.documents.filter { doc ->
            doc.needsUserPageInput && doc.userInputPageCount <= 0
        }

        if (invalidDocuments.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Please enter page count for: ${invalidDocuments.joinToString(", ") { it.fileName }}"
            )
            return
        }

        // Validate that each PDF document has at least one page selected in either B&W or Color
        val pdfDocumentsWithoutPages = _uiState.value.documents.mapIndexedNotNull { index, doc ->
            if (doc.fileType != FileType.PDF) return@mapIndexedNotNull null
            val bwPages = doc.printSettings.customBWPages.trim()
            val colorPages = doc.printSettings.customColorPages.trim()
            if (bwPages.isEmpty() && colorPages.isEmpty()) Pair(index + 1, doc.fileName) else null
        }
        if (pdfDocumentsWithoutPages.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(filesWithMissingSettings = pdfDocumentsWithoutPages)
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isUploading = true, error = null)
                val documents = _uiState.value.documents
                val total = documents.size
                val completed = AtomicInteger(0)

                val uploadedDocuments = withContext(Dispatchers.IO) {
                    coroutineScope {
                        documents.mapIndexed { index, document ->
                            async {
                                val uri = document.uri ?: run {
                                    Log.e("DocumentUploadVM", "Document URI is null for ${document.fileName}")
                                    throw Exception("Document URI is null - please re-select the file")
                                }
                                val documentUrl = printOrderRepository.uploadDocument(
                                    uri,
                                    document.fileType,
                                    document.fileName
                                )
                                completed.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        uploadProgress = completed.get().toFloat() / total
                                    )
                                }
                                index to document.copy(uri = Uri.parse(documentUrl))
                            }
                        }.awaitAll().sortedBy { it.first }.map { it.second }
                    }
                }

                // Create order via Cloud Function
                val cloudFunctionsRepo = CloudFunctionsRepository()
                val createOrderResult = cloudFunctionsRepo.createOrder(
                    documents = uploadedDocuments,
                    totalAmount = _uiState.value.totalCalculatedPrice,
                    customerPhone = _uiState.value.customerPhone,
                    shopId = _uiState.value.shopId
                )

                createOrderResult.fold(
                    onSuccess = { response ->
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            orderId = response.orderId,
                            uploadProgress = 1f,
                            showNonPdfSuccessDialog = true  // Show success dialog instead of payment
                        )
                        // Payment disabled - onOrderCreated(response.orderId)
                    },
                    onFailure = { e ->
                        Log.e("DocumentUploadVM", "Cloud function error: ${e.message}", e)
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            error = "An error occurred",
                            uploadProgress = 0f
                        )
                    }
                )
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

    fun selectFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            try {
                Log.d("DocumentUploadVM", "=== selectFiles called (mixed types supported) ===")
                Log.d("DocumentUploadVM", "Incoming URIs: ${uris.size}")
                val currentDocuments = _uiState.value.documents
                Log.d("DocumentUploadVM", "Current documents: ${currentDocuments.size}")

                val existingUris = currentDocuments.mapNotNull { it.uri }.toSet()
                val newUris = uris.filter { it !in existingUris }

                if (newUris.isEmpty()) {
                    Log.d("DocumentUploadVM", "No new files to add (all duplicates)")
                    _uiState.value = _uiState.value.copy(isLoadingFiles = false)
                    return@launch
                }

                _uiState.value = _uiState.value.copy(error = null, fileTypeMismatchError = null, isLoadingFiles = true)

                var (currentImageCount, currentDocCount) = countByType(currentDocuments)

                val newDocuments = mutableListOf<DocumentItem>()
                val skippedUnsupported = mutableListOf<String>()
                val skippedLimit = mutableListOf<String>()

                for (uri in newUris) {
                    val safeUri = if (uri.scheme == "content") {
                        copyToCache(context, uri) ?: uri
                    } else {
                        uri
                    }

                    val (fileType, nameToUse) = FileUtils.detectFileTypeAndFileName(context, safeUri)
                    if (fileType == null) {
                        skippedUnsupported.add(FileUtils.getFileName(context, safeUri))
                        continue
                    }
                    val fileSize = FileUtils.getFileSize(context, safeUri)
                    if (fileSize <= 0 || fileSize >= 50 * 1024 * 1024) {
                        skippedUnsupported.add(nameToUse)
                        continue
                    }

                    if (fileType == FileType.IMAGE) {
                        if (currentImageCount >= MAX_IMAGES) {
                            skippedLimit.add(nameToUse + " (max $MAX_IMAGES images)")
                            continue
                        }
                        currentImageCount++
                    } else {
                        if (currentDocCount >= MAX_DOCUMENTS) {
                            skippedLimit.add(nameToUse + " (max $MAX_DOCUMENTS documents)")
                            continue
                        }
                        currentDocCount++
                    }

                    val documentItem = processFile(context, safeUri, fileType, nameToUse)
                    if (documentItem != null) {
                        newDocuments.add(documentItem)
                    }
                }

                if (newDocuments.isEmpty() && (skippedUnsupported.isNotEmpty() || skippedLimit.isNotEmpty())) {
                    val reasons = buildList {
                        if (skippedUnsupported.isNotEmpty()) add("Unsupported or invalid: ${skippedUnsupported.take(3).joinToString()}")
                        if (skippedLimit.isNotEmpty()) add("Limit reached: ${skippedLimit.take(2).joinToString()}")
                    }
                    _uiState.value = _uiState.value.copy(
                        error = reasons.joinToString(". "),
                        isLoadingFiles = false
                    )
                    return@launch
                }

                if (skippedUnsupported.isNotEmpty() || skippedLimit.isNotEmpty()) {
                    val msg = buildList {
                        if (skippedUnsupported.isNotEmpty()) add("Skipped ${skippedUnsupported.size} unsupported file(s)")
                        if (skippedLimit.isNotEmpty()) add("Skipped ${skippedLimit.size} file(s) (limit reached)")
                    }.joinToString(". ")
                    Log.d("DocumentUploadVM", msg)
                    _uiState.value = _uiState.value.copy(error = msg)
                }

                val updatedDocuments = sortDocumentsByType(currentDocuments + newDocuments)
                val types = updatedDocuments.map { it.fileType }.toSet()
                val currentFileType = if (types.size == 1) types.single() else null
                val (imgCount, docCount) = countByType(updatedDocuments)
                val canAddMore = imgCount < MAX_IMAGES || docCount < MAX_DOCUMENTS

                Log.d("DocumentUploadVM", "Updated: ${updatedDocuments.size} total (${imgCount} images, $docCount docs), mixed=${types.size > 1}")

                _uiState.value = _uiState.value.copy(
                    currentFileType = currentFileType,
                    documents = updatedDocuments,
                    canAddMoreFiles = canAddMore,
                    isLoadingFiles = false
                )

                persistedDocuments = updatedDocuments
                persistedFileType = currentFileType
                persistedShopId = _uiState.value.shopId

                recalculateTotalPrice()

            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Error selecting files", e)
                _uiState.value = _uiState.value.copy(error = "Error processing files: ${e.message}", isLoadingFiles = false)
            }
        }
    }

    private suspend fun processFile(context: Context, uri: Uri, fileType: FileType, displayNameOverride: String? = null): DocumentItem? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = displayNameOverride ?: FileUtils.getFileName(context, uri)
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
                    FileType.IMAGE -> {
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
                    FileType.DOC -> {
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
                    FileType.PPT -> {
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
                    FileType.XLSX -> {
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
                    FileType.XLS -> {
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
                    FileType.TXT -> {
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
                    FileType.RTF -> {
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
        val updatedDocuments = sortDocumentsByType(_uiState.value.documents.filter { it.id != documentId })
        val types = updatedDocuments.map { it.fileType }.toSet()
        val currentFileType = if (types.size == 1) types.single() else null
        val (imgCount, docCount) = countByType(updatedDocuments)
        val canAddMore = imgCount < MAX_IMAGES || docCount < MAX_DOCUMENTS

        _uiState.value = _uiState.value.copy(
            documents = updatedDocuments,
            currentFileType = currentFileType,
            canAddMoreFiles = canAddMore
        )

        persistedDocuments = updatedDocuments
        persistedFileType = currentFileType
        if (updatedDocuments.isEmpty()) {
            persistedFileType = null
        }

        recalculateTotalPrice()
    }

    fun updateDocumentSettings(documentId: String, settings: PrintSettings) {
        val document = _uiState.value.documents.find { it.id == documentId }
        if (document == null) return

        // Don't sanitize input - just store as-is
        // Validation errors will be shown in UI and checked on submit
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
                    currentShopSettings
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
                currentShopSettings
            )
        }

        // Update all document prices
        val updatedDocuments = _uiState.value.documents.map { doc ->
            val price = printOrderRepository.calculatePrice(
                doc.printSettings,
                currentShopSettings
            )
            doc.copy(calculatedPrice = price)
        }

        _uiState.value = _uiState.value.copy(
            documents = updatedDocuments,
            totalCalculatedPrice = totalPrice
        )
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

        // Validate that each PDF document has at least one page selected in either B&W or Color
        val pdfDocsWithoutPages = _uiState.value.documents.mapIndexedNotNull { index, doc ->
            if (doc.fileType != FileType.PDF) return@mapIndexedNotNull null
            val bwPages = doc.printSettings.customBWPages.trim()
            val colorPages = doc.printSettings.customColorPages.trim()
            if (bwPages.isEmpty() && colorPages.isEmpty()) Pair(index + 1, doc.fileName) else null
        }
        if (pdfDocsWithoutPages.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(filesWithMissingSettings = pdfDocsWithoutPages)
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
                val documents = _uiState.value.documents
                val total = documents.size
                val completed = AtomicInteger(0)

                withContext(Dispatchers.IO) {
                    coroutineScope {
                        documents.map { document ->
                            async {
                                val uri = document.uri ?: run {
                                    Log.e("DocumentUploadVM", "Document URI is null for ${document.fileName}")
                                    throw Exception("Document URI is null - please re-select the file")
                                }
                                printOrderRepository.uploadDocument(uri, document.fileType, document.fileName)
                                completed.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        uploadProgress = completed.get().toFloat() / total
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }

                val individualDocumentsArray = documents.map { document ->
                    mapOf(
                        "fileName" to document.fileName,
                        "fileType" to document.fileType.mimeType,
                        "printSettings" to mapOf(
                            "customBWPages" to document.printSettings.customBWPages,
                            "customColorPages" to document.printSettings.customColorPages,
                            "copies" to document.printSettings.copies
                        )
                    )
                }

                val order = PrintOrder(
                    customerId = _uiState.value.customerId,
                    customerPhone = _uiState.value.customerPhone,
                    shopId = _uiState.value.shopId,
                    fileType = _uiState.value.currentFileType?.mimeType ?: documents.firstOrNull()?.fileType?.mimeType ?: "application/pdf",
                    individualDocuments = individualDocumentsArray,
                    documentCount = documents.size,
                    paymentStatus = PaymentStatus.UNPAID
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
        Log.d("DocumentUploadVM", "Clearing all state")
        _uiState.value = DocumentUploadUiState().copy(
            isShopOpen = _uiState.value.isShopOpen,
            pricePerPage = _uiState.value.pricePerPage
        )
        // Also clear persisted data
        clearPersistedData()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun dismissMissingSettingsDialog() {
        _uiState.value = _uiState.value.copy(filesWithMissingSettings = emptyList())
    }
    
    fun dismissFileTypeMismatchDialog() {
        _uiState.value = _uiState.value.copy(fileTypeMismatchError = null)
    }
    
    fun dismissNonPdfSuccessDialog() {
        _uiState.value = _uiState.value.copy(showNonPdfSuccessDialog = false)
        clearState()
    }
    
    fun clearAllUserData() {
        Log.d("DocumentUploadVM", "Clearing all user data")
        _uiState.value = _uiState.value.copy(
            documents = emptyList(),
            currentFileType = null,
            totalCalculatedPrice = 0.0,
            isUploading = false,
            uploadProgress = 0f,
            error = null,
            orderId = null,
            customerId = "",
            customerPhone = "",
            shopId = "",
            canAddMoreFiles = true
        )
        // Also clear persisted data
        clearPersistedData()
    }
    
    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        Log.d("DocumentUploadVM", "ViewModel cleared and auth listener removed")
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
                Log.d("DocumentUploadVM", "=== submitOrderDirectly: Starting parallel upload ===")
                Log.d("DocumentUploadVM", "Document count: ${_uiState.value.documents.size}")

                _uiState.value = _uiState.value.copy(isUploading = true, error = null)
                val documents = _uiState.value.documents
                val total = documents.size
                val completed = AtomicInteger(0)

                val uploadedDocuments = withContext(Dispatchers.IO) {
                    coroutineScope {
                        documents.mapIndexed { index, document ->
                            async {
                                val uri = document.uri ?: run {
                                    Log.e("DocumentUploadVM", "Document URI is null for ${document.fileName}")
                                    throw Exception("Document URI is null - please re-select the file")
                                }
                                val documentUrl = printOrderRepository.uploadDocument(
                                    uri,
                                    document.fileType,
                                    document.fileName
                                )
                                completed.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        uploadProgress = completed.get().toFloat() / total
                                    )
                                }
                                index to document.copy(uri = Uri.parse(documentUrl))
                            }
                        }.awaitAll().sortedBy { it.first }.map { it.second }
                    }
                }

                // Create order via Cloud Function (same as PDF files)
                // For non-PDF files without payment, set totalAmount to 0
                Log.d("DocumentUploadVM", "Calling Cloud Function to create order...")
                val cloudFunctionsRepo = CloudFunctionsRepository()
                val createOrderResult = cloudFunctionsRepo.createOrder(
                    documents = uploadedDocuments,
                    totalAmount = 0.0, // Non-PDF files don't require payment
                    customerPhone = _uiState.value.customerPhone,
                    shopId = _uiState.value.shopId
                )

                createOrderResult.fold(
                    onSuccess = { response ->
                        Log.d("DocumentUploadVM", "✅ Order created successfully: ${response.orderId}")
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            orderId = response.orderId,
                            uploadProgress = 1f,
                            showNonPdfSuccessDialog = true
                        )
                    },
                    onFailure = { e ->
                        Log.e("DocumentUploadVM", "❌ Failed to create order via Cloud Function", e)
                        throw e
                    }
                )

            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "=== Upload failed in submitOrderDirectly ===", e)
                Log.e("DocumentUploadVM", "Error type: ${e.javaClass.name}")
                Log.e("DocumentUploadVM", "Error message: ${e.message}")
                Log.e("DocumentUploadVM", "Error cause: ${e.cause}")
                e.printStackTrace()
                
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "An error occurred",
                    uploadProgress = 0f
                )
            }
        }
    }

    private fun observeShopStatus() {
        viewModelScope.launch {
            try {
                // Wait for shopId to be set before observing
                val shopId = _uiState.value.shopId.takeIf { it.isNotEmpty() } ?: return@launch
                
                Log.d("DocumentUploadVM", "Observing shop settings for: $shopId")
                shopSettingsRepository.observeShopSettings(shopId).collect { settings ->
                    Log.d("DocumentUploadVM", "Shop settings received for $shopId: shopOpen=${settings.shopOpen}")
                    Log.d("DocumentUploadVM", "Pricing - BW: ${settings.pricePerPage.bw}, Color: ${settings.pricePerPage.color}")
                    currentShopSettings = settings
                    _uiState.value = _uiState.value.copy(
                        isShopOpen = settings.shopOpen,
                        pricePerPage = settings.pricePerPage
                    )

                    if (_uiState.value.documents.isNotEmpty()) {
                        recalculateTotalPrice()
                    }
                }
            } catch (e: Exception) {
                Log.e("DocumentUploadVM", "Error observing shop settings", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

// In DocumentUploadViewModel.kt - Add these helper functions at the end of the class

private fun isValidPageRangeForDocument(pageRange: String, maxPages: Int): Boolean {
    if (pageRange.isEmpty()) return true

    try {
        val parts = pageRange.split(",").map { it.trim() }
        if (parts.any { it.isEmpty() }) return false
        for (part in parts) {
            if (part.contains("-")) {
                val range = part.split("-")
                if (range.size != 2) return false
                val startStr = range[0].trim()
                val endStr = range[1].trim()
                if (startStr.isEmpty() || endStr.isEmpty()) return false
                val start = startStr.toInt()
                val end = endStr.toInt()
                if (start <= 0 || end <= 0 || start > end || start > maxPages || end > maxPages) return false
            } else {
                val page = part.toInt()
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
    val parts = pageRange.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    for (part in parts) {
        if (part.contains("-")) {
            val range = part.split("-")
            if (range.size == 2) {
                try {
                    val start = range[0].trim().toInt()
                    val end = range[1].trim().toInt()
                    for (i in start..end) {
                        pages.add(i)
                    }
                } catch (e: Exception) {
                    // Skip invalid range
                }
            }
        } else {
            try {
                pages.add(part.toInt())
            } catch (e: Exception) {
                // Skip invalid page number
            }
        }
    }

    return pages.toList()
}

/**
 * Sanitizes a page range string by removing any pages that exceed maxPages
 * Returns a cleaned page range string with only valid pages
 * Allows partial input during typing (e.g., "1-", "-5", "1,2,") by returning input as-is
 * Enforces rule: cannot mix ranges and commas - must use either "5-10" OR "1,2,3"
 */
private fun sanitizePageRange(pageRange: String, maxPages: Int): String {
    if (pageRange.isEmpty() || maxPages <= 0) return pageRange

    // Check if input contains both ranges and commas (not allowed)
    val hasRange = pageRange.contains("-")
    val hasComma = pageRange.contains(",")
    
    // If both are present, return as-is to show error (validation will catch it)
    if (hasRange && hasComma) {
        // Check if it's partial input (like "1-," or "1-,2")
        val parts = pageRange.split(",").map { it.trim() }
        val hasPartialRange = parts.any { part ->
            part.contains("-") && (
                part.endsWith("-") || 
                part.startsWith("-") ||
                part.split("-").any { it.trim().isEmpty() }
            )
        }
        // If partial, allow typing to continue
        if (hasPartialRange || pageRange.trim().endsWith(",")) {
            return pageRange
        }
        // Otherwise, it's invalid - return as-is for validation to catch
        return pageRange
    }

    // Check if input contains partial ranges (incomplete during typing)
    // If so, return as-is to allow typing
    val hasPartialRange = hasRange && (
        pageRange.endsWith("-") || 
        pageRange.startsWith("-") ||
        pageRange.split(",").any { part ->
            val trimmed = part.trim()
            trimmed.contains("-") && (
                trimmed.endsWith("-") || 
                trimmed.startsWith("-") ||
                trimmed.split("-").any { it.trim().isEmpty() }
            )
        }
    )
    
    // If there's a trailing comma, it's likely partial input
    val hasTrailingComma = pageRange.trim().endsWith(",") && pageRange.split(",").last().trim().isEmpty()
    
    // If input appears to be partial/incomplete, return as-is
    if (hasPartialRange || hasTrailingComma) {
        return pageRange
    }

    // Otherwise, sanitize complete ranges
    val validParts = mutableListOf<String>()
    
    if (hasRange) {
        // Single range format - no commas allowed
        val trimmed = pageRange.trim()
        val range = trimmed.split("-")
        if (range.size == 2) {
            val startStr = range[0].trim()
            val endStr = range[1].trim()
            if (startStr.isNotEmpty() && endStr.isNotEmpty()) {
                val start = startStr.toIntOrNull()?.coerceIn(1, maxPages)
                val end = endStr.toIntOrNull()?.coerceIn(1, maxPages)
                if (start != null && end != null && start <= end && start <= maxPages && end <= maxPages) {
                    return "$start-$end"
                }
            }
        }
        // Invalid range, return as-is
        return pageRange
    } else {
        // Comma-separated format - no ranges allowed
        val parts = pageRange.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            try {
                val page = trimmed.toIntOrNull()?.coerceIn(1, maxPages)
                if (page != null && page <= maxPages) {
                    validParts.add(page.toString())
                }
            } catch (e: Exception) {
                // Skip invalid parts
                continue
            }
        }
        return validParts.joinToString(", ")
    }
}

