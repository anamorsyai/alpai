package com.anamorsyai.alpai.data.model

import java.util.UUID

/** Who sent a message. */
enum class Role { USER, ASSISTANT, SYSTEM }

/** A chat message. `isStreaming` marks the in-progress assistant reply. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val role: Role,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList(),
    val isStreaming: Boolean = false,
)

/** A file / photo attached from the + sheet. v0.1 keeps the name + URI;
 *  parsing file contents for the model is on the roadmap. */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String,
    val mimeType: String? = null,
)

/** One conversation in the chat list. */
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelId: String? = null,
)

/** What kind of API a provider speaks. v0.1 only implements OpenAI-compatible. */
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    GEMINI,
    ANTHROPIC,
}

/** A configured AI provider: name + base URL + API key. */
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val models: List<AiModel> = emptyList(),
)

/** A model offered by a provider. `isCustom` = typed in by hand. */
data class AiModel(
    val id: String,
    val displayName: String = id,
    val isCustom: Boolean = false,
)

/** Well-known OpenAI-compatible models offered as one-tap presets. */
fun defaultModelsFor(kind: ProviderKind): List<AiModel> = when (kind) {
    ProviderKind.OPENAI_COMPATIBLE -> listOf(
        AiModel("gpt-4o-mini"),
        AiModel("gpt-4o"),
        AiModel("deepseek-chat"),
        AiModel("llama-3.3-70b-versatile"),
    )
    ProviderKind.GEMINI -> listOf(
        AiModel("gemini-2.0-flash"),
        AiModel("gemini-1.5-pro"),
    )
    ProviderKind.ANTHROPIC -> listOf(
        AiModel("claude-3-5-sonnet-latest"),
        AiModel("claude-3-haiku-20240307"),
    )
}

/** A short prompt preset ("agent") the user can start a chat with. */
data class AgentPreset(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val emoji: String,
)

val DEFAULT_AGENTS = listOf(
    AgentPreset(
        id = "general",
        name = "General",
        systemPrompt = "You are AlpAI, a helpful assistant.",
        emoji = "✦",
    ),
    AgentPreset(
        id = "coder",
        name = "Coder",
        systemPrompt = "You are an expert Kotlin/Android developer. Answer with clean, idiomatic code and explain the tricky parts.",
        emoji = "⌨",
    ),
    AgentPreset(
        id = "summarizer",
        name = "Summarizer",
        systemPrompt = "Summarize clearly and briefly, keeping every important point. Use bullet lists.",
        emoji = "☰",
    ),
)
