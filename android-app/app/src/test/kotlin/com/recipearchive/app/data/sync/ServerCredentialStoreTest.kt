package com.recipearchive.app.data.sync

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
 * SharedPreferences -- [ServerCredentialStore.create] is what wires up the real
 * Android-Keystore-backed EncryptedSharedPreferences, which isn't available
 * under Robolectric's JVM environment.
 */
@RunWith(RobolectricTestRunner::class)
class ServerCredentialStoreTest {

    private lateinit var store: ServerCredentialStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_server_sync_credentials", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = ServerCredentialStore(prefs)
    }

    @Test
    fun `has no credentials before anything is saved`() {
        assertFalse(store.hasCredentials())
        assertNull(store.getServerUrl())
        assertNull(store.getUsername())
        assertNull(store.getPassword())
    }

    @Test
    fun `saves and reads back credentials`() {
        store.saveCredentials("https://pi5.example.ts.net:8444", "rissac", "hunter2")

        assertTrue(store.hasCredentials())
        assertEquals("https://pi5.example.ts.net:8444", store.getServerUrl())
        assertEquals("rissac", store.getUsername())
        assertEquals("hunter2", store.getPassword())
    }

    @Test
    fun `clearing removes saved credentials`() {
        store.saveCredentials("https://pi5.example.ts.net:8444", "rissac", "hunter2")

        store.clearCredentials()

        assertFalse(store.hasCredentials())
        assertNull(store.getServerUrl())
    }

    @Test
    fun `blank password does not count as having credentials`() {
        store.saveCredentials("https://pi5.example.ts.net:8444", "rissac", "")

        assertFalse(store.hasCredentials())
    }
}
