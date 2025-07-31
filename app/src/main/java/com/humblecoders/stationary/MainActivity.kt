package com.humblecoders.stationary

import StationaryTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.data.repository.UserPreferencesRepository
import com.humblecoders.stationary.data.service.RazorpayService
import com.humblecoders.stationary.navigation.PrintShopNavigation
import com.humblecoders.stationary.navigation.Screen
import com.humblecoders.stationary.ui.viewmodel.*
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(),PaymentResultWithDataListener {

    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository
    private lateinit var razorpayService: RazorpayService

    private lateinit var mainViewModel: MainViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var customerInfoViewModel: CustomerInfoViewModel
    private lateinit var userPreferencesRepository: UserPreferencesRepository


    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData) {
        // Handle successful payment
        razorpayPaymentId?.let { paymentId ->
            paymentViewModel.handlePaymentSuccess(paymentId, paymentData)
        }
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        // Handle payment error
        paymentViewModel.handlePaymentError(errorCode, response ?: "Payment failed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeRepositories()
        initializeViewModels()

        setContent {
            StationaryTheme {
                PrintShopApp()
            }
        }
    }

    private fun initializeRepositories() {
        printOrderRepository = PrintOrderRepository()
        shopSettingsRepository = ShopSettingsRepository()
        razorpayService = RazorpayService()
        userPreferencesRepository = UserPreferencesRepository(this)

    }

    private fun initializeViewModels() {
        mainViewModel = MainViewModel(shopSettingsRepository)
        homeViewModel = HomeViewModel(printOrderRepository, shopSettingsRepository)
        documentUploadViewModel = DocumentUploadViewModel(printOrderRepository, shopSettingsRepository)
        paymentViewModel = PaymentViewModel(printOrderRepository, razorpayService)
        customerInfoViewModel = CustomerInfoViewModel()
    }

    @Composable
    private fun PrintShopApp() {
        val navController = remember {
            NavHostController(this@MainActivity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
        }

        val customerId = remember { mutableStateOf("") }
        val customerPhone = remember { mutableStateOf("") }
        val startDestination = remember { mutableStateOf(Screen.CustomerInfo.route) }

        // Collect user preferences
        LaunchedEffect(Unit) {
            userPreferencesRepository.customerId.collect { id ->
                if (id.isNotEmpty()) {
                    customerId.value = id
                    homeViewModel.setCustomerId(id)
                    documentUploadViewModel.setCustomerInfo(id, customerPhone.value)

                    if (startDestination.value == Screen.CustomerInfo.route && customerPhone.value.isNotEmpty()) {
                        startDestination.value = Screen.Home.route
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            userPreferencesRepository.customerPhone.collect { phone ->
                if (phone.isNotEmpty()) {
                    customerPhone.value = phone
                    documentUploadViewModel.setCustomerInfo(customerId.value, phone)

                    if (startDestination.value == Screen.CustomerInfo.route && customerId.value.isNotEmpty()) {
                        startDestination.value = Screen.Home.route
                    }
                }
            }
        }

        PrintShopNavigation(
            navController = navController,
            mainViewModel = mainViewModel,
            homeViewModel = homeViewModel,
            documentUploadViewModel = documentUploadViewModel,
            paymentViewModel = paymentViewModel,
            customerInfoViewModel = customerInfoViewModel,
            activity = this@MainActivity,
            startDestination = startDestination.value
        )
    }

}