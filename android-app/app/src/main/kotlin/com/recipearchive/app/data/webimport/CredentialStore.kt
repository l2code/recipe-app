package com.recipearchive.app.data.webimport

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device storage for the NYT Cooking email/password the user wants to keep
 * for their own reference. This never signs in to NYT or sends these values
 * anywhere -- recipe fetching relies entirely on the public page (see
 * [WebRecipeImportService]), so nothing in the app reads these values except
 * this store's own save/load/clear.
 *
 * Takes a [SharedPreferences] directly rather than a [Context] so tests can
 * pass a plain (unencrypted) instance and exercise the save/load logic
 * without touching the Android Keystore. Real usage goes through [create].
 */
class CredentialStore(private val prefs: SharedPreferences) {
    fun saveCredentials(email: String, password: String) {
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_PASSWORD, password).apply()
    }

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean = !getEmail().isNullOrBlank() && !getPassword().isNullOrBlank()

    fun clearCredentials() {
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val PREFS_NAME = "nyt_cooking_credentials"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"

        fun create(context: Context): CredentialStore {
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
            return CredentialStore(prefs)
        }
    }
}
