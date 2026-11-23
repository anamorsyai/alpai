package com.anamorsyai.alpai.data.repository

import com.anamorsyai.alpai.data.model.AiModel
import com.anamorsyai.alpai.data.model.Provider
import com.anamorsyai.alpai.data.model.ProviderKind
import com.anamorsyai.alpai.data.model.defaultModelsFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Providers + their models. */
interface ProviderRepository {
    fun observeProviders(): Flow<List<Provider>>
    suspend fun addProvider(provider: Provider)
    suspend fun updateProvider(provider: Provider)
    suspend fun deleteProvider(providerId: String)
    suspend fun addModel(providerId: String, model: AiModel)
}

/**
 * In-memory implementation for v0.1.
 *
 * TODO: persist providers in Room (or Encrypted DataStore for API keys)
 * in the next step. Keys are therefore NOT persisted across restarts yet
 * — the UI is real, the storage is the placeholder. Never ship a release
 * that stores API keys in plain SharedPreferences / Room without
 * encryption; Encrypted DataStore is the planned home for them.
 */
class InMemoryProviderRepository : ProviderRepository {

    private val _providers = MutableStateFlow(
        listOf(
            Provider(
                name = "Demo (no key needed)",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "",
                apiKey = "",
                models = listOf(AiModel("demo-1", "Demo model")),
            ),
            Provider(
                name = "OpenAI",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1",
                models = defaultModelsFor(ProviderKind.OPENAI_COMPATIBLE),
            ),
            Provider(
                name = "OpenRouter",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://openrouter.ai/api/v1",
                models = listOf(
                    AiModel("openai/gpt-4o-mini"),
                    AiModel("deepseek/deepseek-chat"),
                    AiModel("meta-llama/llama-3.3-70b-instruct"),
                ),
            ),
        ),
    )

    override fun observeProviders(): Flow<List<Provider>> = _providers.asStateFlow()

    override suspend fun addProvider(provider: Provider) {
        _providers.update { it + provider.copy(models = provider.models.ifEmpty { defaultModelsFor(provider.kind) }) }
    }

    override suspend fun updateProvider(provider: Provider) {
        _providers.update { list -> list.map { if (it.id == provider.id) provider else it } }
    }

    override suspend fun deleteProvider(providerId: String) {
        _providers.update { list -> list.filterNot { it.id == providerId } }
    }

    override suspend fun addModel(providerId: String, model: AiModel) {
        _providers.update { list ->
            list.map { provider ->
                if (provider.id == providerId && provider.models.none { it.id == model.id }) {
                    provider.copy(models = provider.models + model)
                } else {
                    provider
                }
            }
        }
    }
}
