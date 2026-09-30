package com.tobfd.tsuzuki.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App settings that belong to the device, not to AniList: colors, theme mode, pure black. Logout keeps them. */
@Singleton
class SettingsStore @Inject constructor(@SettingsPreferences private val dataStore: DataStore<Preferences>) {

    val appearance: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        AppearanceSettings(
            colors = enumOrNull<AppColors>(prefs[Keys.colors]) ?: AppColors.MaterialYou,
            themeMode = enumOrNull<AppThemeMode>(prefs[Keys.themeMode]) ?: AppThemeMode.System,
            pureBlack = prefs[Keys.pureBlack] ?: false
        )
    }

    suspend fun setColors(colors: AppColors) {
        dataStore.edit { it[Keys.colors] = colors.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[Keys.pureBlack] = enabled }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

    private object Keys {
        val colors = stringPreferencesKey("colors")
        val themeMode = stringPreferencesKey("theme_mode")
        val pureBlack = booleanPreferencesKey("pure_black")
    }
}
