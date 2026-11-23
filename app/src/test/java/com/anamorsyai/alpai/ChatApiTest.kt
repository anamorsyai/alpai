package com.anamorsyai.alpai

import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Role
import com.anamorsyai.alpai.data.network.buildChatCompletionRequest
import com.anamorsyai.alpai.data.network.serializeRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.anamorsyai.alpai.data.network.DemoChatApi
import com.anamorsyai.alpai.data.model.Provider

class ChatApiTest {

    @Test
    fun `request serializes model and messages`() {
        val request = buildChatCompletionRequest(
            modelId = "gpt-4o-mini",
            history = listOf(
                ChatMessage(chatId = "c1", role = Role.USER, content = "hello"),
            ),
        )
        val json = serializeRequest(request)
        assertTrue(json.contains("gpt-4o-mini"))
        assertTrue(json.contains("hello"))
        assertTrue(json.contains("\"role\":\"user\""))
    }

    @Test
    fun `demo api streams a non-empty reply`() = runBlocking {
        val api = DemoChatApi()
        val chunks = api.streamReply(
            provider = Provider(name = "Demo", baseUrl = ""),
            modelId = "demo-1",
            history = listOf(
                ChatMessage(chatId = "c1", role = Role.USER, content = "hi"),
            ),
        ).toList()
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.joinToString("").contains("hi"))
    }

    @Test
    fun `request defaults to streaming`() {
        val request = buildChatCompletionRequest("demo-1", emptyList())
        assertEquals(true, request.stream)
        assertTrue(request.messages.isEmpty())
    }
}
