package com.humblecoders.stationary.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
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
import com.humblecoders.stationary.data.model.DocumentItem
import com.humblecoders.stationary.data.model.FileType
import com.humblecoders.stationary.data.model.Orientation
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.ui.component.ShopClosedCard
import com.humblecoders.stationary.ui.viewmodel.DocumentUploadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadScreen(
    viewModel: DocumentUploadViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String, Double, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectFiles(context, uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Documents") },
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

            // File Selection Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Documents to Print",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (uiState.documents.isNotEmpty()) {
                            Text(
                                text = "${uiState.documents.size} file${if (uiState.documents.size != 1) "s" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.documents.isEmpty()) {
                        // Initial file selection
                        FileSelectionPrompt(
                            onSelectFiles = {
                                multipleFilePickerLauncher.launch("*/*")
                            }
                        )
                    } else {
                        // Show current file type and add more option
                        CurrentFileTypeHeader(
                            fileType = uiState.currentFileType!!,
                            documentCount = uiState.documents.size,
                            canAddMore = uiState.canAddMoreFiles,
                            onAddMore = {
                                multipleFilePickerLauncher.launch("*/*")
                            },
                            onClearAll = {
                                viewModel.clearState()
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Documents List
                        DocumentsList(
                            documents = uiState.documents,
                            onRemoveDocument = viewModel::removeDocument,
                            onToggleExpansion = viewModel::toggleDocumentExpansion,
                            onUpdateSettings = viewModel::updateDocumentSettings,
                            onUpdatePageCount = viewModel::updateDocumentPageCount
                        )
                    }
                }
            }

            // Total Price Display
            if (uiState.documents.isNotEmpty()) {
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
                            text = "₹${String.format("%.2f", uiState.totalCalculatedPrice)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "Total: ${uiState.documents.sumOf { it.getEffectivePageCount() }} pages • ${uiState.documents.size} documents",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.submitOrderWithPayment { orderId ->
                                onNavigateToPayment(orderId, uiState.totalCalculatedPrice, uiState.customerPhone)
                            }
                        },
                        enabled = !uiState.isUploading && uiState.documents.isNotEmpty(),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading... ${(uiState.uploadProgress * 100).toInt()}%")
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

                    OutlinedButton(
                        onClick = viewModel::submitOrderWithoutPayment,
                        enabled = !uiState.isUploading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Upload Without Payment")
                    }
                }
            }

            // Error display
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
private fun FileSelectionPrompt(
    onSelectFiles: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "Select documents to print",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can select multiple files of the same type",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSelectFiles,
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
                text = "Supported: PDF, Word (DOCX) • Max 10 files",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CurrentFileTypeHeader(
    fileType: FileType,
    documentCount: Int,
    canAddMore: Boolean,
    onAddMore: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (fileType == FileType.PDF) Icons.Outlined.Description else Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${fileType.displayName} Documents",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$documentCount selected",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAddMore) {
                    IconButton(onClick = onAddMore) {
                        Icon(Icons.Default.Add, contentDescription = "Add more files")
                    }
                }
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.Close, contentDescription = "Clear all")
                }
            }
        }
    }
}

@Composable
private fun DocumentsList(
    documents: List<DocumentItem>,
    onRemoveDocument: (String) -> Unit,
    onToggleExpansion: (String) -> Unit,
    onUpdateSettings: (String, PrintSettings) -> Unit,
    onUpdatePageCount: (String, Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(400.dp) // Fixed height to prevent scroll issues
    ) {
        items(documents, key = { it.id }) { document ->
            DocumentCard(
                document = document,
                onRemove = { onRemoveDocument(document.id) },
                onToggleExpansion = { onToggleExpansion(document.id) },
                onUpdateSettings = { settings -> onUpdateSettings(document.id, settings) },
                onUpdatePageCount = { pageCount -> onUpdatePageCount(document.id, pageCount) }
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentItem,
    onRemove: () -> Unit,
    onToggleExpansion: () -> Unit,
    onUpdateSettings: (PrintSettings) -> Unit,
    onUpdatePageCount: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Document Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PDF Preview or DOCX Icon
                    if (document.fileType == FileType.PDF && document.previewBitmap != null) {
                        Image(
                            bitmap = document.previewBitmap.asImageBitmap(),
                            contentDescription = "PDF Preview",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (document.fileType == FileType.PDF) Icons.Outlined.Description else Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = document.fileName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val info = when (document.fileType) {
                            FileType.PDF -> "${formatFileSize(document.fileSize)} • ${document.getEffectivePageCount()} pages"
                            FileType.DOCX -> "${formatFileSize(document.fileSize)} • Word Document"
                        }

                        Text(
                            text = info,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "₹${String.format("%.2f", document.calculatedPrice)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleExpansion) {
                        Icon(
                            imageVector = if (document.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (document.isExpanded) "Collapse" else "Expand"
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Page Count Input for PDFs with detection issues
            if (document.needsUserPageInput) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = document.userInputPageCount.toString().takeIf { it != "0" } ?: "",
                    onValueChange = { value ->
                        val pages = value.toIntOrNull()?.coerceAtLeast(1) ?: 0
                        onUpdatePageCount(pages)
                    },
                    label = { Text("Number of pages") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Expanded Settings Panel
            AnimatedVisibility(
                visible = document.isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    PrintSettingsPanel(
                        settings = document.printSettings,
                        fileType = document.fileType,
                        onSettingsChange = onUpdateSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun PrintSettingsPanel(
    settings: PrintSettings,
    fileType: FileType,
    onSettingsChange: (PrintSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Color Mode
        Text(
            text = "Color Mode",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ColorMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.colorMode == mode,
                    onClick = {
                        onSettingsChange(settings.copy(colorMode = mode))
                    },
                    label = {
                        Text("${mode.displayName} (₹${if (mode == ColorMode.COLOR) "5" else "2"}/page)")
                    }
                )
            }
        }

        // Orientation
        Text(
            text = "Orientation",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Orientation.entries.forEach { orientation ->
                FilterChip(
                    selected = settings.orientation == orientation,
                    onClick = {
                        onSettingsChange(settings.copy(orientation = orientation))
                    },
                    label = { Text(orientation.displayName) }
                )
            }
        }

        // Pages to Print (PDF only)
        if (fileType == FileType.PDF) {
            Text(
                text = "Pages to Print",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PageSelection.entries.forEach { selection ->
                    FilterChip(
                        selected = settings.pagesToPrint == selection,
                        onClick = {
                            onSettingsChange(settings.copy(pagesToPrint = selection))
                        },
                        label = { Text(selection.displayName) }
                    )
                }
            }

            AnimatedVisibility(visible = settings.pagesToPrint == PageSelection.CUSTOM) {
                OutlinedTextField(
                    value = settings.customPages,
                    onValueChange = {
                        onSettingsChange(settings.copy(customPages = it))
                    },
                    label = { Text("Custom Pages") },
                    placeholder = { Text("e.g., 1-3,5,7-10") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Copies
        Text(
            text = "Copies",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val newCopies = (settings.copies - 1).coerceAtLeast(1)
                    onSettingsChange(settings.copy(copies = newCopies))
                },
                enabled = settings.copies > 1
            ) {
                Text("-", fontSize = 20.sp)
            }

            Text(
                text = settings.copies.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    val newCopies = (settings.copies + 1).coerceAtMost(10)
                    onSettingsChange(settings.copy(copies = newCopies))
                },
                enabled = settings.copies < 10
            ) {
                Text("+", fontSize = 20.sp)
            }

            Text(
                text = "(Max: 10)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024 -> "$sizeInBytes B"
        sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
        else -> String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0))
    }
}