package com.anamorsyai.alpai.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anamorsyai.alpai.data.model.Role
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String?,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String, // Role.name
    val content: String,
    val createdAt: Long,
    val attachmentNames: String = "", // comma-separated names, v0.1 only
)

fun MessageEntity.toRole(): Role = runCatching { Role.valueOf(role) }.getOrDefault(Role.USER)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChat(id: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastMessage(chatId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "alpai.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
