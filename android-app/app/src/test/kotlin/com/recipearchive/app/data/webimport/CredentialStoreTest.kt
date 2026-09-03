package com.recipearchive.app.data.webimport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the save/load/clear logic against a plain (unencrypted) Robolectric
 * SharedPreferences -- [CredentialStore.create] is what wires up the real
 * Android-Keystore-backed EncryptedSharedPreferences, which isn't available
 * under Robolectric's JVM environment.
 */
@RunWith(RobolectricTestRunner::class)
class CredentialStoreTest {

    private lateinit var store: CredentialStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_nyt_credentials", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = CredentialStore(prefs)
    }

    @Test
    fun `has no credentials before anything is saved`() {
        assertFalse(store.hasCredentials())
        assertNull(store.getEmail())
        assertNull(store.getPassword())
    }

    @Test
    fun `saves and reads back credentials`() {
        store.saveCredentials("cook@example.com", "hunter2")

        assertTrue(store.hasCredentials())
        assertEquals("cook@example.com", store.getEmail())
        assertEquals("hunter2", store.getPassword())
    }

    @Test
    fun `clearing removes saved credentials`() {
        store.saveCredentials("cook@example.com", "hunter2")

        store.clearCredentials()

        assertFalse(store.hasCredentials())
        assertNull(store.getEmail())
        assertNull(store.getPassword())
    }

    @Test
    fun `blank password does not count as having credentials`() {
        store.saveCredentials("cook@example.com", "")

        assertFalse(store.hasCredentials())
    }
}
