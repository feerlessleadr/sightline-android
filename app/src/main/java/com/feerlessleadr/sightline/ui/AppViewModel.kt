package com.feerlessleadr.sightline.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.sightline.data.AddressResult
import com.feerlessleadr.sightline.data.BlueIrisClient
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.CameraList
import com.feerlessleadr.sightline.data.LoginInfo
import com.feerlessleadr.sightline.data.ServerAddress
import com.feerlessleadr.sightline.data.SessionExpiredException
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

data class UiState(val form: FormState, val session: SessionState? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sightline", Context.MODE_PRIVATE)
    private var client: BlueIrisClient? = null

    private val _state = MutableStateFlow(
        UiState(
            FormState(
                address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
                username = prefs.getString(KEY_USER, "").orEmpty(),
            ),
        ),
    )
    val state: StateFlow<UiState> = _state

    fun signIn(address: String, username: String, password: String) {
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
                    UiState(FormState(address, username), SessionState(info))
                }
                loadCameras()
            } catch (e: BlueIrisException) {
                _state.update { it.copy(form = FormState(address, username, error = e.message, busy = false)) }
            }
        }
    }

    fun refresh() {
        loadCameras()
    }

    fun selectGroup(optionValue: String?) {
        _state.update { s -> s.session?.let { s.copy(session = it.copy(selectedGroup = optionValue)) } ?: s }
    }

    fun signOut() {
        val old = client
        client = null
        _state.update { UiState(it.form.copy(busy = false, error = null)) }
        if (old != null) viewModelScope.launch { old.logout() }
    }

    private fun loadCameras() {
        val current = client ?: return
        _state.update { s -> s.session?.let { s.copy(session = it.copy(loading = true, error = null)) } ?: s }
        viewModelScope.launch {
            try {
                val list = current.cameras()
                _state.update { s -> s.session?.let { s.copy(session = it.copy(cameras = list, loading = false)) } ?: s }
            } catch (e: SessionExpiredException) {
                client = null
                _state.update { UiState(it.form.copy(error = e.message, busy = false)) }
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
