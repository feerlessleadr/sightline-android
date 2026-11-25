package com.feerlessleadr.sightline.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.sightline.data.AddressResult
import com.feerlessleadr.sightline.data.BlueIrisClient
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.CameraList
import com.feerlessleadr.sightline.data.CredentialStore
import com.feerlessleadr.sightline.data.LoginFailedException
import com.feerlessleadr.sightline.data.LoginInfo
import com.feerlessleadr.sightline.data.SavedCredentials
import com.feerlessleadr.sightline.data.ServerAddress
import com.feerlessleadr.sightline.data.SessionExpiredException
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormState(
    val address: String = "",
    val username: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

data class SessionState(
    val info: LoginInfo,
    val cameras: CameraList? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** `optionValue` of the chosen group, or null for the all-cameras group. */
    val selectedGroup: String? = null,
)

/**
 * [locked]: a saved sign-in exists and a fingerprint is needed to use it (the lock screen is shown).
 * [pendingSave]: a successful sign-in the owner asked to remember, waiting for the fingerprint prompt that
 * authorises saving it. It is held in memory only until that prompt finishes or is cancelled.
 */
data class UiState(
    val form: FormState,
    val session: SessionState? = null,
    val locked: Boolean = false,
    val pendingSave: SavedCredentials? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sightline", Context.MODE_PRIVATE)
    private val store = CredentialStore(app)
    private var client: BlueIrisClient? = null

    private val _state = MutableStateFlow(
        UiState(
            form = FormState(
                address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
                username = prefs.getString(KEY_USER, "").orEmpty(),
            ),
            locked = store.hasSaved,
        ),
    )
    val state: StateFlow<UiState> = _state

    // ---- sign in with the password ----

    fun signIn(address: String, username: String, password: String, remember: Boolean) {
        if (_state.value.form.busy) return
        val parsed = ServerAddress.parse(address)
        if (parsed is AddressResult.Bad) {
            _state.update { it.copy(form = it.form.copy(address = address, username = username, error = parsed.reason)) }
            return
        }
        if (username.isBlank()) {
            _state.update { it.copy(form = it.form.copy(address = address, username = username, error = "Enter your Blue Iris user name.")) }
            return
        }
        val url = (parsed as AddressResult.Ok).url
        _state.update { it.copy(form = FormState(address, username, error = null, busy = true)) }
        viewModelScope.launch {
            val newClient = BlueIrisClient(url)
            try {
                val info = newClient.login(username.trim(), password)
                client = newClient
                prefs.edit().putString(KEY_ADDRESS, address.trim()).putString(KEY_USER, username.trim()).apply()
                _state.update {
                    UiState(
                        form = FormState(address, username),
                        session = SessionState(info),
                        pendingSave = if (remember) SavedCredentials(url.toString(), username.trim(), password) else null,
                    )
                }
                loadCameras()
            } catch (e: BlueIrisException) {
                _state.update { it.copy(form = FormState(address, username, error = e.message, busy = false)) }
            }
        }
    }

    // ---- remembering the sign-in (fingerprint authorises each step) ----

    /** A cipher for the fingerprint prompt that authorises saving, or null if saving is not possible. */
    fun saveCipher(): Cipher? = store.encryptCipher()

    /** Called after the fingerprint prompt succeeded with [cipher] from [saveCipher]. */
    fun completeSave(cipher: Cipher) {
        val pending = _state.value.pendingSave ?: return
        val saved = store.save(cipher, pending)
        _state.update {
            it.copy(
                pendingSave = null,
                session = it.session?.let { s -> if (saved) s else s.copy(error = "Could not save the sign-in on this device.") },
            )
        }
    }

    /** The fingerprint prompt was cancelled or failed: nothing is saved. */
    fun cancelSave() {
        _state.update { it.copy(pendingSave = null) }
    }

    // ---- unlocking with the fingerprint ----

    /** A cipher for the fingerprint prompt that authorises reading the saved sign-in, or null if it is gone. */
    fun unlockCipher(): Cipher? {
        val cipher = store.decryptCipher()
        if (cipher == null) {
            _state.update {
                it.copy(locked = false, form = it.form.copy(error = "The saved sign-in is no longer available. Please sign in again.", busy = false))
            }
        }
        return cipher
    }

    /** Called after the fingerprint prompt succeeded with [cipher] from [unlockCipher]. Signs in silently. */
    fun completeUnlock(cipher: Cipher) {
        if (_state.value.form.busy) return
        val saved = store.load(cipher)
        val url = saved?.let { (ServerAddress.parse(it.baseUrl) as? AddressResult.Ok)?.url }
        if (saved == null || url == null) {
            store.clear()
            _state.update { it.copy(locked = false, form = it.form.copy(error = "The saved sign-in could not be read. Please sign in again.", busy = false)) }
            return
        }
        _state.update { it.copy(form = it.form.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val newClient = BlueIrisClient(url)
            try {
                val info = newClient.login(saved.user, saved.password)
                client = newClient
                _state.update { UiState(form = FormState(it.form.address, it.form.username), session = SessionState(info)) }
                loadCameras()
            } catch (e: LoginFailedException) {
                // The password changed on the server; the saved copy is useless.
                store.clear()
                _state.update { it.copy(locked = false, form = it.form.copy(error = "Blue Iris refused the saved sign-in. Please sign in again.", busy = false)) }
            } catch (e: BlueIrisException) {
                // Probably no network. Stay locked so another fingerprint tap can retry.
                _state.update { it.copy(form = it.form.copy(error = e.message, busy = false)) }
            }
        }
    }

    /** Skip the fingerprint and type the password instead. The saved sign-in is kept. */
    fun usePassword() {
        _state.update { it.copy(locked = false, form = it.form.copy(error = null)) }
    }

    /** Delete the saved sign-in and its key. */
    fun forgetSaved() {
        store.clear()
        _state.update { it.copy(locked = false, form = it.form.copy(error = null)) }
    }

    // ---- using the session ----

    fun refresh() {
        loadCameras()
    }

    /** A current still for [camera], or null if it could not be fetched. An ended session goes back to sign-in. */
    suspend fun fetchStill(camera: String, width: Int): ByteArray? {
        val current = client ?: return null
        return try {
            current.still(camera, width)
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            null
        } catch (e: BlueIrisException) {
            null
        }
    }

    fun selectGroup(optionValue: String?) {
        _state.update { s -> s.session?.let { s.copy(session = it.copy(selectedGroup = optionValue)) } ?: s }
    }

    fun signOut() {
        val old = client
        client = null
        _state.update { UiState(form = it.form.copy(busy = false, error = null), locked = store.hasSaved) }
        if (old != null) viewModelScope.launch { old.logout() }
    }

    /** The server ended the session: back to the lock screen if a sign-in is saved, else the form. */
    private fun endSession(message: String?) {
        client = null
        _state.update { UiState(form = it.form.copy(error = message, busy = false), locked = store.hasSaved) }
    }

    private fun loadCameras() {
        val current = client ?: return
        _state.update { s -> s.session?.let { s.copy(session = it.copy(loading = true, error = null)) } ?: s }
        viewModelScope.launch {
            try {
                val list = current.cameras()
                _state.update { s -> s.session?.let { s.copy(session = it.copy(cameras = list, loading = false)) } ?: s }
            } catch (e: SessionExpiredException) {
                if (client === current) endSession(e.message)
            } catch (e: BlueIrisException) {
                _state.update { s -> s.session?.let { s.copy(session = it.copy(loading = false, error = e.message)) } ?: s }
            }
        }
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_USER = "username"
    }
}
