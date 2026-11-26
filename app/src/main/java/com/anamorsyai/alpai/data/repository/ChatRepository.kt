package com.anamorsyai.alpai.data.repository

import com.anamorsyai.alpai.data.local.ChatDao
import com.anamorsyai.alpai.data.local.ChatEntity
import com.anamorsyai.alpai.data.local.MessageDao
import com.anamorsyai.alpai.data.local.MessageEntity
import com.anamorsyai.alpai.data.local.toRole
import com.anamorsyai.alpai.data.model.Attachment
import com.anamorsyai.alpai.data.model.Chat
import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persistence for chats + messages. Room-backed in v0.1. */
interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>
    suspend fun createChat(title: String, modelId: String?): Chat
    suspend fun saveMessage(message: ChatMessage)
    suspend fun deleteChat(chatId: String)
    /** Re-inserts a previously deleted chat with its messages (undo). */
    suspend fun restoreChat(chat: Chat, messages: List<ChatMessage>)
    suspend fun clearAll()
}

class RoomChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
) : ChatRepository {

    override fun observeChats(): Flow<List<Chat>> =
        chatDao.observeChats().map { list ->
            list.map {
                Chat(
                    id = it.id,
                    title = it.title,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    modelId = it.modelId,
                )
            }
        }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(chatId).map { list ->
            list.map {
                ChatMessage(
                    id = it.id,
                    chatId = it.chatId,
                    role = it.toRole(),
                    content = it.content,
                    createdAt = it.createdAt,
                    attachments = it.attachmentNames
                        .split(",")
                        .filter { name -> name.isNotBlank() }
                        .map { name -> Attachment(name = name, uri = "") },
                )
            }
        }

    override suspend fun createChat(title: String, modelId: String?): Chat {
        val chat = Chat(title = title.take(60), modelId = modelId)
        chatDao.upsert(
            ChatEntity(
                id = chat.id,
                title = chat.title,
                createdAt = chat.createdAt,
                updatedAt = chat.updatedAt,
                modelId = chat.modelId,
            ),
        )
        return chat
    }

    override suspend fun saveMessage(message: ChatMessage) {
        messageDao.upsert(
            MessageEntity(
                id = message.id,
                chatId = message.chatId,
                role = message.role.name,
                content = message.content,
                createdAt = message.createdAt,
                attachmentNames = message.attachments.joinToString(",") { it.name },
            ),
        )
        // Keep the chat list ordered by latest activity.
        chatDao.getChat(message.chatId)?.let { chat ->
            chatDao.upsert(chat.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun deleteChat(chatId: String) {
        messageDao.deleteMessagesForChat(chatId)
        chatDao.deleteChat(chatId)
    }

    override suspend fun restoreChat(chat: Chat, messages: List<ChatMessage>) {
        chatDao.upsert(
            ChatEntity(
                id = chat.id,
                title = chat.title,
                createdAt = chat.createdAt,
                updatedAt = chat.updatedAt,
                modelId = chat.modelId,
            ),
        )
        messages.forEach { message ->
            messageDao.upsert(
                MessageEntity(
                    id = message.id,
                    chatId = message.chatId,
                    role = message.role.name,
                    content = message.content,
                    createdAt = message.createdAt,
                    attachmentNames = message.attachments.joinToString(",") { it.name },
                ),
            )
        }
    }

    override suspend fun clearAll() {
        messageDao.deleteAllMessages()
        chatDao.deleteAllChats()
    }
}
