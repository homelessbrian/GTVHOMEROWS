package dev.tmdbrows.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted storage for the user's TMDB credential and the default target app. */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "tmdbrows_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var tmdbKey: String
        get() = prefs.getString(KEY_TMDB, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TMDB, v.trim()).apply()

    /** Package name of the app used when a list has no specific target. */
    var defaultTargetPackage: String
        get() = prefs.getString(KEY_DEFAULT_TARGET, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DEFAULT_TARGET, v).apply()

    /** URL pattern for a third-party artwork provider (btttr.cc, RPDB, ...). */
    var artPattern: String
        get() = prefs.getString(KEY_ART_PATTERN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ART_PATTERN, v.trim()).apply()

    var artEnabled: Boolean
        get() = prefs.getBoolean(KEY_ART_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ART_ENABLED, v).apply()

    var lastSyncMessage: String
        get() = prefs.getString(KEY_LAST_SYNC, "Never synced") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_SYNC, v).apply()

    companion object {
        private const val KEY_TMDB = "tmdb_key"
        private const val KEY_DEFAULT_TARGET = "default_target"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_ART_PATTERN = "art_pattern"
        private const val KEY_ART_ENABLED = "art_enabled"
    }
}
