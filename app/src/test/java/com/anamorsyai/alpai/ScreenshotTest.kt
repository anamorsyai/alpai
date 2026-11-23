package com.anamorsyai.alpai

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.anamorsyai.alpai.data.model.Chat
import com.anamorsyai.alpai.data.model.ChatMessage
import com.anamorsyai.alpai.data.model.Role
import com.anamorsyai.alpai.data.network.ChatApiFactory
import com.anamorsyai.alpai.data.repository.InMemoryProviderRepository
import com.anamorsyai.alpai.data.repository.SettingsRepository
import com.anamorsyai.alpai.data.repository.ThemeMode
import com.anamorsyai.alpai.ui.chat.ChatListScreen
import com.anamorsyai.alpai.ui.chat.ChatScreen
import com.anamorsyai.alpai.ui.chat.ChatViewModel
import com.anamorsyai.alpai.ui.components.MessageBubble
import com.anamorsyai.alpai.ui.providers.ProvidersScreen
import com.anamorsyai.alpai.ui.providers.ProvidersViewModel
import com.anamorsyai.alpai.ui.settings.SettingsScreen
import com.anamorsyai.alpai.ui.settings.SettingsViewModel
import com.anamorsyai.alpai.ui.theme.AlpAiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Screenshot tests (Paparazzi, JVM — no emulator, no device).
 *
 * Every snapshot renders at Pixel 5 size in dark mode, matching the phone the
 * app was tested on. Goldens live in app/src/test/snapshots/images/ and are
 * recorded with `./gradlew recordPaparazziDebug`, verified with
 * `./gradlew verifyPaparazziDebug` (and inside `testDebugUnitTest` in CI).
 *
 * Screens are rendered through their real ViewModels with fakes behind them:
 * [FakeChatRepository] for chats/messages, the real [InMemoryProviderRepository]
 * (it is already the production seed data) for providers, and a mocked
 * [SettingsRepository] for settings (it is a concrete DataStore-backed class
 * with no interface or fake — see the settings test).
 *
 * NOT covered here (no headless seam exists — see the notes in the report):
 * the Terminal screen, the "Choose provider & model" picker dialog, the "+"
 * attach sheet, and the provider editor dialog. Those are driven by internal
 * state or by native code and cannot be rendered on the JVM as written.
 */
class ScreenshotTest {

    // Dark, like the test phone (system dark theme).
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = DeviceConfig.NightMode.NIGHT),
    )

    // ViewModels run on Dispatchers.Main (viewModelScope + stateIn). Swap it for
    // an unconfined test dispatcher so their flows start eagerly on the JVM.
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Fixed timestamps so the chat rows render the same time on every run
    // (ChatRow formats updatedAt with the default locale/timezone).
    private val t1 = 1_766_000_000_000L // 2025-12-16 19:33 UTC
    private val t2 = 1_766_000_600_000L // 10 minutes later

    private fun chatViewModel(
        chats: List<Chat> = emptyList(),
        messages: Map<String, List<ChatMessage>> = emptyMap(),
    ) = ChatViewModel(
        FakeChatRepository(chats, messages),
        InMemoryProviderRepository(),
        ChatApiFactory(),
    )

    @Test
    fun chatListEmpty() {
        paparazzi.snapshot(name = "chat_list_empty") {
            AlpAiTheme {
                ChatListScreen(
                    viewModel = chatViewModel(),
                    onOpenChat = {},
                    onOpenSettings = {},
                )
            }
        }
    }

    @Test
    fun chatListWithChats() {
        val chats = listOf(
            Chat(
                id = "chat-1",
                title = "Explain the variational principle",
                modelId = "gpt-4o-mini",
                createdAt = t1,
                updatedAt = t2,
            ),
            Chat(
                id = "chat-2",
                title = "jk",
                modelId = "demo-1",
                createdAt = t1,
                updatedAt = t1,
            ),
        )
        paparazzi.snapshot(name = "chat_list_with_chats") {
            AlpAiTheme {
                ChatListScreen(
                    viewModel = chatViewModel(chats = chats),
                    onOpenChat = {},
                    onOpenSettings = {},
                )
            }
        }
    }

    @Test
    fun chatNewEmpty() {
        paparazzi.snapshot(name = "chat_new_empty") {
            AlpAiTheme {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides FakeActivityResultRegistryOwner(),
                ) {
                    ChatScreen(chatId = null, viewModel = chatViewModel(), onBack = {})
                }
            }
        }
    }

    @Test
    fun chatWithMessages() {
        val chatId = "chat-1"
        val chats = listOf(
            Chat(
                id = chatId,
                title = "What is the variational principle?",
                modelId = "gpt-4o-mini",
                createdAt = t1,
                updatedAt = t2,
            ),
        )
        val messages = mapOf(
            chatId to listOf(
                ChatMessage(
                    chatId = chatId,
                    role = Role.USER,
                    content = "What is the variational principle?",
                ),
                ChatMessage(
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = "The **variational principle** says the ground-state energy is " +
                        "the minimum of ⟨ψ|H|ψ⟩ over all trial states — try `E[ψ]` as an example.",
                ),
                ChatMessage(chatId = chatId, role = Role.USER, content = "jk"),
                ChatMessage(
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = "⚠ Error: Provider error: HTTP 401",
                ),
            ),
        )
        paparazzi.snapshot(name = "chat_with_messages") {
            AlpAiTheme {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides FakeActivityResultRegistryOwner(),
                ) {
                    ChatScreen(
                        chatId = chatId,
                        viewModel = chatViewModel(chats, messages),
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun messageBubbles() {
        paparazzi.snapshot(name = "message_bubbles") {
            AlpAiTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MessageBubble(
                        ChatMessage(chatId = "c", role = Role.USER, content = "Hello AlpAI"),
                    )
                    MessageBubble(
                        ChatMessage(
                            chatId = "c",
                            role = Role.ASSISTANT,
                            content = "Hello! How can I help?",
                        ),
                    )
                    MessageBubble(
                        ChatMessage(
                            chatId = "c",
                            role = Role.ASSISTANT,
                            content = "⚠ Error: Provider error: HTTP 401",
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun providers() {
        paparazzi.snapshot(name = "providers") {
            AlpAiTheme {
                ProvidersScreen(viewModel = ProvidersViewModel(InMemoryProviderRepository()))
            }
        }
    }

    @Test
    fun settings() {
        // SettingsRepository is a concrete class over DataStore (needs a real
        // Context) with no interface or fake — mock it and feed the same values
        // the real defaults produce, so the snapshot shows the real screen.
        val settings = mock<SettingsRepository> {
            on { themeMode } doReturn flowOf(ThemeMode.DARK)
            on { fontScale } doReturn flowOf(1.0f)
            on { streamingEnabled } doReturn flowOf(true)
            on { defaultModel } doReturn flowOf("demo-1")
        }
        paparazzi.snapshot(name = "settings") {
            AlpAiTheme {
                SettingsScreen(
                    viewModel = SettingsViewModel(settings, FakeChatRepository()),
                )
            }
        }
    }
}

/**
 * Paparazzi renders without an Activity, so ChatScreen's activity-result
 * launchers (file / photo / camera pickers) would crash on the missing
 * registry owner. The launchers are never fired during a snapshot — this stub
 * only lets composition succeed.
 */
private class FakeActivityResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                // Never launched in a snapshot.
            }
        }
}
