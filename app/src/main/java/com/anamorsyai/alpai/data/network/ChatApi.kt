package com.anamorsyai.alpai.data.network

import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Provider
import com.anamorsyai.alpai.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json as KotlinJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// ---- OpenAI-compatible wire format (/chat/completions, SSE) ----

@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
)

@Serializable
data class StreamDelta(val content: String? = null)

@Serializable
data class StreamChoice(val delta: StreamDelta = StreamDelta())

@Serializable
data class StreamChunk(val choices: List<StreamChoice> = emptyList())

private val json = KotlinJson { ignoreUnknownKeys = true }

/** Builds the request body sent to an OpenAI-compatible endpoint.
 *  Kept separate so unit tests can check the JSON without networking. */
fun buildChatCompletionRequest(modelId: String, history: List<ChatMessage>): ChatCompletionRequest {
    val messages = history.map { message ->
        ApiMessage(
            role = when (message.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.SYSTEM -> "system"
            },
            content = message.content,
        )
    }
    return ChatCompletionRequest(model = modelId, messages = messages)
}

fun serializeRequest(request: ChatCompletionRequest): String =
    KotlinJson.encodeToString(request)

/** A chat backend that streams reply text chunk by chunk. */
interface ChatApi {
    fun streamReply(
        provider: Provider,
        modelId: String,
        history: List<ChatMessage>,
    ): Flow<String>
}

/** Real OpenAI-compatible client over OkHttp SSE. */
class OpenAiCompatibleChatApi(
    private val client: OkHttpClient = OkHttpClient(),
) : ChatApi {

    override fun streamReply(
        provider: Provider,
        modelId: String,
        history: List<ChatMessage>,
    ): Flow<String> = flow {
        val body = serializeRequest(buildChatCompletionRequest(modelId, history))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Provider error: HTTP ${response.code}")
            }
            val source = response.body?.source()
                ?: throw IllegalStateException("Empty response from provider")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val chunk = runCatching { json.decodeFromString<StreamChunk>(payload) }.getOrNull()
                val text = chunk?.choices?.firstOrNull()?.delta?.content
                if (!text.isNullOrEmpty()) emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)
}

/** Demo backend: no key, no network — lets the whole UI run out of the box. */
class DemoChatApi : ChatApi {
    override fun streamReply(
        provider: Provider,
        modelId: String,
        history: List<ChatMessage>,
    ): Flow<String> = flow {
        val lastUser = history.lastOrNull { it.role == Role.USER }?.content.orEmpty()
        val reply = "This is the AlpAI demo model — no API key needed.\n\n" +
            "You said: \"$lastUser\"\n\n" +
            "Add a provider in Providers (OpenAI, OpenRouter, DeepSeek, …) " +
            "and pick a real model to get live answers."
        // Emit word by word so the streaming UI is visible.
        for (word in reply.split(" ")) {
            emit("$word ")
            delay(35)
        }
    }
}

/** Picks the backend for a provider. Demo provider (empty base URL) -> demo. */
class ChatApiFactory(
    private val real: ChatApi = OpenAiCompatibleChatApi(),
    private val demo: ChatApi = DemoChatApi(),
) {
    fun create(provider: Provider): ChatApi =
        if (provider.baseUrl.isBlank()) demo else real
}
