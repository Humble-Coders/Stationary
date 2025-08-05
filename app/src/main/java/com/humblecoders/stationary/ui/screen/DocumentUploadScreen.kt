package com.humblecoders.stationary.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.data.model.ColorMode
import com.humblecoders.stationary.data.model.Orientation
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.ui.component.ShopClosedCard
import com.humblecoders.stationary.ui.viewmodel.DocumentUploadViewModel
import android.graphics.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.core.graphics.createBitmap
import com.humblecoders.stationary.data.model.FileType

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadScreen(
    viewModel: DocumentUploadViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String, Double, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Track if settings panel is expanded
    var showPrintSettings by remember { mutableStateOf(true) }

    // For numeric input validation
    val copiesString = remember(uiState.printSettings.copies) {
        mutableStateOf(uiState.printSettings.copies.toString())
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFile(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Document") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!uiState.isShopOpen) {
                ShopClosedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                return@Column
            }

            // File Selection Card - Improved UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Document to Print",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    if (uiState.selectedFile == null) {
                        // No file selected yet - completely restructured for better button visibility
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Select a PDF document to print",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Simplified button approach
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") }, // Accept all files, validation happens in selectFile
                                    modifier = Modifier.fillMaxWidth(0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Browse Files",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Supported formats: PDF, Word (DOCX)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }else {
                        // File selected - show details with improved UI
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.fileType == FileType.PDF)
                                        Icons.Outlined.Description
                                    else
                                        Icons.Default.Description, // Use different icon for DOCX
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.fileName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val fileInfo = when (uiState.fileType) {
                                        FileType.PDF -> "Size: ${formatFileSize(uiState.fileSize)} • ${uiState.pageCount} pages"
                                        FileType.DOCX -> "Size: ${formatFileSize(uiState.fileSize)} • Word Document"
                                        null -> "Size: ${formatFileSize(uiState.fileSize)}"
                                    }

                                    Text(
                                        text = fileInfo,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                ) {
                                    Text("Change")
                                }
                            }
                        }

                        // After the file selection card, add preview section

                        AnimatedVisibility(
                            visible = uiState.fileType == FileType.PDF, // Only show for PDF
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Document Preview",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Switch(
                                            checked = uiState.showPreview,
                                            onCheckedChange = { viewModel.togglePreview() }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = uiState.showPreview,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        PdfPreview(
                                            uri = uiState.selectedFile!!,
                                            currentPage = uiState.currentPreviewPage,
                                            totalPages = if (uiState.needsUserPageInput && uiState.userInputPageCount > 0)
                                                uiState.userInputPageCount else uiState.pageCount,
                                            colorMode = uiState.printSettings.colorMode,
                                            orientation = uiState.printSettings.orientation,
                                            onPageChange = { page -> viewModel.updatePreviewPage(page) },
                                            modifier = Modifier.padding(top = 16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = uiState.fileType == FileType.DOCX,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Word Document",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "• No preview available for Word documents\n• Entire document will be printed\n• Document will require manual processing",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }


                        // In DocumentUploadScreen.kt - Add this after file selection
                        if (uiState.needsUserPageInput) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Unable to detect page count automatically",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = uiState.userInputPageCount.toString().takeIf { it != "0" } ?: "",
                                        onValueChange = { value ->
                                            val pages = value.toIntOrNull()?.coerceAtLeast(1) ?: 0
                                            viewModel.updateUserPageCount(pages)
                                        },
                                        label = { Text("Number of pages") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Toggle for Print Settings
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Print Settings",
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )

                            Switch(
                                checked = showPrintSettings,
                                onCheckedChange = { showPrintSettings = it }
                            )
                        }
                    }
                }
            }

            // Expandable Print Settings Section
            AnimatedVisibility(
                visible = uiState.selectedFile != null && showPrintSettings,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Color Mode Section
                        Text(
                            text = "Color Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ColorMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.printSettings.colorMode == mode,
                                    onClick = {
                                        viewModel.updatePrintSettings(
                                            uiState.printSettings.copy(colorMode = mode)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "${mode.displayName} (₹${if (mode == ColorMode.COLOR) "5" else "2"}/page)"
                                        )
                                    }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )


                        // Orientation Section (NEW)
                        Text(
                            text = "Orientation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Orientation.entries.forEach { orientation ->
                                FilterChip(
                                    selected = uiState.printSettings.orientation == orientation,
                                    onClick = {
                                        viewModel.updatePrintSettings(
                                            uiState.printSettings.copy(orientation = orientation)
                                        )
                                    },
                                    label = { Text(orientation.displayName) }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        AnimatedVisibility(
                            visible = uiState.fileType == FileType.PDF, // Only show for PDF
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Text(
                                    text = "Pages to Print",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                                )

                                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        PageSelection.entries.forEach { selection ->
                                            FilterChip(
                                                selected = uiState.printSettings.pagesToPrint == selection,
                                                onClick = {
                                                    viewModel.updatePrintSettings(
                                                        uiState.printSettings.copy(pagesToPrint = selection)
                                                    )
                                                },
                                                label = { Text(selection.displayName) }
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = uiState.printSettings.pagesToPrint == PageSelection.CUSTOM,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        val isValidRange = remember(uiState.printSettings.customPages) {
                                            isValidPageRange(uiState.printSettings.customPages)
                                        }

                                        OutlinedTextField(
                                            value = uiState.printSettings.customPages,
                                            onValueChange = {
                                                viewModel.updatePrintSettings(
                                                    uiState.printSettings.copy(customPages = it)
                                                )
                                            },
                                            label = { Text("Custom Pages") },
                                            placeholder = { Text("e.g., 1-3,5,7-10") },
                                            isError = uiState.printSettings.customPages.isNotEmpty() && !isValidRange,
                                            supportingText = {
                                                if (uiState.printSettings.customPages.isNotEmpty() && !isValidRange) {
                                                    Text("Invalid page range format")
                                                } else {
                                                    Text("Specify individual pages or ranges (e.g., 1,3,5-7)")
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Copies Section - Fixed numeric input
                        Text(
                            text = "Number of Copies",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val newValue = (uiState.printSettings.copies - 1).coerceAtLeast(1)
                                    viewModel.updatePrintSettings(
                                        uiState.printSettings.copy(copies = newValue)
                                    )
                                    copiesString.value = newValue.toString()
                                },
                                enabled = uiState.printSettings.copies > 1
                            ) {
                                Text("-", fontSize = 20.sp)
                            }

                            OutlinedTextField(
                                value = copiesString.value,
                                onValueChange = { value ->
                                    copiesString.value = value
                                    val newCopies = value.toIntOrNull()?.coerceIn(1, 10) ?: uiState.printSettings.copies
                                    if (value.isEmpty() || newCopies.toString() == value) {
                                        viewModel.updatePrintSettings(
                                            uiState.printSettings.copy(copies = if (value.isEmpty()) 1 else newCopies)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                singleLine = true
                            )

                            IconButton(
                                onClick = {
                                    val newValue = (uiState.printSettings.copies + 1).coerceAtMost(10)
                                    viewModel.updatePrintSettings(
                                        uiState.printSettings.copy(copies = newValue)
                                    )
                                    copiesString.value = newValue.toString()
                                },
                                enabled = uiState.printSettings.copies < 10
                            ) {
                                Text("+", fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Maximum: 10 copies",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Other settings like paperSize, orientation, quality would go here
                        // Simplified for clarity, add them as needed
                    }
                }
            }

            // Price Display - Enhanced UI
            if (uiState.selectedFile != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Price",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )

                        Text(
                            text = "₹${String.format("%.2f", uiState.calculatedPrice)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (uiState.printSettings.copies > 1) {
                            Text(
                                text = "(₹${String.format("%.2f", uiState.calculatedPrice / uiState.printSettings.copies)} × ${uiState.printSettings.copies} copies)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Action Buttons - Improved layout and feedback
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.submitOrderWithPayment { orderId ->
                                onNavigateToPayment(orderId, uiState.calculatedPrice, uiState.customerPhone)
                            }
                        },
                        enabled = !uiState.isUploading && uiState.selectedFile != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (uiState.isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload & Pay Now", fontSize = 16.sp)
                        }
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::submitOrderWithoutPayment,
                            enabled = !uiState.isUploading && showPrintSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Upload Without Payment")
                        }

                    }
                }
            }

            // Error display with improved visibility
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreview(
    uri: Uri,
    currentPage: Int,
    totalPages: Int,
    colorMode: ColorMode,
    orientation: Orientation,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri, currentPage, colorMode, orientation) {
        isLoading = true
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (currentPage < renderer.pageCount) {
                        renderer.openPage(currentPage).use { page ->
                            val displayMetrics = context.resources.displayMetrics
                            val baseWidth = (displayMetrics.widthPixels * 0.8).toInt()

                            // Adjust dimensions based on orientation
                            val (width, height) = when (orientation) {
                                Orientation.PORTRAIT -> {
                                    val h = (baseWidth * page.height / page.width.toFloat()).toInt()
                                    baseWidth to h
                                }
                                Orientation.LANDSCAPE -> {
                                    val h = (baseWidth * page.width / page.height.toFloat()).toInt()
                                    baseWidth to h
                                }
                            }

                            val originalBitmap = createBitmap(width, height)

                            // Render based on orientation
                            when (orientation) {
                                Orientation.PORTRAIT -> {
                                    page.render(originalBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                                Orientation.LANDSCAPE -> {
                                    // Create a rotated bitmap
                                    val tempBitmap = createBitmap(height, width)
                                    page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val matrix = Matrix().apply { postRotate(90f) }
                                    val rotatedBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, tempBitmap.width, tempBitmap.height, matrix, true)

                                    val canvas = Canvas(originalBitmap)
                                    val srcRect = Rect(0, 0, rotatedBitmap.width, rotatedBitmap.height)
                                    val destRect = Rect(0, 0, width, height)
                                    canvas.drawBitmap(rotatedBitmap, srcRect, destRect, null)

                                    tempBitmap.recycle()
                                    rotatedBitmap.recycle()
                                }
                            }

                            // Apply color mode filter
                            val finalBitmap = when (colorMode) {
                                ColorMode.COLOR -> originalBitmap
                                ColorMode.BW -> {
                                    applyGrayscaleFilter(originalBitmap)
                                }
                            }

                            bitmap = finalBitmap
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error loading PDF preview: ${e.message}")
            // Handle error
        }
        isLoading = false
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Preview note
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Preview simulation - actual print output may vary",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (totalPages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 0) onPageChange(currentPage - 1) },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }

                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = { if (currentPage < totalPages - 1) onPageChange(currentPage + 1) },
                    enabled = currentPage < totalPages - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
    }
}

private fun applyGrayscaleFilter(originalBitmap: Bitmap): Bitmap {
    val grayscaleBitmap = createBitmap(originalBitmap.width, originalBitmap.height)

    val canvas = Canvas(grayscaleBitmap)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f) // Remove all color saturation
        })
    }

    canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
    return grayscaleBitmap
}

// Helper function to format file size
@SuppressLint("DefaultLocale")
private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024 -> "$sizeInBytes B"
        sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
        else -> String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0))
    }
}

// Validate page range input
private fun isValidPageRange(pageRange: String): Boolean {
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
                if (start <= 0 || end <= 0 || start > end) return false
            } else {
                val page = trimmed.toInt()
                if (page <= 0) return false
            }
        }
        return true
    } catch (e: Exception) {
        println("Invalid page range format: ${e.message}")
        return false
    }
}