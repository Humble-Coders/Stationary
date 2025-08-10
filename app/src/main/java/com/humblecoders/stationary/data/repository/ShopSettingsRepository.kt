package com.humblecoders.stationary.data.repository


import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.humblecoders.stationary.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShopSettingsRepository(firestore : FirebaseFirestore) {
    private val settingsDoc = firestore.collection("shop_settings").document("default")

    fun observeShopSettings(): Flow<ShopSettings> {
        return settingsDoc.snapshots().map { snapshot ->
            snapshot.toObject(ShopSettings::class.java) ?: ShopSettings()
        }
    }

    fun observeShopStatus(): Flow<Boolean> {
        return observeShopSettings().map { it.shopOpen }
    }
}
