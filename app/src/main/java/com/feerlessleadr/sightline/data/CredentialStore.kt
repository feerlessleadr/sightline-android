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

    val hasSaved: Boolean get() = prefs.getString(KEY_BLOB, null) != null

    /** A cipher ready for the biometric prompt, to encrypt new credentials. Null if the key cannot be made. */
    fun encryptCipher(): Cipher? = try {
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key(createIfMissing = true)) }
    } catch (e: Exception) {
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
            clear()
            null
        } catch (e: Exception) {
            clear()
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
        clear()
        false
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

    /** Forgets the saved sign-in and destroys its key. */
    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
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
            // 0 = authenticate for every use, with a strong biometric only.
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ALIAS = "sightline_credentials_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val KEY_BLOB = "blob"
        const val IV_BYTES = 12
    }
}
