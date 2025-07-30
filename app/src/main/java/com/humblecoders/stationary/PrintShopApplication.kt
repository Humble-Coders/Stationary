package com.humblecoders.stationary

// PrintShopApplication.kt

import android.app.Application
import com.google.firebase.FirebaseApp

class PrintShopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}