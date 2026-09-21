package com.recipearchive.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device storage for the home-server sync connection: base URL plus the
 * HTTP Basic Auth username/password Caddy expects in front of it. Kept
 * separate from [com.recipearchive.app.data.webimport.CredentialStore] (NYT
 * creds) since these are unrelated concerns.
 *
 * Takes a [SharedPreferences] directly rather than a [Context] so tests can
 * pass a plain (unencrypted) instance and exercise the save/load logic
 * without touching the Android Keystore. Real usage goes through [create].
 */
class ServerCredentialStore(private val prefs: SharedPreferences) {
    fun saveCredentials(serverUrl: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean =
        !getServerUrl().isNullOrBlank() && !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()

    fun clearCredentials() {
        prefs.edit().remove(KEY_SERVER_URL).remove(KEY_USERNAME).remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val PREFS_NAME = "server_sync_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        fun create(context: Context): ServerCredentialStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return ServerCredentialStore(prefs)
        }
    }
}
