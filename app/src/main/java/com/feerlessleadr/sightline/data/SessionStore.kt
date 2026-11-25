package com.feerlessleadr.sightline.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable

/** A running Blue Iris sign-in: the server, the session token and the non-secret login details. */
@Serializable
data class SavedSession(val baseUrl: String, val token: String, val info: LoginInfo)

/**
 * Keeps the Blue Iris session token (never the password) so the app can open without the fingerprint while the
 * owner's "lock after" time has not run out. Encrypted with an AES-GCM Keystore key that needs no fingerprint,
 * so it is only used when the owner chose a lock time other than Always. Deleted on sign-out, lock, an expired
 * session or any decryption error, and excluded from backups.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("sightline_session", Context.MODE_PRIVATE)

    fun save(session: SavedSession) {
        try {
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val plain = BlueIrisJson.encodeToString(SavedSession.serializer(), session).toByteArray()
            prefs.edit().putString(KEY_BLOB, Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)).apply()
        } catch (e: Exception) {
            clear()
        }
    }

    fun load(): SavedSession? {
        val text = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val blob = Base64.decode(text, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES)) }
            BlueIrisJson.decodeFromString(SavedSession.serializer(), String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)))
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "sightline_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val KEY_BLOB = "blob"
        const val IV_BYTES = 12
    }
}
