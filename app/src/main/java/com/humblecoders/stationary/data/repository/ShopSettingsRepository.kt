package com.humblecoders.stationary.data.repository


import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.humblecoders.stationary.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShopSettingsRepository(firestore : FirebaseFirestore) {
    private val settingsDoc = firestore.collection("shop_settings").document("default")

    fun observeShopSettings(): Flow<ShopSettings> {
        return settingsDoc.snapshots().map { snapshot ->
            if (snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as? Map<String, Any>
                Log.d("ShopSettingsRepo", "Raw Firestore data: ${data?.toString()}")
                
                // Get pricing map from Firestore
                val pricingMap = data?.get("pricing") as? Map<*, *>
                Log.d("ShopSettingsRepo", "Pricing map from Firestore: $pricingMap")
                
                // Try automatic mapping first
                val settings = try {
                    snapshot.toObject(ShopSettings::class.java) ?: ShopSettings()
                } catch (e: Exception) {
                    Log.e("ShopSettingsRepo", "Error mapping ShopSettings", e)
                    ShopSettings()
                }
                
                // If pricing map exists, manually extract values to ensure correct mapping
                if (pricingMap != null) {
                    val bwValue = (pricingMap["bw"] as? Number)?.toDouble()
                    val colorValue = (pricingMap["color"] as? Number)?.toDouble()
                    
                    // If we got values from Firestore, use them (even if auto-mapping worked)
                    if (bwValue != null && colorValue != null) {
                        val manualPricePerPage = com.humblecoders.stationary.data.model.PricePerPage(
                            bw = bwValue,
                            color = colorValue
                        )
                        Log.d("ShopSettingsRepo", "Using pricing from Firestore - BW: $bwValue, Color: $colorValue")
                        
                        // Create new ShopSettings with manually mapped pricing
                        ShopSettings(
                            shopId = settings.shopId,
                            shopOpen = settings.shopOpen,
                            autoPrintEnabled = settings.autoPrintEnabled,
                            pricePerPage = manualPricePerPage
                        )
                    } else {
                        Log.w("ShopSettingsRepo", "Pricing map exists but values are null")
                        settings
                    }
                } else {
                    Log.w("ShopSettingsRepo", "No pricing map found in Firestore data")
                    settings
                }
            } else {
                Log.w("ShopSettingsRepo", "Shop settings document does not exist")
                ShopSettings()
            }
        }
    }

    fun observeShopStatus(): Flow<Boolean> {
        return observeShopSettings().map { it.shopOpen }
    }
}
