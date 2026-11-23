package com.anamorsyai.alpai.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anamorsyai.alpai.data.model.AiModel
import com.anamorsyai.alpai.data.model.Provider
import com.anamorsyai.alpai.data.model.ProviderKind
import com.anamorsyai.alpai.data.repository.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(
    private val repository: ProviderRepository,
) : ViewModel() {

    val providers: StateFlow<List<Provider>> = repository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addProvider(name: String, baseUrl: String, apiKey: String, kind: ProviderKind) {
        viewModelScope.launch {
            repository.addProvider(
                Provider(name = name, baseUrl = baseUrl, apiKey = apiKey, kind = kind),
            )
        }
    }

    fun updateProvider(provider: Provider) {
        viewModelScope.launch { repository.updateProvider(provider) }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch { repository.deleteProvider(providerId) }
    }

    fun addModel(providerId: String, modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addModel(providerId, AiModel(id = trimmed, isCustom = true))
        }
    }
}

class ProvidersViewModelFactory(
    private val repository: ProviderRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProvidersViewModel::class.java)) {
            return ProvidersViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
