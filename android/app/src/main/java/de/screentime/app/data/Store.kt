package de.screentime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** Verbindungsdaten zum PC. */
data class Conn(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val hostKey: String,
) {
    val complete: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && port in 1..65535
}

/**
 * Speichert die Zugangsdaten verschlüsselt auf dem Handy
 * (Android-Keystore, AES-256). Nichts davon verlässt das Gerät.
 */
class Store(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "screentime_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun conn() = Conn(
        host = prefs.getString("host", "") ?: "",
        port = prefs.getInt("port", 22),
        user = prefs.getString("user", "wolfram") ?: "wolfram",
        password = prefs.getString("password", "") ?: "",
        hostKey = prefs.getString("hostKey", "") ?: "",
    )

    fun save(host: String, port: Int, user: String, password: String) {
        val old = conn()
        val editor = prefs.edit()
            .putString("host", host.trim())
            .putInt("port", port)
            .putString("user", user.trim())
            .putString("password", password)
        // Neuer PC → bekannten PC-Schlüssel vergessen
        if (old.host != host.trim() || old.port != port) editor.putString("hostKey", "")
        editor.apply()
    }

    var hostKey: String
        get() = prefs.getString("hostKey", "") ?: ""
        set(value) = prefs.edit().putString("hostKey", value).apply()
}
