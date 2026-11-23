package com.anamorsyai.alpai.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alpai_settings")

/** Real, persisted user settings (DataStore Preferences). */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontScaleKey = floatPreferencesKey("font_scale")
    private val streamingKey = booleanPreferencesKey("streaming_enabled")
    private val defaultModelKey = stringPreferencesKey("default_model")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val fontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[fontScaleKey] ?: 1.0f
    }

    val streamingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[streamingKey] ?: true
    }

    val defaultModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[defaultModelKey] ?: "demo-1"
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[fontScaleKey] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[streamingKey] = enabled }
    }

    suspend fun setDefaultModel(modelId: String) {
        context.dataStore.edit { it[defaultModelKey] = modelId }
    }
}
