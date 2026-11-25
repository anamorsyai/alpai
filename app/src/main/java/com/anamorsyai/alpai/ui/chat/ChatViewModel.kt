package com.anamorsyai.alpai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anamorsyai.alpai.data.model.Attachment
import com.anamorsyai.alpai.data.model.Chat
import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Provider
import com.anamorsyai.alpai.data.model.Role
import com.anamorsyai.alpai.data.network.ChatApiFactory
import com.anamorsyai.alpai.data.repository.ChatRepository
import com.anamorsyai.alpai.data.repository.ProviderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val chatApiFactory: ChatApiFactory,
) : ViewModel() {

    val chats: StateFlow<List<Chat>> = chatRepository.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val providers: StateFlow<List<Provider>> = providerRepository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    val selectedProvider: StateFlow<Provider?> = combine(providers, _selectedProviderId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull { it.enabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedModelId = MutableStateFlow("demo-1")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    /** The assistant reply currently streaming (not yet saved to Room). */
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** Set when a brand-new chat is created by sending the first message,
     *  so the UI can navigate to chat/{id}. Consumed once by the screen. */
    private val _createdChatId = MutableStateFlow<String?>(null)
    val createdChatId: StateFlow<String?> = _createdChatId.asStateFlow()

    /** Last deleted chat + its messages, held briefly so the UI can offer Undo.
     *  Only the most recent delete is kept: deleting a second chat while the
     *  first undo is still showing replaces it. */
    data class DeletedChat(val chat: Chat, val messages: List<ChatMessage>)

    private val _deletedChat = MutableStateFlow<DeletedChat?>(null)
    val deletedChat: StateFlow<DeletedChat?> = _deletedChat.asStateFlow()

    private var generationJob: Job? = null

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        chatRepository.observeMessages(chatId)

    fun selectProvider(providerId: String, modelId: String? = null) {
        _selectedProviderId.value = providerId
        val provider = providers.value.firstOrNull { it.id == providerId }
        val models = provider?.models.orEmpty()
        // Never pair a provider with a model it does not offer (e.g. OpenAI with
        // the Demo provider's "demo-1"): a stale or foreign model id falls back to
        // the new provider's first model instead of being sent to the wrong backend.
        _selectedModelId.value = modelId?.takeIf { id -> models.any { it.id == id } }
            ?: models.firstOrNull()?.id
            ?: _selectedModelId.value
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
    }

    fun consumeCreatedChatId() {
        _createdChatId.value = null
    }

    fun sendMessage(chatId: String?, text: String, attachments: List<Attachment> = emptyList()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return
        generationJob = viewModelScope.launch {
            val chat = if (chatId == null) {
                chatRepository.createChat(
                    title = trimmed,
                    modelId = _selectedModelId.value,
                ).also { _createdChatId.value = it.id }
            } else {
                chats.value.firstOrNull { it.id == chatId }
                    ?: chatRepository.createChat(title = trimmed, modelId = _selectedModelId.value)
            }
            val userMessage = ChatMessage(
                chatId = chat.id,
                role = Role.USER,
                content = trimmed,
                attachments = attachments,
            )
            chatRepository.saveMessage(userMessage)
            streamAssistantReply(chat)
        }
    }

    /** Streams the assistant reply for [chat] and saves it when done. */
    private fun streamAssistantReply(chat: Chat) {
        generationJob = viewModelScope.launch {
            val provider = selectedProvider.value
                ?: providers.value.firstOrNull()
                ?: return@launch
            val assistantMessage = ChatMessage(chatId = chat.id, role = Role.ASSISTANT, content = "")
            // A real (non-Demo) provider with no API key can never answer: say so
            // plainly instead of sending a key-less request and surfacing the
            // provider's cryptic HTTP 401. (Demo has an empty base URL and needs no key.)
            if (provider.baseUrl.isNotBlank() && provider.apiKey.isBlank()) {
                chatRepository.saveMessage(
                    assistantMessage.copy(
                        content = "No API key set for ${provider.name} — add one in " +
                            "Providers first, then send again. (Demo needs no key.)",
                    ),
                )
                return@launch
            }
            val api = chatApiFactory.create(provider)
            val history = chatRepository.observeMessages(chat.id).first()
            _streamingMessage.value = assistantMessage
            _isGenerating.value = true
            var accumulated = ""
            try {
                api.streamReply(provider, _selectedModelId.value, history).collect { chunk ->
                    accumulated += chunk
                    _streamingMessage.value = assistantMessage.copy(
                        content = accumulated,
                        isStreaming = true,
                    )
                }
            } catch (e: Exception) {
                accumulated += "\n\n⚠ Error: ${e.message ?: "provider failed"}"
            } finally {
                chatRepository.saveMessage(
                    assistantMessage.copy(content = accumulated.ifBlank { "(empty reply)" }),
                )
                _streamingMessage.value = null
                _isGenerating.value = false
            }
        }
    }

    fun regenerate(chatId: String) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val history = chatRepository.observeMessages(chatId).first()
            if (history.none { it.role == Role.USER }) return@launch
            val chat = chats.value.firstOrNull { it.id == chatId } ?: return@launch
            streamAssistantReply(chat)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        // Persist whatever streamed so far, if anything.
        val partial = _streamingMessage.value
        if (partial != null && partial.content.isNotBlank()) {
            viewModelScope.launch { chatRepository.saveMessage(partial.copy(isStreaming = false)) }
        }
        _streamingMessage.value = null
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            val chat = chats.value.firstOrNull { it.id == chatId } ?: return@launch
            val messages = chatRepository.observeMessages(chatId).first()
            chatRepository.deleteChat(chatId)
            _deletedChat.value = DeletedChat(chat, messages)
        }
    }

    /** Restores the last deleted chat (Snackbar Undo). No-op if nothing to restore. */
    fun restoreDeletedChat() {
        val deleted = _deletedChat.value ?: return
        _deletedChat.value = null
        viewModelScope.launch { chatRepository.restoreChat(deleted.chat, deleted.messages) }
    }

    /** Clears the pending undo without restoring (e.g. the Snackbar timed out). */
    fun consumeDeletedChat() {
        _deletedChat.value = null
    }
}

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val chatApiFactory: ChatApiFactory,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(chatRepository, providerRepository, chatApiFactory) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
