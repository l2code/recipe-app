package com.recipearchive.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        store = SettingsStore(context)
    }

    @Test
    fun `defaults to system theme and labels shown`() {
        assertEquals(ThemeMode.SYSTEM, store.themeMode.value)
        assertTrue(store.showNavLabels.value)
    }

    @Test
    fun `theme mode choice persists`() {
        store.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, store.themeMode.value)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val reloaded = SettingsStore(context)
        assertEquals(ThemeMode.DARK, reloaded.themeMode.value)
    }

    @Test
    fun `show nav labels choice persists`() {
        store.setShowNavLabels(false)

        assertEquals(false, store.showNavLabels.value)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val reloaded = SettingsStore(context)
        assertEquals(false, reloaded.showNavLabels.value)
    }
}
