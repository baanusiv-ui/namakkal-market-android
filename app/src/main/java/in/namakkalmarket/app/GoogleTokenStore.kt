package com.namakkalmarket.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GoogleTokenStore(context: Context) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "google_sheet_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(token: String) {
        prefs.edit().putString("access_token", token)
            .putLong("saved_at", System.currentTimeMillis()).apply()
    }

    fun getValidToken(): String? {
        val savedAt = prefs.getLong("saved_at", 0L)
        if (System.currentTimeMillis() - savedAt > 50 * 60 * 1000L) return null
        return prefs.getString("access_token", null)
    }
}
