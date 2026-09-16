package com.recipearchive.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.settings.SettingsStore
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.data.webimport.CredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var settingsStore: SettingsStore
    private lateinit var credentialStore: CredentialStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val credentialPrefs = context.getSharedPreferences("test_settings_vm_credentials", Context.MODE_PRIVATE)
        credentialPrefs.edit().clear().commit()

        settingsStore = SettingsStore(context)
        credentialStore = CredentialStore(credentialPrefs)
        viewModel = SettingsViewModel(settingsStore, credentialStore)
    }

    @Test
    fun `changing theme mode updates the store`() {
        viewModel.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals(ThemeMode.LIGHT, settingsStore.themeMode.value)
    }

    @Test
    fun `toggling nav labels updates the store`() {
        viewModel.setShowNavLabels(false)

        assertEquals(false, viewModel.showNavLabels.value)
        assertEquals(false, settingsStore.showNavLabels.value)
    }

    @Test
    fun `saving nyt credentials persists them and flips the saved flag`() {
        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.onNytPasswordChanged("hunter2")
        viewModel.saveNytCredentials()

        assertTrue(viewModel.nytAccountState.value.isSaved)
        assertTrue(credentialStore.hasCredentials())
        assertEquals("cook@example.com", credentialStore.getEmail())
    }

    @Test
    fun `saving with a blank field surfaces a status message and does not save`() {
        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.saveNytCredentials()

        assertEquals(false, viewModel.nytAccountState.value.isSaved)
        assertEquals(false, credentialStore.hasCredentials())
        assertEquals("Enter both an email and a password to save.", viewModel.nytAccountState.value.statusMessage)
    }

    @Test
    fun `test login never contacts NYT, only validates field shape`() {
        viewModel.onNytEmailChanged("not-an-email")
        viewModel.onNytPasswordChanged("hunter2")
        viewModel.testNytLogin()
        assertEquals("Enter a valid email and password first.", viewModel.nytAccountState.value.statusMessage)

        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.testNytLogin()
        assertTrue(viewModel.nytAccountState.value.statusMessage!!.contains("don't sign in"))
    }

    @Test
    fun `removing nyt credentials clears the saved state`() {
        credentialStore.saveCredentials("cook@example.com", "hunter2")
        val reloaded = SettingsViewModel(settingsStore, credentialStore)
        assertTrue(reloaded.nytAccountState.value.isSaved)

        reloaded.removeNytCredentials()

        assertEquals(false, reloaded.nytAccountState.value.isSaved)
        assertEquals(false, credentialStore.hasCredentials())
    }
}
