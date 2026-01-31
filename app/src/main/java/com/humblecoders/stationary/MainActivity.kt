package com.humblecoders.stationary

import PrintQTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.humblecoders.stationary.data.repository.BugReportRepository
import com.humblecoders.stationary.data.repository.FCMTokenRepository
import com.humblecoders.stationary.data.repository.FirebaseAuthRepository
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ProfileRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.data.service.RazorpayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.humblecoders.stationary.navigation.PrintShopNavigation
import com.humblecoders.stationary.ui.viewmodel.*
import com.humblecoders.stationary.ui.viewmodel.auth.LoginViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.ProfileViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterViewModel
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

// Data class to hold shared files information
data class SharedFilesData(
    val uris: List<Uri>,
    val handled: Boolean = false
)

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var authRepository: FirebaseAuthRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository
    private lateinit var bugReportRepository: BugReportRepository

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var registerViewModel: RegisterViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel

    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fcmTokenRepository: FCMTokenRepository

    lateinit var razorpayService: RazorpayService
        private set

    // Holds shared files from external apps
    private var sharedFilesData = mutableStateOf<SharedFilesData?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()
        initializeRepositories()
        initializeGoogleSignInLauncher()
        initializeNotificationPermissionLauncher()
        initializeRazorpayService()
        initializeViewModels()
        logAuthState()
        setupAuthStateListener()
        requestNotificationPermission()
        initializeFCM()

        // Handle share intent if app was opened via share
        handleShareIntent(intent)

        setContent {
            PrintQTheme {
                PrintShopApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    // Take read permission to prevent expiration
                    takeUriPermission(uri)
                    Log.d("MainActivity", "Received shared file: $uri")
                    // Accumulate files - add to existing pending files instead of replacing
                    val existingFiles = sharedFilesData.value?.uris ?: emptyList()
                    val newFiles = existingFiles + listOf(uri)
                    sharedFilesData.value = SharedFilesData(newFiles)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    // Take read permission for all URIs to prevent expiration
                    uris.forEach { uri -> takeUriPermission(uri) }
                    Log.d("MainActivity", "Received ${uris.size} shared files")
                    // Accumulate files - add to existing pending files instead of replacing
                    val existingFiles = sharedFilesData.value?.uris ?: emptyList()
                    val newFiles = existingFiles + uris
                    sharedFilesData.value = SharedFilesData(newFiles)
                }
            }
        }
    }

    private fun takeUriPermission(uri: Uri) {
        try {
            // Try to take persistable permission first (for content providers that support it)
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d("MainActivity", "Took persistent permission for: $uri")
        } catch (e: SecurityException) {
            // Permission might not be persistable, that's okay
            // The temporary permission from the intent should still work
            Log.w("MainActivity", "Could not take persistent permission (this is normal): ${e.message}")
        }
    }

    // Get shared files and mark as handled
    fun getSharedFiles(): SharedFilesData? {
        val data = sharedFilesData.value
        return data
    }

    // Clear shared files after they've been processed
    fun clearSharedFiles() {
        sharedFilesData.value = null
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        Log.d("MainActivity", "Payment success: $razorpayPaymentId")

        paymentData?.let { data ->
            Log.d("MainActivity", "Payment data: ${data.data}")

            try {
                // paymentData.data is already a JSONObject, not a Map
                val dataJson = when (val paymentDataObj = data.data) {
                    is JSONObject -> paymentDataObj
                    is String -> JSONObject(paymentDataObj)
                    else -> {
                        Log.e("MainActivity", "Unexpected data type: ${paymentDataObj?.javaClass}")
                        throw ClassCastException("Unexpected payment data type")
                    }
                }

                val razorpayOrderId = dataJson.optString("razorpay_order_id")
                val razorpayPaymentIdFromData = dataJson.optString("razorpay_payment_id")
                val razorpaySignature = dataJson.optString("razorpay_signature")

                Log.d("MainActivity", "Order ID: $razorpayOrderId")
                Log.d("MainActivity", "Payment ID: $razorpayPaymentIdFromData")
                Log.d("MainActivity", "Signature: $razorpaySignature")

                // Use the payment ID from data if available, otherwise use the parameter
                val finalPaymentId = razorpayPaymentIdFromData.ifEmpty {
                    razorpayPaymentId ?: ""
                }

                if (finalPaymentId.isNotEmpty() && razorpaySignature.isNotEmpty()) {
                    // Call verification directly on paymentViewModel
                    paymentViewModel.verifyPayment(
                        razorpayPaymentId = finalPaymentId,
                        razorpaySignature = razorpaySignature
                    )
                } else {
                    Log.e("MainActivity", "Missing payment ID or signature")
                    paymentViewModel.handlePaymentError(
                        0,
                        "Payment data incomplete"
                    )
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "JSON error", e)
                paymentViewModel.handlePaymentError(
                    0,
                    "Error processing payment data: ${e.message}"
                )
            }
        } ?: run {
            Log.e("MainActivity", "Payment data is null")
            paymentViewModel.handlePaymentError(
                0,
                "Payment data not received"
            )
        }
    }

    override fun onPaymentError(errorCode: Int, errorMessage: String?, paymentData: PaymentData?) {
        Log.e("MainActivity", "Payment error: $errorCode - $errorMessage")

        paymentData?.let { data ->
            Log.e("MainActivity", "Error data: ${data.data}")
        }

        // Call error handler directly on paymentViewModel
        paymentViewModel.handlePaymentError(
            errorCode,
            errorMessage ?: "Payment failed"
        )
    }

    private fun initializeFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun logAuthState() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("MainActivity", "User authenticated: ${currentUser.uid}")
            Log.d("MainActivity", "Email: ${currentUser.email}")
        } else {
            Log.e("MainActivity", "NO USER AUTHENTICATED!")
        }
    }

    private fun setupAuthStateListener() {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                // Try to get and save FCM token when user logs in
                initializeFCM()
            }
        }
    }

    private fun initializeRepositories() {
        authRepository = FirebaseAuthRepository(firebaseAuth, this)
        profileRepository = ProfileRepository(firebaseAuth, firestore, this)
        printOrderRepository = PrintOrderRepository(firestore, storage)
        shopSettingsRepository = ShopSettingsRepository(firestore)
        bugReportRepository = BugReportRepository(firestore, storage)
        fcmTokenRepository = FCMTokenRepository()
    }

    private fun initializeRazorpayService() {
        razorpayService = RazorpayService(this)
    }

    private fun initializeGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "Google Sign-In result: ${result.resultCode}")

            when (result.resultCode) {
                RESULT_OK -> {
                    Log.d("MainActivity", "Google Sign-In OK, processing result")
                    loginViewModel.handleGoogleSignInResult(result.data)
                    registerViewModel.handleGoogleSignInResult(result.data)
                }
                RESULT_CANCELED -> {
                    Log.d("MainActivity", "Google Sign-In cancelled by user")
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                    loginViewModel.clearGoogleSignInState()
                    registerViewModel.clearGoogleSignInState()
                }
                else -> {
                    Log.d("MainActivity", "Google Sign-In failed with code: ${result.resultCode}")
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                    loginViewModel.clearGoogleSignInState()
                    registerViewModel.clearGoogleSignInState()
                }
            }
        }
    }

    companion object {
        private const val TAG = "FCMService"
    }

    private fun initializeNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                initializeFCM()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun initializeFCM() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            if (token != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        fcmTokenRepository.saveToken(token)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving FCM token", e)
                    }
                }
            }
        }
    }

    private fun initializeViewModels() {
        loginViewModel = LoginViewModel(authRepository)
        registerViewModel = RegisterViewModel(authRepository)
        profileViewModel = ProfileViewModel(profileRepository, authRepository, bugReportRepository)
        homeViewModel = HomeViewModel(printOrderRepository, shopSettingsRepository)
        documentUploadViewModel = DocumentUploadViewModel(printOrderRepository, shopSettingsRepository)
        paymentViewModel = PaymentViewModel()
    }

    @Composable
    private fun PrintShopApp() {
        val navController = remember {
            NavHostController(this@MainActivity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
        }

        // Get shared files state
        val sharedFiles = sharedFilesData.value

        PrintShopNavigation(
            navController = navController,
            homeViewModel = homeViewModel,
            documentUploadViewModel = documentUploadViewModel,
            paymentViewModel = paymentViewModel,
            loginViewModel = loginViewModel,
            registerViewModel = registerViewModel,
            profileViewModel = profileViewModel,
            activity = this@MainActivity,
            googleSignInLauncher = googleSignInLauncher,
            sharedFilesData = sharedFiles,
            onSharedFilesHandled = { clearSharedFiles() }
        )
    }
}