package com.feerlessleadr.sightline.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable

/** What is needed to sign in again without typing. */
@Serializable
data class SavedCredentials(val baseUrl: String, val user: String, val password: String)

/**
 * Keeps the sign-in so the owner can unlock the app with a fingerprint instead of typing the password.
 * The credentials are encrypted with an AES-256-GCM key in the Android Keystore that can only be used
 * right after a strong biometric check (and is destroyed when fingerprints are added or removed).
 * Nothing here can be read without the fingerprint, and the file is excluded from backups.
 *
 * Saving and loading each need a [Cipher] that has been authorised by a BiometricPrompt, so the UI
 * gets one from [encryptCipher] / [decryptCipher], runs the prompt, then calls [save] / [load].
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("sightline_credentials", Context.MODE_PRIVATE)

    /** A sign-in saved behind the fingerprint (the default). */
    val hasProtected: Boolean get() = prefs.getString(KEY_BLOB, null) != null

    /** A sign-in saved with no fingerprint needed, because the owner chose "Never" lock. */
    val hasOpen: Boolean get() = prefs.getString(KEY_OPEN, null) != null

    val hasSaved: Boolean get() = hasProtected || hasOpen

    /** Why the last [encryptCipher] or [save] failed (an exception name and message, never any secret). */
    @Volatile
    var lastProblem: String? = null
        private set

    /** A cipher ready for the biometric prompt, to encrypt new credentials. Null if the key cannot be made. */
    fun encryptCipher(): Cipher? = try {
        lastProblem = null
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key(createIfMissing = true)) }
    } catch (e: Exception) {
        lastProblem = "${e.javaClass.simpleName}: ${e.message}".take(120)
        null
    }

    /** A cipher ready for the biometric prompt, to decrypt the saved credentials. Null (and cleared) if unusable. */
    fun decryptCipher(): Cipher? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val iv = Base64.decode(blob, Base64.NO_WRAP).copyOfRange(0, IV_BYTES)
            Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(createIfMissing = false) ?: error("no key"), GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearProtected()
            null
        } catch (e: Exception) {
            clearProtected()
            null
        }
    }

    /** [cipher] must come from [encryptCipher] and have been authorised by the biometric prompt. */
    fun save(cipher: Cipher, credentials: SavedCredentials): Boolean = try {
        val plain = BlueIrisJson.encodeToString(SavedCredentials.serializer(), credentials).toByteArray()
        val blob = cipher.iv + cipher.doFinal(plain)
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        true
    } catch (e: Exception) {
        lastProblem = "${e.javaClass.simpleName}: ${e.message}".take(120)
        clearProtected()
        false
    }

    /**
     * For the "Never lock" choice: saves the sign-in encrypted with a Keystore key that needs no fingerprint, so
     * the app can sign in by itself. Still unreadable by other apps and excluded from backups.
     */
    fun saveOpen(credentials: SavedCredentials): Boolean = try {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, openKey()) }
        val plain = BlueIrisJson.encodeToString(SavedCredentials.serializer(), credentials).toByteArray()
        prefs.edit().putString(KEY_OPEN, Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)).apply()
        true
    } catch (e: Exception) {
        lastProblem = "${e.javaClass.simpleName}: ${e.message}".take(120)
        clearOpen()
        false
    }

    fun loadOpen(): SavedCredentials? {
        val text = prefs.getString(KEY_OPEN, null) ?: return null
        return try {
            val blob = Base64.decode(text, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
                .apply { init(Cipher.DECRYPT_MODE, openKey(), GCMParameterSpec(128, blob, 0, IV_BYTES)) }
            BlueIrisJson.decodeFromString(
                SavedCredentials.serializer(),
                String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)),
            )
        } catch (e: Exception) {
            clearOpen()
            null
        }
    }

    /** [cipher] must come from [decryptCipher] and have been authorised by the biometric prompt. */
    fun load(cipher: Cipher): SavedCredentials? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            BlueIrisJson.decodeFromString(SavedCredentials.serializer(), String(plain))
        } catch (e: Exception) {
            null
        }
    }

    /** Forgets every saved sign-in and destroys the keys. */
    fun clear() {
        clearProtected()
        clearOpen()
    }

    /** Forgets only the fingerprint-protected sign-in and its key. */
    fun clearProtected() {
        prefs.edit().remove(KEY_BLOB).apply()
        deleteKeys(ALIAS, OLD_ALIAS)
    }

    /** Forgets only the no-fingerprint sign-in and its key. */
    fun clearOpen() {
        prefs.edit().remove(KEY_OPEN).apply()
        deleteKeys(OPEN_ALIAS)
    }

    private fun deleteKeys(vararg aliases: String) {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            aliases.forEach { store.deleteEntry(it) }
        } catch (_: Exception) {
        }
    }

    private fun openKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(OPEN_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(OPEN_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun key(createIfMissing: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) return null
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            // 0 = authenticate for every use, with a strong biometric or the phone's PIN, pattern or password.
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        // v2 accepts the phone's PIN, pattern or password as well as a strong biometric. The v1 key was
        // biometric-only and cannot be changed, so a sign-in saved with it is dropped and must be saved again.
        const val ALIAS = "sightline_credentials_key_v2"
        const val OLD_ALIAS = "sightline_credentials_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val OPEN_ALIAS = "sightline_credentials_open_key"
        const val KEY_BLOB = "blob"
        const val KEY_OPEN = "open_blob"
        const val IV_BYTES = 12
    }
}
