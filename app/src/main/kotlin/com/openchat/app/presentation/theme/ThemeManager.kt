package com.openchat.app.presentation.theme
import android.content.Context
import android.content.SharedPreferences
import com.openchat.app.presentation.screens.settings.FontSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
enum class ThemeMode { LIGHT, DARK, SYSTEM }
@Singleton
class ThemeManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name))
    val themeMode: StateFlow<ThemeMode> = _themeMode
    private val _isDarkMode = MutableStateFlow(resolveDark(_themeMode.value, context))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode
    private val _fontSize = MutableStateFlow(FontSize.entries.find { it.scale == prefs.getFloat("font_scale", 1.0f) } ?: FontSize.MEDIUM)
    val fontSize: StateFlow<FontSize> = _fontSize
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        _isDarkMode.value = resolveDark(mode, context)
        prefs.edit().putString("theme_mode", mode.name).apply()
    }
    fun setDarkMode(enabled: Boolean) = setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    fun setFontSize(size: FontSize) {
        _fontSize.value = size
        prefs.edit().putFloat("font_scale", size.scale).apply()
    }
    fun refreshSystemTheme(isSystemDark: Boolean) {
        if (_themeMode.value == ThemeMode.SYSTEM) _isDarkMode.value = isSystemDark
    }
    private fun resolveDark(mode: ThemeMode, ctx: Context): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
