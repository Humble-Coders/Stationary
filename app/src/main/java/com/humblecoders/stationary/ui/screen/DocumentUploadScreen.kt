package com.humblecoders.stationary.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.humblecoders.stationary.MainActivity
import com.humblecoders.stationary.data.model.DocumentItem
import com.humblecoders.stationary.data.model.FileType
import com.humblecoders.stationary.data.model.Orientation
import com.humblecoders.stationary.data.model.PricePerPage
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.service.RazorpayService
import com.humblecoders.stationary.ui.component.ShopClosedCard
import com.humblecoders.stationary.ui.viewmodel.DocumentUploadViewModel
import com.humblecoders.stationary.ui.viewmodel.PaymentViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Modern color palette
private val BlueBtn = Color(0xFF3B82F6)
private val BackgroundGray = Color(0xFFF9FAFB)
private val CardWhite = Color.White
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val BorderGray = Color(0xFFE5E7EB)
private val SuccessGreen = Color(0xFF10B981)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadScreen(
    viewModel: DocumentUploadViewModel,
    paymentViewModel: PaymentViewModel,
    activity: ComponentActivity,
    shopId: String,
    sharedFiles: List<Uri>? = null,
    onSharedFilesProcessed: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = onNavigateBack // Default to onNavigateBack for backwards compatibility
) {
    val context = LocalContext.current
    
    // Set shopId when screen is loaded
    LaunchedEffect(shopId) {
        viewModel.setShopId(shopId)
    }

    // Track which URIs have been processed to avoid duplicates
    var processedUris by remember { mutableStateOf(setOf<Uri>()) }
    
    // Load shared files when screen appears (from external share intent)
    LaunchedEffect(sharedFiles, shopId) {
        Log.d("DocumentUploadScreen", "LaunchedEffect fired - sharedFiles: ${sharedFiles?.size ?: 0}, processedUris: ${processedUris.size}")
        if (sharedFiles != null && sharedFiles.isNotEmpty()) {
            // Filter out already processed URIs
            val newUris = sharedFiles.filter { it !in processedUris }
            Log.d("DocumentUploadScreen", "New URIs to process: ${newUris.size}")
            if (newUris.isNotEmpty()) {
                // Small delay to ensure ViewModel is ready with shop settings
                kotlinx.coroutines.delay(200)
                viewModel.selectFiles(context, newUris)
                // Mark these URIs as processed
                processedUris = processedUris + newUris.toSet()
                Log.d("DocumentUploadScreen", "ProcessedUris updated: ${processedUris.size}")
            }
            // Clear shared files from navigation state
            onSharedFilesProcessed()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val paymentState by paymentViewModel.uiState.collectAsState()

    var showSuccessDialog by remember { mutableStateOf(false) }
    var showBackWarningDialog by remember { mutableStateOf(false) }

    // Handle back press with warning if documents are present
    val onBackPressedCallback = remember {
        object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.documents.isNotEmpty()) {
                    showBackWarningDialog = true
                } else {
                    onNavigateBack()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        activity.onBackPressedDispatcher.addCallback(activity, onBackPressedCallback)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBackPressedCallback.remove()
        }
    }

    // Get Razorpay service from MainActivity
    val razorpayService = remember(activity) {
        (activity as? MainActivity)?.razorpayService
    }

    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectFiles(context, uris)
        }
    }

    // Handle payment initiation
    LaunchedEffect(paymentState.razorpayOrderId) {
        val orderId = paymentState.razorpayOrderId
        val keyId = paymentState.razorpayKeyId
        val amount = paymentState.amount

        if (orderId != null && keyId != null && razorpayService != null) {
            Log.d("DocumentUploadScreen", "Starting Razorpay: orderId=$orderId, amount=$amount")

            razorpayService.startPayment(
                razorpayOrderId = orderId,
                amount = amount,
                keyId = keyId,
                customerPhone = uiState.customerPhone,
                customerEmail = "",
                customerName = "Customer",
                callback = object : RazorpayService.PaymentCallback {
                    override fun onPaymentSuccess(razorpayPaymentId: String, razorpaySignature: String) {
                        Log.d("DocumentUploadScreen", "Callback received but handled by MainActivity")
                    }

                    override fun onPaymentError(errorCode: Int, errorMessage: String) {
                        Log.d("DocumentUploadScreen", "Error callback received but handled by MainActivity")
                    }
                }
            )
        }
    }

    // Handle payment verification success
    LaunchedEffect(paymentState.paymentVerified) {
        if (paymentState.paymentVerified) {
            Log.d("DocumentUploadScreen", "Payment verified, showing success dialog")
            showSuccessDialog = true

            // Auto dismiss after 5 seconds
            delay(5000)
            showSuccessDialog = false

            viewModel.clearState()
            paymentViewModel.resetPaymentState()
            onNavigateToHome()
        }
    }

    // Handle payment errors
    LaunchedEffect(paymentState.error) {
        paymentState.error?.let { error ->
            Log.e("DocumentUploadScreen", "Payment error: $error")
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            // Clear error after showing to prevent re-displaying
            paymentViewModel.clearError()
        }
    }

    // Handle document/file errors (including invalid shared files)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Log.e("DocumentUploadScreen", "Document error: $error")
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            // Clear error after showing to prevent re-displaying
            viewModel.clearError()
        }
    }

    // Handle non-PDF upload success (images, documents, etc.)
    LaunchedEffect(uiState.showNonPdfSuccessDialog) {
        if (uiState.showNonPdfSuccessDialog) {
            // Auto dismiss after 5 seconds
            delay(5000)
            viewModel.dismissNonPdfSuccessDialog()
            onNavigateToHome()
        }
    }

    // Show upload screen if no documents, or loading state
    if (uiState.documents.isEmpty()) {
        if (uiState.isLoadingFiles) {
            // Show loading state when files are being processed
            LoadingFilesScreen(onBackPressed = onNavigateBack)
        } else {
            UploadScreen(
                isShopOpen = uiState.isShopOpen,
                onBackPressed = onNavigateBack,
                onUploadClick = { multipleFilePickerLauncher.launch("*/*") }
            )
        }
    } else {
        // Show documents with settings - use warning dialog for back press
        val handleBackPress = {
            if (uiState.documents.isNotEmpty()) {
                showBackWarningDialog = true
            } else {
                onNavigateBack()
            }
        }
        
        DocumentsScreen(
            uiState = uiState,
            paymentState = paymentState,
            onBackPressed = handleBackPress,
            onAddMore = { multipleFilePickerLauncher.launch("*/*") },
            onRemoveDocument = viewModel::removeDocument,
            onUpdateSettings = viewModel::updateDocumentSettings,
            onUpdatePageCount = viewModel::updateDocumentPageCount,
            onProceedWithPayment = {
                viewModel.submitOrderWithPayment { orderId ->
                    Log.d("DocumentUploadScreen", "Order created: $orderId")
                    paymentViewModel.initiatePayment(orderId)
                }
            },
            onProceedDirect = {
                viewModel.submitOrderDirectly {
                    // Success is now handled by LaunchedEffect above
                }
            }
        )
    }

    // Payment success dialog (for PDF orders)
    if (showSuccessDialog) {
        PaymentSuccessDialog()
    }
    
    // Non-PDF upload success dialog (for images, documents, etc.)
    if (uiState.showNonPdfSuccessDialog) {
        NonPdfUploadSuccessDialog()
    }
    
    // Missing settings dialog
    if (uiState.filesWithMissingSettings.isNotEmpty()) {
        MissingSettingsDialog(
            filesWithMissingSettings = uiState.filesWithMissingSettings,
            onDismiss = { viewModel.dismissMissingSettingsDialog() }
        )
    }
    
    // File type mismatch dialog
    uiState.fileTypeMismatchError?.let { errorMessage ->
        FileTypeMismatchDialog(
            errorMessage = errorMessage,
            onDismiss = { viewModel.dismissFileTypeMismatchDialog() }
        )
    }
    
    // Loading dialog when files are being processed
    if (uiState.isLoadingFiles) {
        LoadingFilesDialog()
    }
    
    // Back warning dialog
    if (showBackWarningDialog) {
        BackWarningDialog(
            onDismiss = { showBackWarningDialog = false },
            onConfirm = {
                showBackWarningDialog = false
                viewModel.clearState()
                onNavigateBack()
            }
        )
    }
}

