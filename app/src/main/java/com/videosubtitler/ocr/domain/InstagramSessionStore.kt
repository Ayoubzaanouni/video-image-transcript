package com.videosubtitler.ocr.domain

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the user's own Instagram session cookie (captured via an in-app login the
 * user explicitly performs and consents to) so link fetches can be made as that
 * logged-in session. Stored encrypted at rest; never sent anywhere but instagram.com.
 */
class InstagramSessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "instagram_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveCookie(cookie: String) {
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(): String? = prefs.getString(KEY_COOKIE, null)

    fun hasSession(): Boolean = !getCookie().isNullOrBlank()

    fun clear() {
        prefs.edit().remove(KEY_COOKIE).apply()
    }

    private companion object {
        const val KEY_COOKIE = "cookie"
    }
}
