package com.humblecoders.stationary.data.repository


import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.humblecoders.stationary.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShopSettingsRepository(private val firestore: FirebaseFirestore) {
    
    fun observeShopSettings(shopId: String): Flow<ShopSettings> {
        val settingsDoc = firestore.collection("shop_settings").document(shopId)
        return settingsDoc.snapshots().map { snapshot ->
            if (snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as? Map<String, Any>
                Log.d("ShopSettingsRepo", "Raw Firestore data for $shopId: ${data?.toString()}")
                
                // Manually extract isShopOpen boolean from Firestore
                val isShopOpen = data?.get("isShopOpen") as? Boolean ?: true
                Log.d("ShopSettingsRepo", "isShopOpen for $shopId: $isShopOpen")
                
                // Get pricing map from Firestore
                val pricingMap = data?.get("pricing") as? Map<*, *>
                Log.d("ShopSettingsRepo", "Pricing map from Firestore: $pricingMap")
                
                // Extract pricing values
                val bwValue = (pricingMap?.get("bw") as? Number)?.toDouble() ?: 2.0
                val colorValue = (pricingMap?.get("color") as? Number)?.toDouble() ?: 5.0
                
                val pricePerPage = com.humblecoders.stationary.data.model.PricePerPage(
                    bw = bwValue,
                    color = colorValue
                )
                Log.d("ShopSettingsRepo", "Pricing for $shopId - BW: $bwValue, Color: $colorValue")
                
                // Create ShopSettings with manually extracted values
                ShopSettings(
                    shopId = shopId,
                    shopOpen = isShopOpen,
                    pricePerPage = pricePerPage
                )
            } else {
                Log.w("ShopSettingsRepo", "Shop settings document does not exist for $shopId")
                ShopSettings(shopId = shopId)
            }
        }
    }

    fun observeShopStatus(shopId: String): Flow<Boolean> {
        return observeShopSettings(shopId).map { it.shopOpen }
    }
}
