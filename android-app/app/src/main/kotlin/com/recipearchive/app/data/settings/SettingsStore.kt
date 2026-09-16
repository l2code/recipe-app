package com.recipearchive.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * On-device app preferences: theme choice and whether the main nav shows text labels under its
 * icons. Backed by plain SharedPreferences (not encrypted -- nothing sensitive here), with a
 * StateFlow per setting so the nav rail and theme root recompose immediately when either changes
 * from the Settings screen.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _showNavLabels = MutableStateFlow(prefs.getBoolean(KEY_SHOW_NAV_LABELS, true))
    val showNavLabels: StateFlow<Boolean> = _showNavLabels.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setShowNavLabels(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NAV_LABELS, show).apply()
        _showNavLabels.value = show
    }

    private fun readThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_NAV_LABELS = "show_nav_labels"
    }
}
