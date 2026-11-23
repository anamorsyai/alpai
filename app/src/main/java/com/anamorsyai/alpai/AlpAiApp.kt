package com.anamorsyai.alpai

import android.app.Application
import com.anamorsyai.alpai.data.local.AppDatabase
import com.anamorsyai.alpai.data.network.ChatApiFactory
import com.anamorsyai.alpai.data.repository.InMemoryProviderRepository
import com.anamorsyai.alpai.data.repository.RoomChatRepository
import com.anamorsyai.alpai.data.repository.SettingsRepository

/**
 * Simple manual dependency graph for v0.1.
 *
 * No DI framework yet on purpose: the graph is small enough to wire by
 * hand, and it keeps the scaffold easy to read. If it grows past a
 * handful of repositories, move this to Hilt.
 */
class AlpAiApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val chatRepository: RoomChatRepository by lazy {
        RoomChatRepository(database.chatDao(), database.messageDao())
    }

    val providerRepository: InMemoryProviderRepository by lazy {
        InMemoryProviderRepository()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val chatApiFactory: ChatApiFactory by lazy { ChatApiFactory() }
}