@Composable
private fun PaymentSuccessDialog() {
    Dialog(onDismissRequest = { }) {
        val infiniteTransition = rememberInfiniteTransition(label = "success_animation")

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_animation"
        )

        val rotation by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation_animation"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated checkmark
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing circle
                    Surface(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(scale),
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.2f)
                    ) {}

                    // Middle circle
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.3f)
                    ) {}

                    // Inner circle with checkmark
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = CircleShape,
                        color = SuccessGreen
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(40.dp)
                                    .rotate(rotation)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Payment Successful!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Your order has been placed successfully.\nYou will be redirected shortly.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun NonPdfUploadSuccessDialog() {
    Dialog(onDismissRequest = { }) {
        val infiniteTransition = rememberInfiniteTransition(label = "nonpdf_success_animation")

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_animation"
        )

        val rotation by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation_animation"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated checkmark
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing circle
                    Surface(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(scale),
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.2f)
                    ) {}

                    // Middle circle
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.3f)
                    ) {}

                    // Inner circle with checkmark
                    Surface(
                        modifier = Modifier.size(70.dp),
                        shape = CircleShape,
                        color = SuccessGreen
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(40.dp)
                                    .rotate(rotation)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Order Submitted!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Your files have been uploaded successfully.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                // Pay at shop notice
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BlueBtn.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = null,
                            tint = BlueBtn,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Please pay at the shop\nwhen you collect your order",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = BlueBtn,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingSettingsDialog(
    filesWithMissingSettings: List<Pair<Int, String>>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Warning icon
                Surface(
                    shape = CircleShape,
                    color = ErrorRed.copy(alpha = 0.15f),
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = "Page Settings Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = if (filesWithMissingSettings.size == 1) {
                        "Please enter at least one page number in either Black & White or Color for the following file:"
                    } else {
                        "Please enter at least one page number in either Black & White or Color for the following files:"
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                // Files list in a card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundGray)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filesWithMissingSettings.forEach { (fileNumber, fileName) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // File number badge
                                Surface(
                                    shape = CircleShape,
                                    color = BlueBtn,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$fileNumber",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // File name with ellipsis
                                Text(
                                    text = fileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Got it button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueBtn)
                ) {
                    Text(
                        text = "Got it",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypeMismatchDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Surface(
                    shape = CircleShape,
                    color = ErrorRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = "Cannot Mix File Types",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Message
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                // Got it button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueBtn)
                ) {
                    Text(
                        text = "Got it",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingFilesDialog() {
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = BlueBtn,
                    strokeWidth = 4.dp
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Loading Files...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Please wait while your files are being processed",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BackWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Warning icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = "Discard Changes?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = "You have selected files. Going back will clear all your selections and settings. Are you sure you want to continue?",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, BorderGray),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Discard button
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text(
                            text = "Discard",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingFilesScreen(
    onBackPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = CardWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Upload Documents",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGray)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Loading indicator
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BlueBtn.copy(alpha = 0.1f),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = BlueBtn,
                            strokeWidth = 4.dp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Loading files...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Please wait while we process your files",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UploadScreen(
    isShopOpen: Boolean,
    onBackPressed: () -> Unit,
    onUploadClick: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = CardWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Upload Documents",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGray)
                .padding(paddingValues)
        ) {
            if (!isShopOpen) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ShopClosedCard(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.weight(1f))

                    // Upload illustration
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = BlueBtn.copy(alpha = 0.1f),
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = BlueBtn,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "Upload Your Documents",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Select one or multiple files\nto start printing",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.weight(1f))

                    // Upload button
                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Choose Files",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Supported: PDF, Word, PowerPoint, Images\nMax: 50 images, 10 documents. Mix allowed.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsScreen(
    uiState: com.humblecoders.stationary.ui.viewmodel.DocumentUploadUiState,
    paymentState: com.humblecoders.stationary.ui.viewmodel.PaymentUiState,
    onBackPressed: () -> Unit,
    onAddMore: () -> Unit,
    onRemoveDocument: (String) -> Unit,
    onUpdateSettings: (String, PrintSettings) -> Unit,
    onUpdatePageCount: (String, Int) -> Unit,
    onProceedWithPayment: () -> Unit,
    onProceedDirect: () -> Unit
) {
    // Use pager only when all files are PDF; otherwise use list (supports mixed types)
    val isAllPdf = uiState.currentFileType == FileType.PDF
    val isMixedTypes = uiState.currentFileType == null && uiState.documents.isNotEmpty()
    val pagerState = if (isAllPdf) rememberPagerState(pageCount = { uiState.documents.size }) else null
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Surface(
                color = CardWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        
                        // Centered Add Files button
                        OutlinedButton(
                            onClick = onAddMore,
                            enabled = !uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null && uiState.canAddMoreFiles,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, if (!uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null && uiState.canAddMoreFiles) BlueBtn else BorderGray),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BlueBtn,
                                disabledContentColor = TextSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (!uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null && uiState.canAddMoreFiles) BlueBtn else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Add Files",
                                color = if (!uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null && uiState.canAddMoreFiles) BlueBtn else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        // Empty spacer for balance
                        Spacer(Modifier.width(48.dp))
                    }

                    // Page indicator with navigation arrows when all PDF
                    if (isAllPdf && pagerState != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left arrow
                            IconButton(
                                onClick = {
                                    val prevPage = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(prevPage)
                                    }
                                },
                                enabled = pagerState.currentPage > 0 && !uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous file",
                                    tint = if (pagerState.currentPage > 0 && !uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null) 
                                        TextPrimary else BorderGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // File counter
                            Text(
                                text = "File ${pagerState.currentPage + 1} of ${uiState.documents.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )

                            Spacer(Modifier.width(8.dp))

                            // Right arrow
                            IconButton(
                                onClick = {
                                    val nextPage = (pagerState.currentPage + 1).coerceAtMost(uiState.documents.size - 1)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(nextPage)
                                    }
                                },
                                enabled = pagerState.currentPage < uiState.documents.size - 1 && !uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Next file",
                                    tint = if (pagerState.currentPage < uiState.documents.size - 1 && !uiState.isUploading && !paymentState.isProcessing && uiState.orderId == null) 
                                        TextPrimary else BorderGray,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(180f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${uiState.documents.size} ${if (uiState.documents.size == 1) "file" else "files"}${if (isMixedTypes) " (mixed)" else ""}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGray)
                .padding(paddingValues)
        ) {
            if (isAllPdf && pagerState != null) {
                // Horizontal pager when all documents are PDF
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DocumentCard(
                            document = uiState.documents[page],
                            pricePerPage = uiState.pricePerPage,
                            isProcessing = uiState.isUploading || paymentState.isProcessing || uiState.orderId != null,
                            onRemove = { onRemoveDocument(uiState.documents[page].id) },
                            onUpdateSettings = { settings ->
                                onUpdateSettings(uiState.documents[page].id, settings)
                            },
                            onUpdatePageCount = { pageCount ->
                                onUpdatePageCount(uiState.documents[page].id, pageCount)
                            }
                        )
                    }
                }
            } else {
                // List for non-PDF or mixed types: PDFs get full card with settings, others get simple card
                val hasMultipleTypes = uiState.documents.map { it.fileType }.distinct().size > 1
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.documents, key = { _, doc -> doc.id }) { index, document ->
                        val isFirstOfType = uiState.documents.indexOfFirst { it.fileType == document.fileType } == index
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (hasMultipleTypes && isFirstOfType) {
                                DocumentTypeHeader(title = document.fileType.displayName)
                            }
                            if (document.fileType == FileType.PDF) {
                                DocumentCard(
                                    document = document,
                                    pricePerPage = uiState.pricePerPage,
                                    isProcessing = uiState.isUploading || paymentState.isProcessing || uiState.orderId != null,
                                    onRemove = { onRemoveDocument(document.id) },
                                    onUpdateSettings = { settings -> onUpdateSettings(document.id, settings) },
                                    onUpdatePageCount = { pageCount -> onUpdatePageCount(document.id, pageCount) }
                                )
                            } else {
                                NonPdfDocumentCard(
                                    document = document,
                                    isProcessing = uiState.isUploading || paymentState.isProcessing || uiState.orderId != null,
                                    onRemove = { onRemoveDocument(document.id) }
                                )
                            }
                        }
                    }
                   item { Spacer(Modifier.height(6.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Bottom section (no shadow)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Proceed button (whole button fills blue/grey as progress when processing)
                    val isProcessing = uiState.isUploading || paymentState.isProcessing || uiState.orderId != null
                    val progressFraction = when {
                        uiState.isUploading -> uiState.uploadProgress.coerceIn(0f, 1f)
                        paymentState.isProcessing -> 1f
                        else -> 0f
                    }
                    Button(
                        onClick = {
                            if (uiState.documents.any { it.fileType == FileType.PDF }) {
                                onProceedWithPayment()
                            } else {
                                onProceedDirect()
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            focusedElevation = 0.dp,
                            disabledElevation = 0.dp
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                // Grey track (full size)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(BorderGray)
                                )
                                // Blue fill from left by progress
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .fillMaxHeight()
                                        .background(BlueBtn)
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            uiState.isUploading -> "Processing... ${(uiState.uploadProgress * 100).toInt()}%"
                                            paymentState.isProcessing -> "Processing Payment..."
                                            else -> "Processing..."
                                        },
                                        color = if (progressFraction >= 0.5f) Color.White else TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Proceed",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Error display
                    AnimatedVisibility(
                        visible = uiState.error != null || paymentState.error != null
                    ) {
                        val errorMessage = uiState.error ?: paymentState.error
                        errorMessage?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTypeHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 0.dp)
    )
}

@Composable
private fun NonPdfDocumentCard(
    document: DocumentItem,
    isProcessing: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File preview/icon
            if (document.fileType == FileType.IMAGE && document.previewBitmap != null) {
                Image(
                    bitmap = document.previewBitmap.asImageBitmap(),
                    contentDescription = "Preview",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BlueBtn.copy(alpha = 0.1f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = BlueBtn,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = document.fileType.displayName,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = !isProcessing,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = if (!isProcessing) TextSecondary else BorderGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentItem,
    pricePerPage: PricePerPage,
    isProcessing: Boolean,
    onRemove: () -> Unit,
    onUpdateSettings: (PrintSettings) -> Unit,
    onUpdatePageCount: (Int) -> Unit
) {
    val pageOverlapError = if (document.fileType == FileType.PDF) {
        checkPageOverlap(
            document.printSettings.customBWPages,
            document.printSettings.customColorPages,
            document.getEffectivePageCount()
        )
    } else null

    // Single combined card: preview/name + optional page input + print settings (for PDF)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Preview row: icon, name, remove
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (document.previewBitmap != null) {
                            Image(
                                bitmap = document.previewBitmap.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BlueBtn.copy(alpha = 0.1f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = BlueBtn,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.fileName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (document.fileType == FileType.PDF) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${document.getEffectivePageCount()} pages",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRemove,
                        enabled = !isProcessing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = if (!isProcessing) TextSecondary else BorderGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Page count input for PDFs with detection issues
            if (document.needsUserPageInput) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Number of Pages",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = document.userInputPageCount.toString().takeIf { it != "0" } ?: "",
                        onValueChange = { value ->
                            val pages = value.toIntOrNull()?.coerceAtLeast(1) ?: 0
                            onUpdatePageCount(pages)
                        },
                        enabled = !isProcessing,
                        placeholder = { Text("Enter page count", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueBtn,
                            unfocusedBorderColor = BorderGray,
                            disabledBorderColor = BorderGray,
                            disabledTextColor = TextSecondary
                        )
                    )
                }
            }

            // Print settings (for PDF) — same card, no separate card
            if (document.fileType == FileType.PDF) {
                HorizontalDivider(color = BorderGray)
                PrintSettingsContent(
                    document = document,
                    pricePerPage = pricePerPage,
                    isProcessing = isProcessing,
                    onUpdateSettings = onUpdateSettings,
                    pageOverlapError = pageOverlapError
                )
            }
        }
    }

    // Spacer to allow scrolling to see number of copies
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SupportingTextContent(
    maxPages: Int,
    text: String,
    isError: Boolean
) {
    if (maxPages > 0) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isError) ErrorRed else TextSecondary
        )
    }
}

@Composable
private fun PrintSettingsContent(
    document: DocumentItem,
    pricePerPage: PricePerPage,
    isProcessing: Boolean,
    onUpdateSettings: (PrintSettings) -> Unit,
    pageOverlapError: String?
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)) {
            // Black & White Pages (removed "Print Type" heading)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Black & White Pages",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            val pageCount = document.getEffectivePageCount()
                            if (pageCount > 0) {
                                onUpdateSettings(document.printSettings.copy(customBWPages = "1-$pageCount"))
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BlueBtn,
                            disabledContentColor = TextSecondary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val maxPages = document.getEffectivePageCount()
                val bwPages = document.printSettings.customBWPages
                val hasInvalidBWPages = bwPages.isNotEmpty() && !isValidPageRange(bwPages, maxPages)

                OutlinedTextField(
                    value = document.printSettings.customBWPages,
                    onValueChange = { value: String ->
                        val filtered = value.filter { it.isDigit() || it == ',' || it == '-' }
                        onUpdateSettings(document.printSettings.copy(customBWPages = filtered))
                    },
                    enabled = !isProcessing,
                    placeholder = {
                        Text(
                            text = "e.g. 1,2-4,5 (max: $maxPages)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 15.sp
                        )
                    },

                    isError = hasInvalidBWPages,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (hasInvalidBWPages) ErrorRed else BlueBtn,
                        unfocusedBorderColor = if (hasInvalidBWPages) ErrorRed else BorderGray,
                        cursorColor = BlueBtn,
                        errorBorderColor = ErrorRed,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledBorderColor = BorderGray,
                        disabledTextColor = TextSecondary
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    trailingIcon = {
                        if (bwPages.isNotEmpty()) {
                            IconButton(
                                onClick = { onUpdateSettings(document.printSettings.copy(customBWPages = "")) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp),
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                )
                if (hasInvalidBWPages) {
                    Spacer(Modifier.height(4.dp))
                    val errorMessage = getPageRangeErrorMessage(bwPages, maxPages)
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Colored Pages
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Colored Pages",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            val pageCount = document.getEffectivePageCount()
                            if (pageCount > 0) {
                                onUpdateSettings(document.printSettings.copy(customColorPages = "1-$pageCount"))
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BlueBtn,
                            disabledContentColor = TextSecondary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val maxPages = document.getEffectivePageCount()
                val colorPages = document.printSettings.customColorPages
                val hasInvalidColorPages = colorPages.isNotEmpty() && !isValidPageRange(colorPages, maxPages)

                OutlinedTextField(
                    value = document.printSettings.customColorPages,
                    onValueChange = { value: String ->
                        val filtered = value.filter { it.isDigit() || it == ',' || it == '-' }
                        onUpdateSettings(document.printSettings.copy(customColorPages = filtered))
                    },
                    enabled = !isProcessing,
                    placeholder = {
                        Text(
                            text = "e.g. 1,2-4,5 (max: $maxPages)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 15.sp
                        )
                    },
                   
                    isError = hasInvalidColorPages,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (hasInvalidColorPages) ErrorRed else BlueBtn,
                        unfocusedBorderColor = if (hasInvalidColorPages) ErrorRed else BorderGray,
                        cursorColor = BlueBtn,
                        errorBorderColor = ErrorRed,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledBorderColor = BorderGray,
                        disabledTextColor = TextSecondary
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    trailingIcon = {
                        if (colorPages.isNotEmpty()) {
                            IconButton(
                                onClick = { onUpdateSettings(document.printSettings.copy(customColorPages = "")) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp),
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                )
                if (hasInvalidColorPages) {
                    Spacer(Modifier.height(4.dp))
                    val errorMessage = getPageRangeErrorMessage(colorPages, maxPages)
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Show error if pages overlap
            pageOverlapError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderGray)
            Spacer(Modifier.height(10.dp))

            // Orientation
            Text(
                text = "Orientation",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Orientation.entries.forEach { orientation ->
                    Button(
                        onClick = {
                            onUpdateSettings(document.printSettings.copy(orientation = orientation))
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = if (document.printSettings.orientation != orientation)
                            BorderStroke(1.dp, BorderGray) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (document.printSettings.orientation == orientation)
                                BlueBtn else Color.White,
                            contentColor = if (document.printSettings.orientation == orientation)
                                Color.White else Color(0xFF374151),
                            disabledContainerColor = if (document.printSettings.orientation == orientation)
                                BlueBtn.copy(alpha = 0.5f) else Color(0xFFF3F4F6),
                            disabledContentColor = if (document.printSettings.orientation == orientation)
                                Color.White.copy(alpha = 0.5f) else TextSecondary
                        )
                    ) {
                        Text(
                            orientation.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Print sides
            Text(
                text = "Print Sides",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val sides = listOf("One Side" to false, "Both Sides" to true)
                sides.forEach { (label, value) ->
                    Button(
                        onClick = {
                            onUpdateSettings(document.printSettings.copy(printOnBothSides = value))
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = if (document.printSettings.printOnBothSides != value)
                            BorderStroke(1.dp, BorderGray) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (document.printSettings.printOnBothSides == value)
                                BlueBtn else Color.White,
                            contentColor = if (document.printSettings.printOnBothSides == value)
                                Color.White else Color(0xFF374151),
                            disabledContainerColor = if (document.printSettings.printOnBothSides == value)
                                BlueBtn.copy(alpha = 0.5f) else Color(0xFFF3F4F6),
                            disabledContentColor = if (document.printSettings.printOnBothSides == value)
                                Color.White.copy(alpha = 0.5f) else TextSecondary
                        )
                    ) {
                        Text(
                            label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Number of copies
            Text(
                text = "Number of Copies",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button
                Button(
                    onClick = {
                        if (document.printSettings.copies > 1) {
                            onUpdateSettings(
                                document.printSettings.copy(
                                    copies = document.printSettings.copies - 1
                                )
                            )
                        }
                    },
                    enabled = !isProcessing && document.printSettings.copies > 1,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlueBtn,
                        disabledContainerColor = BlueBtn.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "-",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Copies display
                OutlinedTextField(
                    value = document.printSettings.copies.toString(),
                    onValueChange = { value ->
                        val newValue = value.toIntOrNull()
                        if (newValue != null && newValue in 1..10) {
                            onUpdateSettings(document.printSettings.copy(copies = newValue))
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueBtn,
                        unfocusedBorderColor = BorderGray,
                        cursorColor = BlueBtn,
                        disabledBorderColor = BorderGray,
                        disabledTextColor = TextSecondary
                    )
                )

                // Plus button
                Button(
                    onClick = {
                        if (document.printSettings.copies < 10) {
                            onUpdateSettings(
                                document.printSettings.copy(
                                    copies = document.printSettings.copies + 1
                                )
                            )
                        }
                    },
                    enabled = !isProcessing && document.printSettings.copies < 10,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlueBtn,
                        disabledContainerColor = BlueBtn.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "+",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            // Helper text for max copies
            if (document.printSettings.copies >= 10) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Maximum 10 copies allowed",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
}

/**
 * Validates page range format: comma-separated items, each item is a number or range (e.g. 1,2-4,5).
 * Rejects: trailing comma, trailing hyphen, multiple commas/hyphens, empty after comma/hyphen.
 */
private fun isValidPageRange(pageRange: String, maxPages: Int): Boolean {
    if (pageRange.isEmpty() || maxPages <= 0) return true

    val parts = pageRange.split(",").map { it.trim() }
    if (parts.any { it.isEmpty() }) return false

    for (part in parts) {
        if (part.contains("-")) {
            val rangeParts = part.split("-")
            if (rangeParts.size != 2) return false
            val startStr = rangeParts[0].trim()
            val endStr = rangeParts[1].trim()
            if (startStr.isEmpty() || endStr.isEmpty()) return false
            val start = startStr.toIntOrNull() ?: return false
            val end = endStr.toIntOrNull() ?: return false
            if (start <= 0 || end <= 0 || start > end || start > maxPages || end > maxPages) return false
        } else {
            val page = part.toIntOrNull() ?: return false
            if (page <= 0 || page > maxPages) return false
        }
    }
    return true
}

private fun getPageRangeErrorMessage(pageRange: String, maxPages: Int): String {
    if (pageRange.isEmpty()) return "Enter page numbers (e.g. 1,2-4,5)"
    val parts = pageRange.split(",").map { it.trim() }
    if (parts.any { it.isEmpty() }) {
        return "Remove trailing comma or use a number after comma"
    }
    for (part in parts) {
        if (part.contains("-")) {
            val rangeParts = part.split("-")
            if (rangeParts.size != 2) return "Use only one hyphen per range (e.g. 2-4)"
            val startStr = rangeParts[0].trim()
            val endStr = rangeParts[1].trim()
            if (startStr.isEmpty() || endStr.isEmpty()) {
                return "Enter a number after hyphen (e.g. 2-4)"
            }
            val start = startStr.toIntOrNull()
            val end = endStr.toIntOrNull()
            if (start == null || end == null) return "Use numbers only"
            if (start <= 0 || end <= 0) return "Page numbers must be at least 1"
            if (start > end) return "Range start must be ≤ end (e.g. 2-4)"
            if (start > maxPages || end > maxPages) return "Page numbers cannot exceed $maxPages"
        } else {
            val page = part.toIntOrNull()
            if (page == null) return "Use numbers only"
            if (page <= 0) return "Page numbers must be at least 1"
            if (page > maxPages) return "Page numbers cannot exceed $maxPages"
        }
    }
    return "Page numbers cannot exceed $maxPages"
}

private fun checkPageOverlap(bwPages: String, colorPages: String, maxPages: Int): String? {
    if (bwPages.isEmpty() || colorPages.isEmpty()) return null

    try {
        val bwPagesList = parsePageRangeToList(bwPages)
        val colorPagesList = parsePageRangeToList(colorPages)

        val overlapping = bwPagesList.intersect(colorPagesList.toSet())

        return if (overlapping.isNotEmpty()) {
            "Pages ${overlapping.sorted().joinToString(", ")} are specified in both B&W and Color"
        } else null
    } catch (e: Exception) {
        return "Invalid page format"
    }
}

private fun parsePageRangeToList(pageRange: String): List<Int> {
    if (pageRange.isEmpty()) return emptyList()

    val pages = mutableSetOf<Int>()
    val parts = pageRange.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    for (part in parts) {
        if (part.contains("-")) {
            val range = part.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toInt()
                val end = range[1].trim().toInt()
                for (i in start..end) {
                    pages.add(i)
                }
            }
        } else {
            pages.add(part.toInt())
        }
    }

    return pages.toList()
}

data class PriceBreakdown(
    val bwPages: Int,
    val colorPages: Int,
    val bwPrice: Double,
    val colorPrice: Double
)

private fun parsePageRangeCount(pageRange: String): Int {
    if (pageRange.isEmpty()) return 0

    try {
        val pages = mutableSetOf<Int>()
        val parts = pageRange.split(",")

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    val start = range[0].trim().toIntOrNull()?.coerceAtLeast(1) ?: continue
                    val end = range[1].trim().toIntOrNull()?.coerceAtLeast(1) ?: continue
                    if (start <= end) {
                        for (i in start..end) {
                            pages.add(i)
                        }
                    }
                }
            } else {
                val page = trimmed.toIntOrNull()
                if (page != null && page >= 1) {
                    pages.add(page)
                }
            }
        }

        return pages.size
    } catch (e: Exception) {
        return 0
    }
}

private fun calculatePriceBreakdown(
    documents: List<DocumentItem>,
    pricePerPage: PricePerPage
): PriceBreakdown {
    var totalBWPages = 0
    var totalColorPages = 0
    
    documents.forEach { doc ->
        val bwPages = parsePageRangeCount(doc.printSettings.customBWPages)
        val colorPages = parsePageRangeCount(doc.printSettings.customColorPages)
        val copies = doc.printSettings.copies
        
        totalBWPages += bwPages * copies
        totalColorPages += colorPages * copies
    }
    
    val bwPrice = totalBWPages * pricePerPage.bw
    val colorPrice = totalColorPages * pricePerPage.color
    
    return PriceBreakdown(
        bwPages = totalBWPages,
        colorPages = totalColorPages,
        bwPrice = bwPrice,
        colorPrice = colorPrice
    )
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}