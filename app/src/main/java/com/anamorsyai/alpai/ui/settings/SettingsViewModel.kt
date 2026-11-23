package com.anamorsyai.alpai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anamorsyai.alpai.data.repository.ChatRepository
import com.anamorsyai.alpai.data.repository.SettingsRepository
import com.anamorsyai.alpai.data.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val fontScale: StateFlow<Float> = settings.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val streamingEnabled: StateFlow<Boolean> = settings.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val defaultModel: StateFlow<String> = settings.defaultModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "demo-1")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settings.setFontScale(scale) }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setStreamingEnabled(enabled) }
    }

    fun setDefaultModel(modelId: String) {
        viewModelScope.launch { settings.setDefaultModel(modelId) }
    }

    fun clearAllChats() {
        viewModelScope.launch { chatRepository.clearAll() }
    }
}

class SettingsViewModelFactory(
    private val settings: SettingsRepository,
    private val chatRepository: ChatRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settings, chatRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
