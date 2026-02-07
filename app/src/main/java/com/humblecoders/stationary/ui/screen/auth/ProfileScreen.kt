package com.humblecoders.stationary.ui.screen.auth

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.humblecoders.stationary.data.model.ProfileState
import com.humblecoders.stationary.ui.viewmodel.auth.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

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
fun ProfileScreen(
    viewModel: ProfileViewModel,
    navController: NavController,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val isSubmittingBugReport by viewModel.isSubmittingBugReport.collectAsState()
    val bugReportResult by viewModel.bugReportSubmissionResult.collectAsState()
    val context = LocalContext.current
    var showBugReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Profile",
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
            when (val state = profileState) {
                is ProfileState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BlueBtn)
                    }
                }
                is ProfileState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = ErrorRed
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Error loading profile",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.loadUserProfile() },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueBtn)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ProfileState.Success -> {
                    val profile = state.profile
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Header Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Picture
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(BlueBtn.copy(alpha = 0.1f))
                                        .border(3.dp, BlueBtn, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profile.profilePictureUrl.isNotEmpty()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(profile.profilePictureUrl),
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(50.dp),
                                            tint = BlueBtn
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = profile.name.ifEmpty { "User" },
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = profile.email,
                                    fontSize = 16.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // User Information Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Account Information",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Sign-in Method
                                InfoRow(
                                    icon = Icons.Default.AccountCircle,
                                    label = "Sign-in Method",
                                    value = if (profile.isGoogleSignIn) "Google" else "Email/Password"
                                )

                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = BorderGray)

                                Spacer(Modifier.height(12.dp))

                                // Account Created Date
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                val createdDate = if (profile.createdAt > 0) {
                                    dateFormat.format(Date(profile.createdAt))
                                } else {
                                    "N/A"
                                }
                                InfoRow(
                                    icon = Icons.Default.CalendarToday,
                                    label = "Member Since",
                                    value = createdDate
                                )

                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = BorderGray)

                                Spacer(Modifier.height(12.dp))

                                // User ID
                               
                            }
                        }

                        // Report Bug Button
                        Button(
                            onClick = { showBugReportDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueBtn)
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Report a Bug",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Sign Out Button
                        OutlinedButton(
                            onClick = {
                                viewModel.signOut()
                                onSignOut()
                            },
                            enabled = !isSigningOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(2.dp, ErrorRed),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorRed
                            )
                        ) {
                            if (isSigningOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ErrorRed,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = if (isSigningOut) "Signing Out..." else "Sign Out",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Document Deletion Note
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = BlueBtn.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = BlueBtn,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Document Deletion Policy",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "All documents are automatically deleted after printing, 24 hours after order completion.",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Handle bug report submission result
    LaunchedEffect(bugReportResult) {
        bugReportResult?.fold(
            onSuccess = {
                Toast.makeText(
                    context,
                    "Bug report submitted successfully. Thank you for your feedback!",
                    Toast.LENGTH_LONG
                ).show()
                showBugReportDialog = false
                viewModel.clearBugReportResult()
            },
            onFailure = { exception ->
                Toast.makeText(
                    context,
                    "Failed to submit bug report: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearBugReportResult()
            }
        )
    }

    // Bug Report Dialog
    if (showBugReportDialog) {
        BugReportDialog(
            onDismiss = { 
                if (!isSubmittingBugReport) {
                    showBugReportDialog = false
                }
            },
            onSubmit = { subject, description, screenshotUri ->
                viewModel.submitBugReport(subject, description, screenshotUri)
            },
            isSubmitting = isSubmittingBugReport
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = TextSecondary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun BugReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Uri?) -> Unit,
    isSubmitting: Boolean = false
) {
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        screenshotUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Report a Bug",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Subject Field
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("Brief description of the issue") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueBtn,
                        unfocusedBorderColor = BorderGray
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Please describe the issue in detail...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueBtn,
                        unfocusedBorderColor = BorderGray
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Screenshot Attachment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderGray),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (screenshotUri != null) "Change Screenshot" else "Attach Screenshot",
                            fontSize = 14.sp
                        )
                    }

                    if (screenshotUri != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { screenshotUri = null },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = ErrorRed
                            )
                        }
                    }
                }

                if (screenshotUri != null) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(screenshotUri),
                            contentDescription = "Screenshot",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderGray),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (subject.isNotBlank() && description.isNotBlank()) {
                                onSubmit(subject, description, screenshotUri)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Please fill in all required fields",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                        enabled = subject.isNotBlank() && description.isNotBlank() && !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Submit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
