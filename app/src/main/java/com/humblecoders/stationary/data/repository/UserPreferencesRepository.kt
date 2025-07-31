// New file: UserPreferencesRepository.kt
package com.humblecoders.stationary.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    private val CUSTOMER_ID = stringPreferencesKey("customer_id")
    private val CUSTOMER_PHONE = stringPreferencesKey("customer_phone")

    val customerId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOMER_ID] ?: ""
    }

    val customerPhone: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOMER_PHONE] ?: ""
    }

    suspend fun saveCustomerInfo(id: String, phone: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOMER_ID] = id
            preferences[CUSTOMER_PHONE] = phone
        }
    }

    suspend fun clearCustomerInfo() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTOMER_ID)
            preferences.remove(CUSTOMER_PHONE)
        }
    }
}