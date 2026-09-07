package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "study_aid_settings")

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val QUESTION_TYPE = stringPreferencesKey("question_type")
        val QUESTION_COUNT = intPreferencesKey("question_count")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val defaultKey = try {
            BuildConfig.GEMINI_API_KEY.ifBlank { "" }
        } catch (_: Exception) {
            ""
        }
        val storedKey = preferences[PreferencesKeys.API_KEY] ?: defaultKey
        val model = preferences[PreferencesKeys.SELECTED_MODEL] ?: "gemini-3.5-flash"
        val qType = preferences[PreferencesKeys.QUESTION_TYPE] ?: "MIXED"
        val qCount = preferences[PreferencesKeys.QUESTION_COUNT] ?: 5

        AppSettings(
            apiKey = storedKey,
            selectedModel = model,
            questionType = qType,
            questionCount = qCount
        )
    }

    suspend fun updateApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    suspend fun updateQuestionType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUESTION_TYPE] = type
        }
    }

    suspend fun updateQuestionCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUESTION_COUNT] = count
        }
    }
}
