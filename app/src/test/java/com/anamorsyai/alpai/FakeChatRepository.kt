package com.anamorsyai.alpai

import com.anamorsyai.alpai.data.model.Chat
import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [ChatRepository] for tests — no Room, no disk. */
class FakeChatRepository(
    initialChats: List<Chat> = emptyList(),
    initialMessages: Map<String, List<ChatMessage>> = emptyMap(),
) : ChatRepository {

    private val chatsFlow = MutableStateFlow(initialChats)
    private val messagesFlow = MutableStateFlow(initialMessages)

    override fun observeChats(): Flow<List<Chat>> = chatsFlow.asStateFlow()

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        messagesFlow.map { it[chatId].orEmpty() }

    override suspend fun createChat(title: String, modelId: String?): Chat {
        val chat = Chat(title = title.take(60), modelId = modelId)
        chatsFlow.update { listOf(chat) + it }
        return chat
    }

    override suspend fun saveMessage(message: ChatMessage) {
        messagesFlow.update { current ->
            current + (message.chatId to (current[message.chatId].orEmpty() + message))
        }
    }

    override suspend fun deleteChat(chatId: String) {
        chatsFlow.update { list -> list.filterNot { it.id == chatId } }
        messagesFlow.update { it - chatId }
    }

    override suspend fun clearAll() {
        chatsFlow.value = emptyList()
        messagesFlow.value = emptyMap()
    }
}
