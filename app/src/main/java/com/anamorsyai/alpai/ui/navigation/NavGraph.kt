package com.anamorsyai.alpai.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anamorsyai.alpai.AlpAiApp
import com.anamorsyai.alpai.ui.chat.ChatListScreen
import com.anamorsyai.alpai.ui.chat.ChatScreen
import com.anamorsyai.alpai.ui.chat.ChatViewModel
import com.anamorsyai.alpai.ui.chat.ChatViewModelFactory
import com.anamorsyai.alpai.ui.providers.ProvidersScreen
import com.anamorsyai.alpai.ui.providers.ProvidersViewModel
import com.anamorsyai.alpai.ui.providers.ProvidersViewModelFactory
import com.anamorsyai.alpai.ui.settings.SettingsScreen
import com.anamorsyai.alpai.ui.settings.SettingsViewModel
import com.anamorsyai.alpai.ui.settings.SettingsViewModelFactory
import com.anamorsyai.alpai.ui.terminal.TerminalScreen

object Routes {
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat/{chatId}"
    const val PROVIDERS = "providers"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal"

    fun chat(chatId: String?) = if (chatId == null) CHAT else "chat/$chatId"
}

private data class TopLevel(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevels = listOf(
    TopLevel(Routes.CHAT_LIST, "Chats", Icons.Filled.Chat),
    TopLevel(Routes.PROVIDERS, "Providers", Icons.Filled.Storage),
    TopLevel(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AlpAiNavHost(app: AlpAiApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevels.any { top ->
        currentDestination?.hierarchy?.any { it.route == top.route } == true
    }

    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(app.chatRepository, app.providerRepository, app.chatApiFactory),
    )
    val providersViewModel: ProvidersViewModel = viewModel(
        factory = ProvidersViewModelFactory(app.providerRepository),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.settingsRepository, app.chatRepository),
    )
    val fontScale by settingsViewModel.fontScale.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { top ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == top.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, contentDescription = top.label) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT_LIST,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.CHAT_LIST) {
                ChatListScreen(
                    viewModel = chatViewModel,
                    onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                route = Routes.CHAT_WITH_ID,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                ChatScreen(
                    chatId = entry.arguments?.getString("chatId"),
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                    fontScale = fontScale,
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    chatId = null,
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                    fontScale = fontScale,
                )
            }
            composable(Routes.PROVIDERS) {
                ProvidersScreen(viewModel = providersViewModel)
            }
            composable(Routes.TERMINAL) {
                TerminalScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
