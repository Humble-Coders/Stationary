package com.humblecoders.stationary.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.ShopSettings
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val shopSettings: ShopSettings = ShopSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class MainViewModel(
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeShopSettings()
    }

    private fun observeShopSettings() {
        viewModelScope.launch {
            try {
                shopSettingsRepository.observeShopSettings().collect { settings ->
                    _uiState.value = _uiState.value.copy(
                        shopSettings = settings,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}



