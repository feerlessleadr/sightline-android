package com.feerlessleadr.sightline.ui

import android.app.Application
import android.content.Context
import com.feerlessleadr.sightline.data.AlertFilterConfig
import com.feerlessleadr.sightline.data.AlertFilterSettings
import com.feerlessleadr.sightline.data.Exports
import com.feerlessleadr.sightline.data.ExportStart
import com.feerlessleadr.sightline.data.ExportOptions
import com.feerlessleadr.sightline.data.ExportQueue
import com.feerlessleadr.sightline.data.ExportRequest
import com.feerlessleadr.sightline.data.ClipRange
import com.feerlessleadr.sightline.data.RecordingFile
import com.feerlessleadr.sightline.data.RecordingFiles
import com.feerlessleadr.sightline.data.AlertClips
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import android.provider.MediaStore
import android.os.SystemClock
import android.content.ContentValues
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.sightline.data.AddressResult
import com.feerlessleadr.sightline.data.BlueIrisClient
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.CameraList
import com.feerlessleadr.sightline.data.CredentialStore
import com.feerlessleadr.sightline.data.PopupConfig
import com.feerlessleadr.sightline.data.LockAfter
import com.feerlessleadr.sightline.data.LockPolicy
import com.feerlessleadr.sightline.data.LoginFailedException
import com.feerlessleadr.sightline.data.RecentAlert
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.AlertsFeed
import com.feerlessleadr.sightline.data.AlertFlags
import com.feerlessleadr.sightline.data.Timeline
import com.feerlessleadr.sightline.data.SavedSession
import com.feerlessleadr.sightline.data.SessionStore
import com.feerlessleadr.sightline.data.ProbeVariant
import com.feerlessleadr.sightline.data.VideoQuality
import com.feerlessleadr.sightline.data.LoginInfo
import com.feerlessleadr.sightline.data.SavedCredentials
import com.feerlessleadr.sightline.data.ServerAddress
import com.feerlessleadr.sightline.data.SessionExpiredException
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest
import javax.crypto.Cipher
import kotlinx.coroutines.delay
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
    /** Chosen tile size per group `optionValue`; groups not listed use [TileSize.Auto]. */
    val tileSizes: Map<String, TileSize> = emptyMap(),
    /** Hand-made camera order and tile shapes per group `optionValue`. */
    val arrangements: Map<String, GroupArrangement> = emptyMap(),
    /** Live video in the camera tiles (off = stills only). */
    val liveVideo: Boolean = true,
    /** Coloured borders on tiles whose camera sees motion or is triggered. */
    val highlight: Boolean = true,
    /** What the AI saw ("Person 91%") on the tile while it is still happening. */
    val aiLabels: Boolean = true,
    /** Boxes drawn on tiles around movement the app finds by comparing pictures (off by default, like the web app). */
    val motionBoxes: Boolean = false,
    /** How sensitive the boxes are, 1 (only big movement) to 10 (almost any change); 5 is balanced. */
    val motionSensitivity: Int = 5,
    /** How exports (MP4 downloads of alerts) are made. */
    val export: ExportOptions = ExportOptions(),
    /** The download being made or just finished (shown over the player); null when there is none. */
    val download: DownloadState? = null,
    /** A folder the owner chose for downloads (a tree address from the phone's folder picker), or null for the Downloads folder. */
    val downloadFolder: String? = null,
    val downloadFolderName: String? = null,
    val popup: PopupConfig = PopupConfig(),
    /** Developer: outline each camera of a group picture in the player. */
    val groupDebug: Boolean = false,
    /** On wide screens the Alerts tab shows a preview panel beside the list. */
    val alertPreview: Boolean = true,
    /** The file name of the web version of Sightline on the Blue Iris server (Settings > Account). */
    val webPage: String = com.feerlessleadr.sightline.data.WebLink.DEFAULT_PAGE,
    /** Show cameras marked Hidden in Blue Iris (disabled ones never show). */
    val showHidden: Boolean = false,
    /** Group buttons the owner hid from the Live screen (stored as hidden, so groups added later show by default). */
    val hiddenGroups: Set<String> = emptySet(),
    /** Sound in the player (on or muted) and its volume, 0 to 2 (1 is normal). */
    val soundOn: Boolean = true,
    val volume: Float = 1f,
    /** False while Blue Iris cannot be reached (a network blip); the grid shows a "Reconnecting" banner. */
    val online: Boolean = true,
    /** Size of the one-camera video stream. */
    val quality: VideoQuality = VideoQuality.Auto,
    /** How long the app may be away before the fingerprint is needed again. */
    val lockAfter: LockAfter = LockAfter.Always,
    /** A sign-in is saved on this device behind the fingerprint. */
    val savedSignIn: Boolean = false,
)

/**
 * [locked]: a saved sign-in exists and a fingerprint is needed to use it (the lock screen is shown).
 * [pendingSave]: a successful sign-in the owner asked to remember, waiting for the fingerprint prompt that
 * authorises saving it. It is held in memory only until that prompt finishes or is cancelled.
 */
/** A download of an alert as an MP4: what to tell the owner, how far it is (null = unknown), and whether it ended. */
data class DownloadState(
    val text: String,
    val percent: Int?,
    val name: String,
    val finished: Boolean = false,
    val failed: Boolean = false,
)

data class UiState(
    val form: FormState,
    val session: SessionState? = null,
    val locked: Boolean = false,
    val pendingSave: SavedCredentials? = null,
    /** With the "Never" lock choice the lock screen is just "Signing in…" (with Try again), not a fingerprint prompt. */
    val autoSignIn: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sightline", Context.MODE_PRIVATE)
    private val store = CredentialStore(app)
    private val sessions = SessionStore(app)
    private var client: BlueIrisClient? = null

    private val _state = MutableStateFlow(
        UiState(
            form = FormState(
                address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
                username = prefs.getString(KEY_USER, "").orEmpty(),
            ),
            locked = store.hasSaved,
            autoSignIn = neverModeNow(),
        ),
    )
    val state: StateFlow<UiState> = _state

    /** The chosen lock time. "Never" only counts while a no-fingerprint sign-in is really saved. */
    private val lockAfter: LockAfter
        get() {
            val chosen = LockAfter.entries.firstOrNull { it.name == prefs.getString(KEY_LOCK_AFTER, null) } ?: LockAfter.Always
            return if (chosen == LockAfter.Never && !store.hasOpen) LockAfter.Always else chosen
        }

    private fun neverModeNow() = lockAfter == LockAfter.Never && store.hasOpen

    /** What the owner picked, whether or not a sign-in is saved yet (used to decide how to save a new one). */
    private fun lockAfterChoice() =
        LockAfter.entries.firstOrNull { it.name == prefs.getString(KEY_LOCK_AFTER, null) } ?: LockAfter.Always

    private var signingInAgain = false

    init {
        startKeepAlive()
        if (neverModeNow()) {
            // "Never": no fingerprint. Pick the saved session up, or sign in by ourselves with the saved password.
            val saved = sessions.load()
            val url = saved?.let { (ServerAddress.parse(it.baseUrl) as? AddressResult.Ok)?.url }
            if (saved != null && url != null) {
                client = BlueIrisClient(url).also { it.restore(saved.token) }
                _state.update { it.copy(locked = false, autoSignIn = false, session = newSession(saved.info)) }
                loadCameras()
            } else {
                signInAgain()
            }
        } else if (store.hasSaved && LockPolicy.stillUnlocked(lockAfter, prefs.getLong(KEY_LAST_STOP, 0L), System.currentTimeMillis())) {
            // Opened again soon enough after leaving: pick the session up without the fingerprint. The server decides
            // on the first call whether it is still valid; if not, the lock screen shows.
            val saved = sessions.load()
            val url = saved?.let { (ServerAddress.parse(it.baseUrl) as? AddressResult.Ok)?.url }
            if (saved != null && url != null) {
                client = BlueIrisClient(url).also { it.restore(saved.token) }
                _state.update { it.copy(locked = false, session = newSession(saved.info)) }
                loadCameras()
            } else {
                sessions.clear()
            }
        }
    }

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
                val credentials = SavedCredentials(url.toString(), username.trim(), password)
                memoryCredentials = if (remember) credentials else null
                // With the "Never" lock choice there is no fingerprint to ask for: save the sign-in straight away.
                val openSave = remember && lockAfterChoice() == LockAfter.Never
                val savedOpen = openSave && store.saveOpen(credentials)
                _state.update {
                    UiState(
                        form = FormState(address, username),
                        session = newSession(info),
                        pendingSave = if (remember && !openSave) credentials else null,
                    )
                }
                if (savedOpen) persistSession(url.toString())
                loadCameras()
            } catch (e: BlueIrisException) {
                _state.update { it.copy(form = FormState(address, username, error = e.message, busy = false)) }
            }
        }
    }

    // ---- remembering the sign-in (fingerprint authorises each step) ----

    /** A cipher for the fingerprint prompt that authorises saving, or null if saving is not possible. */
    fun saveCipher(): Cipher? {
        val cipher = store.encryptCipher()
        if (cipher == null) cancelSave("Your sign-in was not saved: this phone could not set up secure storage (${store.lastProblem}).")
        return cipher
    }

    /** Called after the fingerprint prompt succeeded with [cipher] from [saveCipher]. */
    fun completeSave(cipher: Cipher) {
        val pending = _state.value.pendingSave ?: return
        val saved = store.save(cipher, pending)
        _state.update {
            it.copy(
                pendingSave = null,
                session = it.session?.let { s ->
                    if (saved) s.copy(savedSignIn = true) else s.copy(error = "Your sign-in was not saved (${store.lastProblem}).")
                },
            )
        }
        if (saved) persistSession(pending.baseUrl)
    }

    /** Nothing is saved (the prompt was cancelled or failed, or secure storage is unavailable); say why on screen. */
    fun cancelSave(reason: String = "Your sign-in was not saved: the fingerprint or screen-lock check did not finish.") {
        _state.update { it.copy(pendingSave = null, session = it.session?.copy(error = reason)) }
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
            sessions.clear()
            _state.update { it.copy(locked = false, form = it.form.copy(error = "The saved sign-in could not be read. Please sign in again.", busy = false)) }
            return
        }
        _state.update { it.copy(form = it.form.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val newClient = BlueIrisClient(url)
            try {
                val info = newClient.login(saved.user, saved.password)
                client = newClient
                memoryCredentials = saved
                _state.update { UiState(form = FormState(it.form.address, it.form.username), session = newSession(info)) }
                persistSession(saved.baseUrl)
                loadCameras()
            } catch (e: LoginFailedException) {
                // The password changed on the server; the saved copy is useless.
                store.clear()
                sessions.clear()
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

    /** Delete the saved sign-in and its key (and any saved session). */
    fun forgetSaved() {
        memoryCredentials = null
        store.clear()
        sessions.clear()
        _state.update { it.copy(locked = false, form = it.form.copy(error = null), session = it.session?.copy(savedSignIn = false)) }
    }

    // ---- locking ----

    private var awaySince = 0L
    private var pickerOpenedAt = 0L

    /** The app is about to open the phone's folder picker: coming back from it must not lock the app. */
    fun openingPicker() {
        pickerOpenedAt = System.currentTimeMillis()
    }

    /**
     * The app left the foreground: remember when, so coming back can be judged against the lock time. A screen
     * rotation also stops and restarts the activity; that is not leaving, so it is ignored.
     */
    fun onBackgrounded(changingConfiguration: Boolean) {
        if (changingConfiguration) return
        inForeground = false
        awaySince = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_STOP, awaySince).apply()
    }

    /** The app came back. If a sign-in is saved and the lock time has run out, lock it (the stream stops with it). */
    fun onForegrounded() {
        inForeground = true
        val left = awaySince
        awaySince = 0L
        // Coming back from a system screen the app opened itself (the folder picker) is not leaving the app.
        if (pickerOpenedAt > 0L && System.currentTimeMillis() - pickerOpenedAt < 10 * 60_000L) {
            pickerOpenedAt = 0L
            return
        }
        pickerOpenedAt = 0L
        if (left == 0L || _state.value.session == null || !store.hasSaved) return
        if (!LockPolicy.stillUnlocked(lockAfter, left, System.currentTimeMillis())) lockNow()
    }

    private fun lockNow() {
        val old = client
        client = null
        alerts.clear()
        alertFilters.clear()
        activity.clear()
        retention.clear()
        memoryCredentials = null
        downloadJob?.cancel()
        sessions.clear()
        _state.update { UiState(form = it.form.copy(busy = false, error = null), locked = true) }
        if (old != null) viewModelScope.launch { old.logout() }
    }

    /** Keeps the session token (encrypted, no fingerprint) only when a lock time other than Always is chosen. */
    private fun persistSession(baseUrl: String) {
        val current = client ?: return
        val token = current.sessionToken ?: return
        val info = _state.value.session?.info ?: return
        if (lockAfter == LockAfter.Always) sessions.clear() else sessions.save(SavedSession(baseUrl, token, info))
    }

    // ---- switching to and from "Never" (each needs one fingerprint check) ----

    /** A cipher for the fingerprint prompt that authorises moving the saved sign-in to the "Never" form. */
    fun neverCipher(): Cipher? {
        val cipher = store.decryptCipher()
        if (cipher == null) cancelSave("The saved sign-in is no longer available; sign in again with Remember me.")
        return cipher
    }

    /** After the fingerprint check: keep the sign-in without a fingerprint requirement and stop asking for one. */
    fun completeNever(cipher: Cipher) {
        val saved = store.load(cipher)
        if (saved == null || !store.saveOpen(saved)) {
            cancelSave("Could not change the lock setting (${store.lastProblem ?: "the saved sign-in could not be read"}).")
            return
        }
        store.clearProtected()
        prefs.edit().putString(KEY_LOCK_AFTER, LockAfter.Never.name).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(lockAfter = LockAfter.Never, savedSignIn = true)) } ?: s }
        persistSession(saved.baseUrl)
    }

    /** A cipher for the fingerprint prompt that authorises putting the saved sign-in behind the fingerprint again. */
    fun protectCipher(): Cipher? {
        val cipher = store.encryptCipher()
        if (cipher == null) cancelSave("Could not change the lock setting: this phone could not set up secure storage (${store.lastProblem}).")
        return cipher
    }

    /** After the fingerprint check: protect the sign-in again and use the chosen lock time. */
    fun completeProtect(cipher: Cipher, value: LockAfter) {
        val saved = store.loadOpen()
        if (saved == null || !store.save(cipher, saved)) {
            cancelSave("Could not change the lock setting (${store.lastProblem ?: "the saved sign-in could not be read"}).")
            return
        }
        store.clearOpen()
        prefs.edit().putString(KEY_LOCK_AFTER, value.name).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(lockAfter = value, savedSignIn = true)) } ?: s }
        if (value == LockAfter.Always) sessions.clear() else persistSession(saved.baseUrl)
    }

    /** Changes the lock time between Always and the timed choices. Moving to or from Never needs the fingerprint flow above. */
    fun setLockAfter(value: LockAfter) {
        if (value == LockAfter.Never || neverModeNow()) return
        prefs.edit().putString(KEY_LOCK_AFTER, value.name).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(lockAfter = value)) } ?: s }
        if (value == LockAfter.Always) {
            sessions.clear()
        } else if (store.hasSaved) {
            val url = prefs.getString(KEY_ADDRESS, null)?.let { ServerAddress.parse(it) as? AddressResult.Ok }?.url
            if (url != null) persistSession(url.toString())
        }
    }

    fun setQuality(value: VideoQuality) {
        prefs.edit().putString(KEY_QUALITY, value.name).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(quality = value)) } ?: s }
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

    /** Opens the live stream of [camera]. An ended session goes back to sign-in; every failure is rethrown. */
    suspend fun openVideo(camera: String, size: StreamRequest? = null): VideoStream {
        val current = client ?: throw SessionExpiredException()
        try {
            return current.openVideo(camera, size)
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            throw e
        }
    }

    /** Runs [block] with the signed-in client; an ended session goes back to sign-in and is rethrown. */
    private suspend fun <T> guarded(block: suspend (BlueIrisClient) -> T): T {
        val current = client ?: throw SessionExpiredException()
        try {
            return block(current)
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            throw e
        }
    }

    // ---- the Alerts tab ----

    /** The alert list (kept here so it survives opening an alert in the player and coming back). */
    val alerts = AlertsFeed(
        scope = viewModelScope,
        fetch = { camera, startSec, endSec -> guarded { it.cameraAlerts(camera, startSec * 1000, endSec * 1000) } },
        shown = { name ->
            _state.value.session?.cameras?.cameras?.any { it.optionValue.equals(name, ignoreCase = true) && it.isEnabled && (!it.hidden || _state.value.session?.showHidden == true) } ?: true
        },
    )

    /** Finds an alert of [camera] from the last 3 days by its Blue Iris id (a notification link); null if it is not there. */
    suspend fun findAlert(camera: String, id: String): com.feerlessleadr.sightline.data.AlertItem? = try {
        val want = AlertFlags.recordId(id)
        val now = System.currentTimeMillis()
        guarded { it.cameraAlerts(camera, now - 3 * 86_400_000L, now + 60_000L) }.firstOrNull { AlertFlags.recordId(it.path) == want }
    } catch (e: BlueIrisException) {
        null
    }

    /** How far back each camera's recordings go (Server status). */
    val retention = com.feerlessleadr.sightline.data.RetentionFeed(
        scope = viewModelScope,
        timeline = { camera, startMs, endMs, step -> guarded { it.timeline(camera, startMs, endMs, step) } },
    )

    /** The Activity screen's alert counts. */
    val activity = com.feerlessleadr.sightline.data.ActivityFeed(
        scope = viewModelScope,
        fetch = { camera, startSec, endSec -> guarded { it.cameraAlerts(camera, startSec * 1000, endSec * 1000) } },
        shown = { name ->
            _state.value.session?.cameras?.cameras?.any { it.optionValue.equals(name, ignoreCase = true) && it.isEnabled && (!it.hidden || _state.value.session?.showHidden == true) } ?: true
        },
    )

    /** Which filter buttons the Alerts tab shows, kept on the phone. */
    val alertFilters = AlertFilterSettings(
        scope = viewModelScope,
        fetch = { camera, startSec, endSec -> guarded { it.cameraAlerts(camera, startSec * 1000, endSec * 1000) } },
        shown = { name ->
            _state.value.session?.cameras?.cameras?.any { it.optionValue.equals(name, ignoreCase = true) && it.isEnabled && (!it.hidden || _state.value.session?.showHidden == true) } ?: true
        },
        load = { AlertFilterConfig.decode(prefs.getString(KEY_ALERT_FILTERS, null)) },
        save = { prefs.edit().putString(KEY_ALERT_FILTERS, it.encode()).apply() },
    )

    /** The thumbnail of an alert, or null if it cannot be had (the list shows a blank). */
    suspend fun alertThumbnail(path: String): ByteArray? = try {
        guarded { it.thumbnail(AlertFlags.recordId(path)) }
    } catch (e: SessionExpiredException) {
        null
    } catch (e: BlueIrisException) {
        null
    }

    /** A still picture of [camera] at [positionMs] for the alert preview panel; null if Blue Iris has none. */
    suspend fun alertFrame(camera: String, positionMs: Long): ByteArray? = try {
        guarded { it.recordedStill(camera, positionMs, 960) }
    } catch (e: SessionExpiredException) {
        null
    } catch (e: BlueIrisException) {
        null
    }

    /** Flags or protects an alert from the list: shown at once, taken back if Blue Iris refuses (the error is rethrown). */
    suspend fun changeAlertFlags(path: String, before: Long, after: Long) {
        alerts.noteFlags(path, after)
        try {
            guarded { it.setAlertFlags(path, after) }
        } catch (e: BlueIrisException) {
            alerts.noteFlags(path, before)
            throw e
        }
    }

    /** The player's calls to Blue Iris (live and recorded video, stills, timeline, alerts). */
    val playerApi = PlayerApi(
        openLive = { camera, size, audio, group -> guarded { it.openVideo(camera, size, audio, group) } },
        openRecorded = { camera, pos, speed, size, audio, group -> guarded { it.openRecorded(camera, pos, speed, size, audio, group) } },
        liveStill = { camera, width -> fetchStill(camera, width) },
        openClip = { file, offset, speed, size, audio -> guarded { it.openClip(file, offset, speed, size, audio) } },
        recordedStill = { camera, pos, width, group -> guarded { it.recordedStill(camera, pos, width, group) } },
        timeline = { camera, start, end, step -> guarded { it.timeline(camera, start, end, step) } },
        alerts = { camera, start, end -> guarded { it.cameraAlerts(camera, start, end) } },
        setFlags = { path, flags -> guarded { it.setAlertFlags(path, flags) }.also { alerts.noteFlags(path, flags) } },
    )

    /** Diagnostic for Settings -> Test video start: one line describing how [variant] of opening [camera] performed. */
    suspend fun probeVideo(camera: String, size: StreamRequest, variant: ProbeVariant): String {
        val current = client ?: return "Not signed in."
        return try {
            current.probeVideo(camera, size, variant)
        } catch (e: BlueIrisException) {
            "${variant.label}: ${e.message}"
        }
    }

    private fun newSession(info: LoginInfo) = SessionState(
        info = info,
        tileSizes = prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_TILE_SIZE) && value is String) {
                TileSize.entries.firstOrNull { it.name == value }?.let { key.removePrefix(KEY_TILE_SIZE) to it }
            } else {
                null
            }
        }.toMap(),
        arrangements = prefs.all.keys.filter { it.startsWith(KEY_ORDER) || it.startsWith(KEY_SHAPES) }
            .map { it.removePrefix(KEY_ORDER).removePrefix(KEY_SHAPES) }.distinct()
            .associateWith { group ->
                GroupArrangement.decode(prefs.getString(KEY_ORDER + group, null), prefs.getString(KEY_SHAPES + group, null))
            },
        liveVideo = prefs.getBoolean(KEY_LIVE_VIDEO, true),
        highlight = prefs.getBoolean(KEY_HIGHLIGHT, true),
        aiLabels = prefs.getBoolean(KEY_AI_LABELS, true),
        motionBoxes = prefs.getBoolean(KEY_MOTION_BOXES, false),
        export = ExportOptions(
            reencode = prefs.getBoolean(KEY_EXPORT_REENCODE, true),
            profile = prefs.getInt(KEY_EXPORT_PROFILE, 0).coerceIn(0, 3),
            audio = prefs.getBoolean(KEY_EXPORT_AUDIO, true),
            overlay = prefs.getBoolean(KEY_EXPORT_OVERLAY, true),
        ),
        downloadFolder = prefs.getString(KEY_DOWNLOAD_FOLDER, null),
        downloadFolderName = prefs.getString(KEY_DOWNLOAD_FOLDER, null)?.let { folderName(android.net.Uri.parse(it)) },
        popup = PopupConfig.decode(prefs.getString(KEY_POPUP, null)),
        webPage = com.feerlessleadr.sightline.data.WebLink.clean(prefs.getString(KEY_WEB_PAGE, null)) ?: com.feerlessleadr.sightline.data.WebLink.DEFAULT_PAGE,
        showHidden = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
        groupDebug = prefs.getBoolean(KEY_GROUP_DEBUG, false),
        alertPreview = prefs.getBoolean(KEY_ALERT_PREVIEW, true),
        hiddenGroups = prefs.getStringSet(KEY_HIDDEN_GROUPS, emptySet()).orEmpty().toSet(),
        soundOn = prefs.getBoolean(KEY_SOUND_ON, true),
        volume = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 2f),
        motionSensitivity = prefs.getInt(KEY_MOTION_SENS, 5).coerceIn(1, 10),
        quality = VideoQuality.entries.firstOrNull { it.name == prefs.getString(KEY_QUALITY, null) } ?: VideoQuality.Auto,
        lockAfter = lockAfter,
        savedSignIn = store.hasSaved,
    )

    fun setHighlight(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIGHLIGHT, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(highlight = on)) } ?: s }
    }

    /** Remembers the sound setting (on or muted) and volume on this phone. */
    fun setSound(on: Boolean, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        prefs.edit().putBoolean(KEY_SOUND_ON, on).putFloat(KEY_VOLUME, v).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(soundOn = on, volume = v)) } ?: s }
    }

    fun setMotionBoxes(on: Boolean) {
        prefs.edit().putBoolean(KEY_MOTION_BOXES, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(motionBoxes = on)) } ?: s }
    }

    fun setMotionSensitivity(level: Int) {
        val value = level.coerceIn(1, 10)
        prefs.edit().putInt(KEY_MOTION_SENS, value).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(motionSensitivity = value)) } ?: s }
    }

    /** Shows or hides cameras Blue Iris marks Hidden; what was counted from the camera set is forgotten. */
    fun setShowHidden(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(showHidden = on)) } ?: s }
        alerts.clear()
        alertFilters.clear()
        activity.clear()
        retention.clear()
    }

    /** Shows or hides a group button on the Live screen; false (nothing changed) if it would leave none. */
    fun toggleGroup(id: String): Boolean {
        val session = _state.value.session ?: return false
        val all = session.cameras?.groups.orEmpty().map { it.optionValue }
        val hidden = session.hiddenGroups
        val next = if (id in hidden) hidden - id else hidden + id
        if (all.none { it !in next }) return false
        prefs.edit().putStringSet(KEY_HIDDEN_GROUPS, next).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(hiddenGroups = next)) } ?: s }
        return true
    }

    /** Settings: the alert preview panel beside the list on wide screens. */
    fun setAlertPreview(on: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_PREVIEW, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(alertPreview = on)) } ?: s }
    }

    /** Developer: show or hide the group layout outlines in the player. */
    fun setGroupDebug(on: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_DEBUG, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(groupDebug = on)) } ?: s }
    }

    /** Saves the web page file name; false if it is not allowed (see [com.feerlessleadr.sightline.data.WebLink]). */
    fun setWebPage(text: String): Boolean {
        val clean = com.feerlessleadr.sightline.data.WebLink.clean(text) ?: return false
        prefs.edit().putString(KEY_WEB_PAGE, clean).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(webPage = clean)) } ?: s }
        return true
    }

    /** The address of the web version of Sightline on the Blue Iris server, to open in the browser; null if it cannot be made. */
    fun webPageUrl(): String? {
        val base = (com.feerlessleadr.sightline.data.ServerAddress.parse(prefs.getString(KEY_ADDRESS, "").orEmpty()) as? com.feerlessleadr.sightline.data.AddressResult.Ok)?.url ?: return null
        return com.feerlessleadr.sightline.data.WebLink.url(base, _state.value.session?.webPage)
    }

    /** Which detections pop a camera up on the grid (Settings > Pop up on detection). */
    fun setPopup(config: PopupConfig) {
        prefs.edit().putString(KEY_POPUP, config.encode()).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(popup = config)) } ?: s }
    }

    fun setAiLabels(on: Boolean) {
        prefs.edit().putBoolean(KEY_AI_LABELS, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(aiLabels = on)) } ?: s }
    }

    /** Saves the hand-made order and tile shapes of [group] on this device. */
    fun setArrangement(group: String, value: GroupArrangement) {
        prefs.edit().apply {
            if (value.order.isEmpty()) remove(KEY_ORDER + group) else putString(KEY_ORDER + group, value.encodeOrder())
            if (value.shapes.isEmpty()) remove(KEY_SHAPES + group) else putString(KEY_SHAPES + group, value.encodeShapes())
        }.apply()
        _state.update { s ->
            s.session?.let { s.copy(session = it.copy(arrangements = if (value.isDefault) it.arrangements - group else it.arrangements + (group to value))) } ?: s
        }
    }

    /** "Reset layout for this view": back to Blue Iris's order, normal tiles and fit-to-screen size. */
    fun resetLayout(group: String) {
        setArrangement(group, GroupArrangement())
        setTileSize(group, TileSize.Auto)
    }

    /** Remembers the tile size for [group] on this device. */
    fun setTileSize(group: String, size: TileSize) {
        prefs.edit().putString(KEY_TILE_SIZE + group, size.name).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(tileSizes = it.tileSizes + (group to size))) } ?: s }
    }

    fun setLiveVideo(on: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_VIDEO, on).apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(liveVideo = on)) } ?: s }
    }

    fun selectGroup(optionValue: String?) {
        _state.update { s -> s.session?.let { s.copy(session = it.copy(selectedGroup = optionValue)) } ?: s }
    }

    fun signOut() {
        val old = client
        client = null
        alerts.clear()
        alertFilters.clear()
        activity.clear()
        retention.clear()
        memoryCredentials = null
        downloadJob?.cancel() // a download does not go on after signing out
        sessions.clear()
        // Signing out with the "Never" lock choice means really signed out: forget the saved sign-in too, or the
        // app would just sign straight back in.
        if (neverModeNow()) store.clear()
        _state.update { UiState(form = it.form.copy(busy = false, error = null), locked = store.hasSaved) }
        if (old != null) viewModelScope.launch { old.logout() }
    }

    /**
     * The sign-in the app may use to sign back in by itself while it is open: the password is held in memory only,
     * from a fingerprint unlock or a "Remember me" sign-in, never for a sign-in the owner did not ask to remember.
     */
    private var memoryCredentials: SavedCredentials? = null
    private var reSigningIn = false

    /** Quietly signs in again after Blue Iris ended the session (the web app's "stay signed in"); the grid just carries on. */
    private fun silentReSignIn(creds: SavedCredentials, fallbackMessage: String?) {
        if (reSigningIn) return
        val url = (ServerAddress.parse(creds.baseUrl) as? AddressResult.Ok)?.url
        if (url == null) {
            hardEndSession(fallbackMessage)
            return
        }
        reSigningIn = true
        val old = client
        viewModelScope.launch {
            val fresh = BlueIrisClient(url)
            try {
                fresh.login(creds.user, creds.password)
                if (client === old) {
                    client = fresh
                    persistSession(creds.baseUrl)
                    _state.update { s -> s.session?.let { s.copy(session = it.copy(online = true)) } ?: s }
                }
            } catch (e: LoginFailedException) {
                if (client === old) hardEndSession(fallbackMessage)
            } catch (e: BlueIrisException) {
                // Probably the network: the status poll tries again in a moment.
                _state.update { s -> s.session?.let { s.copy(session = it.copy(online = false)) } ?: s }
            } finally {
                reSigningIn = false
            }
        }
    }

    /** The server ended the session: sign in again quietly if the password is at hand, else the lock screen or the form. */
    private fun endSession(message: String?) {
        val creds = memoryCredentials ?: if (neverModeNow()) store.loadOpen() else null
        if (creds != null && _state.value.session != null) {
            silentReSignIn(creds, message)
            return
        }
        hardEndSession(message)
    }

    private fun hardEndSession(message: String?) {
        memoryCredentials = null
        client = null
        alerts.clear()
        alertFilters.clear()
        activity.clear()
        retention.clear()
        sessions.clear()
        if (neverModeNow()) {
            // "Never" lock: just sign in again with the saved password.
            _state.update { UiState(form = it.form.copy(error = null, busy = true), locked = true, autoSignIn = true) }
            signInAgain()
            return
        }
        _state.update { UiState(form = it.form.copy(error = message, busy = false), locked = store.hasSaved) }
    }

    /** "Never" lock: signs in with the saved password, no fingerprint. Also the lock screen's Try again. */
    fun signInAgain() {
        if (signingInAgain) return
        val saved = store.loadOpen()
        val url = saved?.let { (ServerAddress.parse(it.baseUrl) as? AddressResult.Ok)?.url }
        if (saved == null || url == null) {
            _state.update { it.copy(locked = false, autoSignIn = false, form = it.form.copy(error = "The saved sign-in is no longer available. Please sign in again.", busy = false)) }
            return
        }
        signingInAgain = true
        _state.update { it.copy(locked = true, autoSignIn = true, form = it.form.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val newClient = BlueIrisClient(url)
            try {
                val info = newClient.login(saved.user, saved.password)
                client = newClient
                _state.update { UiState(form = FormState(it.form.address, it.form.username), session = newSession(info)) }
                persistSession(saved.baseUrl)
                loadCameras()
            } catch (e: LoginFailedException) {
                store.clear()
                sessions.clear()
                _state.update { it.copy(locked = false, autoSignIn = false, form = it.form.copy(error = "Blue Iris refused the saved sign-in. Please sign in again.", busy = false)) }
            } catch (e: BlueIrisException) {
                // Probably no network: stay on the "Signing in…" screen so Try again can retry.
                _state.update { it.copy(form = it.form.copy(error = e.message, busy = false)) }
            } finally {
                signingInAgain = false
            }
        }
    }

    // ---- downloading an alert as an MP4 ----

    private var downloadJob: Job? = null

    private fun setDownload(state: DownloadState?) {
        _state.update { s -> s.session?.let { s.copy(session = it.copy(download = state)) } ?: s }
    }

    /** Hides the download's last message once the owner has read it. */
    fun dismissDownload() {
        if (downloadJob?.isActive != true) setDownload(null)
    }

    private val folderFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** The name of a folder picked with the phone's folder picker, for showing in Settings. */
    private fun folderName(tree: android.net.Uri): String = runCatching {
        val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
        getApplication<Application>().contentResolver.query(doc, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: "Chosen folder"

    /** Saves downloads in [tree] (a folder the owner picked; the app keeps permission to it) or, when null, in the Downloads folder. */
    fun setDownloadFolder(tree: android.net.Uri?) {
        val resolver = getApplication<Application>().contentResolver
        val old = prefs.getString(KEY_DOWNLOAD_FOLDER, null)
        if (tree != null) runCatching { resolver.takePersistableUriPermission(tree, folderFlags) }
        if (old != null && old != tree?.toString()) runCatching { resolver.releasePersistableUriPermission(android.net.Uri.parse(old), folderFlags) }
        prefs.edit().apply { if (tree == null) remove(KEY_DOWNLOAD_FOLDER) else putString(KEY_DOWNLOAD_FOLDER, tree.toString()) }.apply()
        _state.update { s ->
            s.session?.let { s.copy(session = it.copy(downloadFolder = tree?.toString(), downloadFolderName = tree?.let { t -> folderName(t) })) } ?: s
        }
    }

    /** Says what is wrong when the chosen download folder can no longer be written to (checked before Blue Iris is asked to make anything). */
    private fun folderProblem(): String? {
        val tree = _state.value.session?.downloadFolder ?: return null
        val granted = getApplication<Application>().contentResolver.persistedUriPermissions.any { it.uri.toString() == tree && it.isWritePermission }
        return if (granted) null else "The download folder you chose is no longer available. Choose it again in Settings, or go back to the Downloads folder."
    }

    /** Remembers how exports should be made (re-encode or original, encoder profile, overlay, audio). */
    fun setExportOptions(options: ExportOptions) {
        prefs.edit()
            .putBoolean(KEY_EXPORT_REENCODE, options.reencode)
            .putInt(KEY_EXPORT_PROFILE, options.profile.coerceIn(0, 3))
            .putBoolean(KEY_EXPORT_AUDIO, options.audio)
            .putBoolean(KEY_EXPORT_OVERLAY, options.overlay)
            .apply()
        _state.update { s -> s.session?.let { s.copy(session = it.copy(export = options.copy(profile = options.profile.coerceIn(0, 3)))) } ?: s }
    }

    /**
     * Has Blue Iris prepare an MP4 of [alert], waits for it (Blue Iris re-encodes it, which can take a while), then saves
     * it in the phone's Downloads folder. It carries on if the player is closed; signing out or locking stops it.
     */
    fun startAlertDownload(alert: AlertItem, cameraName: String) {
        val session = _state.value.session ?: return
        val request = Exports.forAlert(alert, session.export)
        launchDownload(Exports.fileName(cameraName, alert.timeMs)) { request to null }
    }

    /**
     * Downloads the part of a camera's recordings in [range] (the owner chose its start and end on the timeline). The
     * recording file holding the start is found from Blue Iris's list of recordings, unless [own] (the alert being
     * watched) already holds it; a range reaching past the end of that file is shortened, and the owner is told.
     */
    fun startRangeDownload(camera: String, cameraName: String, range: ClipRange, own: AlertItem?) {
        val current = client ?: return
        val name = Exports.fileName(cameraName, range.startMs)
        launchDownload(name) {
            val options = _state.value.session?.export ?: ExportOptions()
            val ownFile = own?.takeIf { Exports.canExport(it) }?.let { RecordingFile(it.clip, it.timeMs - it.offsetMs, Long.MAX_VALUE) }
            val ownHoldsStart = ownFile != null && own != null &&
                range.startMs >= ownFile.startMs && range.startMs <= own.timeMs + AlertClips.durationMillis(own.fileSize)
            var file = if (ownHoldsStart) ownFile else null
            var listed = true
            if (file == null) {
                setDownload(DownloadState("Finding the recording…", null, name))
                val files = try {
                    current.recordingFiles(camera, range.startMs - 60_000L, minOf(range.endMs + 60_000L, System.currentTimeMillis()))
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (e: BlueIrisException) {
                    listed = false
                    emptyList()
                }
                file = RecordingFiles.find(files, range.startMs)
                // An older Blue Iris that will not list recordings can still export from the alert's own file.
                if (file == null && ownFile != null && range.startMs >= ownFile.startMs) file = ownFile
            }
            if (file == null) {
                val at = java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(range.startMs))
                throw BlueIrisException(
                    if (listed) "Blue Iris has no recording at $at."
                    else "This Blue Iris can only download from the alert's own recording" + (ownFile?.let { ", which starts at " + java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(it.startMs)) } ?: "") + ".",
                )
            }
            val cut = Exports.forRange(file, range, options)
            val note = if (cut.shortened) {
                "it stops at " + java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(cut.range.endMs)) + ", where that recording file ends"
            } else {
                null
            }
            cut.request to note
        }
    }

    /** Runs one download: [prepare] says what to export (and a note for the end of the message), then Blue Iris makes it and the phone saves it. */
    private fun launchDownload(name: String, prepare: suspend () -> Pair<ExportRequest, String?>) {
        if (downloadJob?.isActive == true) return
        val current = client ?: return
        folderProblem()?.let {
            setDownload(DownloadState(it, null, name, finished = true, failed = true))
            return
        }
        downloadJob = viewModelScope.launch {
            var jobPath = ""
            var note: String? = null
            try {
                setDownload(DownloadState("Asking Blue Iris to prepare the clip…", null, name))
                val (request, extra) = prepare()
                note = extra
                setDownload(DownloadState("Asking Blue Iris to prepare the clip…", null, name))
                // What is already queued, so that our own job can be told apart if Blue Iris names it differently.
                val before = try { current.exportQueue().map { ExportQueue.keyOf(it) }.toSet() } catch (e: BlueIrisException) { emptySet() }
                val started = current.startExport(request)
                jobPath = started.path
                val uri = waitForExport(current, started, before, name)
                // The file may take a moment to appear once Blue Iris has taken the job out of its queue.
                var attempt = 0
                while (true) {
                    try {
                        saveExport(current, uri, name)
                        break
                    } catch (e: SessionExpiredException) {
                        throw e
                    } catch (e: BlueIrisException) {
                        if (++attempt >= 6) throw BlueIrisException("Blue Iris prepared the clip but would not hand it over (${e.message})")
                        setDownload(DownloadState("Waiting for Blue Iris to finish writing the clip…", null, name))
                        delay(2_000)
                    }
                }
                val where = _state.value.session?.downloadFolderName ?: "Downloads"
                setDownload(DownloadState("Saved to $where: $name" + (note?.let { " ($it)" } ?: ""), 100, name, finished = true))
            } catch (e: CancellationException) {
                withContext(NonCancellable) { setDownload(DownloadState(cancelExport(current, jobPath), null, name, finished = true)) }
                throw e
            } catch (e: SessionExpiredException) {
                if (client === current) endSession(e.message)
                setDownload(null)
            } catch (e: BlueIrisException) {
                setDownload(DownloadState(e.message ?: "The download failed.", null, name, finished = true, failed = true))
            }
        }
    }

    /** Stops waiting for the clip and takes it out of Blue Iris's queue. */
    fun cancelDownload() {
        val job = downloadJob ?: return
        if (!job.isActive) return
        setDownload(_state.value.session?.download?.copy(text = "Cancelling…", percent = null))
        job.cancel()
    }

    /** Asks Blue Iris to drop the job and says how that went (older Blue Iris versions cannot stop an export). */
    private suspend fun cancelExport(current: BlueIrisClient, path: String): String {
        if (path.isEmpty()) return "Download cancelled."
        var gone = false
        try {
            current.deleteExport(path)
            delay(1_500)
            val item = current.exportQueue().firstOrNull { it.path == path }
            gone = item == null || item.status == "error"
        } catch (e: BlueIrisException) {
            // Best effort.
        }
        return if (gone) "Download cancelled." else "Cancelled here. This Blue Iris can't stop exports, so it will still finish in its export folder."
    }

    /** Polls Blue Iris's export queue until the clip is ready and returns where it is; gives up when it stops making progress. */
    private suspend fun waitForExport(current: BlueIrisClient, started: ExportStart, before: Set<String>, name: String): String {
        val t0 = SystemClock.elapsedRealtime()
        var moved = t0
        var seen = ""
        var misses = 0
        var errors = 0
        var everSeen = false
        var emptyPolls = 0
        var lastUri = ""
        while (true) {
            delay(1_500)
            val now = SystemClock.elapsedRealtime()
            if (now - moved > 180_000L || now - t0 > 3_600_000L) {
                throw BlueIrisException("Blue Iris stopped making progress on this clip. Try again later, or choose \"Original recording\".")
            }
            val queue = try {
                current.exportQueue().also { errors = 0 }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (e: BlueIrisException) {
                if (++errors > 5) throw e
                continue
            }
            val item = ExportQueue.find(queue, started, before)
            if (item == null) {
                // Blue Iris takes a finished job out of its queue (a copy of the original recording is done at once and
                // may never be listed). So a job that has gone, after being seen or after a few looks, is finished: its
                // file is where Blue Iris said when it took the export.
                emptyPolls++
                if (started.uri.isNotEmpty() && ((everSeen && emptyPolls >= 2) || emptyPolls >= 3)) return lastUri.ifEmpty { started.uri }
                if (++misses > 20) {
                    // Say what Blue Iris answered (record names only), so a different reply shape can be seen and fixed.
                    throw BlueIrisException("Couldn't find the clip in Blue Iris's export queue. " + ExportQueue.describe(queue, started))
                }
                continue
            }
            everSeen = true
            emptyPolls = 0
            if (item.uri.isNotEmpty()) lastUri = item.uri
            val key = item.status + ":" + item.progress.roundToInt()
            if (key != seen) {
                seen = key
                moved = now
            }
            when (item.status) {
                "done" -> return item.uri.ifEmpty { started.uri }.ifEmpty { throw BlueIrisException("Blue Iris didn't say where the clip was saved.") }
                "error" -> throw BlueIrisException("Blue Iris couldn't export this alert.")
                "active" -> setDownload(DownloadState("Preparing the clip… ${item.progress.roundToInt()}%", item.progress.roundToInt(), name))
                else -> setDownload(DownloadState("Waiting in Blue Iris's export queue…", null, name))
            }
        }
    }

    /** Copies the finished clip from Blue Iris into the Downloads folder (no storage permission needed on Android 10 and later). */
    private suspend fun saveExport(current: BlueIrisClient, uri: String, name: String) {
        val resolver = getApplication<Application>().contentResolver
        val tree = _state.value.session?.downloadFolder?.let { android.net.Uri.parse(it) }
        if (tree != null) saveToFolder(resolver, tree, current, uri, name) else saveToDownloads(resolver, current, uri, name)
    }

    private suspend fun saveToDownloads(resolver: android.content.ContentResolver, current: BlueIrisClient, uri: String, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw BlueIrisException("This phone would not let the app save to Downloads.")
        try {
            val out = resolver.openOutputStream(target) ?: throw BlueIrisException("This phone would not let the app save to Downloads.")
            out.use { stream -> fetchInto(current, uri, name, stream) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(target, null, null) } // never leave half a file behind
            throw e
        }
    }

    /** Saves into the folder the owner chose; the folder's provider renames the file if the name is already taken. */
    private suspend fun saveToFolder(resolver: android.content.ContentResolver, tree: android.net.Uri, current: BlueIrisClient, uri: String, name: String) {
        val problem = "This phone would not let the app save to the folder you chose. Choose it again in Settings."
        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
        val target = try {
            android.provider.DocumentsContract.createDocument(resolver, parent, "video/mp4", name)
        } catch (e: Exception) {
            null
        } ?: throw BlueIrisException(problem)
        try {
            val out = resolver.openOutputStream(target) ?: throw BlueIrisException(problem)
            out.use { stream -> fetchInto(current, uri, name, stream) }
        } catch (e: Throwable) {
            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) } // never leave half a file behind
            throw e
        }
    }

    /** Copies the finished clip from Blue Iris into [stream], showing the progress. */
    private suspend fun fetchInto(current: BlueIrisClient, uri: String, name: String, stream: java.io.OutputStream) {
        var lastPercent = -2
        var lastAt = 0L
        current.downloadExport(uri, stream) { done, total ->
            val percent = if (total > 0) (done * 100 / total).toInt() else -1
            val at = SystemClock.elapsedRealtime()
            if (percent != lastPercent && at - lastAt > 250) {
                lastPercent = percent
                lastAt = at
                val megabytes = done / 1_000_000
                setDownload(DownloadState(if (percent >= 0) "Downloading… $percent% ($megabytes MB)" else "Downloading… $megabytes MB", percent.takeIf { it >= 0 }, name))
            }
        }
    }

    // ---- polling while the grid is on screen ----

    private var inForeground = true

    /**
     * Blue Iris's own motion and trigger state, from a fresh camera list (the web app asks every 2 s). A network
     * failure only marks the connection as down (the grid shows "Reconnecting"); an ended session signs in again.
     */
    suspend fun pollStatus() {
        val current = client ?: return
        try {
            val list = current.cameras()
            _state.update { s ->
                s.session?.let {
                    if (it.cameras == list && it.online) s else s.copy(session = it.copy(cameras = list, online = true, loading = false))
                } ?: s
            }
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
        } catch (e: BlueIrisException) {
            _state.update { s -> s.session?.let { if (it.online) s.copy(session = it.copy(online = false)) else s } ?: s }
        }
    }

    /** Blue Iris's system log for the log screen. Null if the session ended. */
    suspend fun loadLog(): com.feerlessleadr.sightline.data.LogResult? {
        val current = client ?: return null
        return try {
            com.feerlessleadr.sightline.data.LogResult.Ok(current.systemLog())
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            null
        } catch (e: com.feerlessleadr.sightline.data.AccessDeniedException) {
            com.feerlessleadr.sightline.data.LogResult.Denied(e.message.orEmpty())
        } catch (e: BlueIrisException) {
            com.feerlessleadr.sightline.data.LogResult.Failed(e.message.orEmpty())
        }
    }

    /** Blue Iris's status and its cameras, for the Server status screen; null if they cannot be had right now. */
    suspend fun serverView(): Pair<com.feerlessleadr.sightline.data.ServerStatus, List<com.feerlessleadr.sightline.data.Camera>>? {
        val current = client ?: return null
        return try {
            current.serverStatus() to current.cameras().cameras
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            null
        } catch (e: BlueIrisException) {
            null
        }
    }

    /** Alerts from the last two minutes (the web app asks every 4 s) for the AI labels; empty if they cannot be had. */
    suspend fun recentAlerts(): List<RecentAlert> {
        val current = client ?: return emptyList()
        return try {
            current.recentAlerts()
        } catch (e: SessionExpiredException) {
            if (client === current) endSession(e.message)
            emptyList()
        } catch (e: BlueIrisException) {
            emptyList()
        }
    }

    /** Sign in again now instead of waiting (the banner's "Sign in again"). */
    fun signInAgainNow() {
        hardEndSession("Please sign in again.")
    }

    private fun startKeepAlive() {
        viewModelScope.launch {
            while (true) {
                delay(40_000)
                val current = client
                if (current != null && inForeground) {
                    try {
                        current.keepAlive()
                    } catch (e: SessionExpiredException) {
                        if (client === current) endSession(e.message)
                    } catch (e: BlueIrisException) {
                        // A network problem shows up on the grid's own polling.
                    }
                }
            }
        }
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
        const val KEY_ALERT_FILTERS = "alert_filters"
        const val KEY_POPUP = "popup_config"
        const val KEY_WEB_PAGE = "web_page"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_GROUP_DEBUG = "group_debug"
        const val KEY_ALERT_PREVIEW = "alert_preview"
        const val KEY_HIDDEN_GROUPS = "hidden_groups"
        const val KEY_ADDRESS = "address"
        const val KEY_USER = "username"
        const val KEY_TILE_SIZE = "tile_size_"
        const val KEY_ORDER = "tile_order_"
        const val KEY_SHAPES = "tile_shapes_"
        const val KEY_LIVE_VIDEO = "live_video"
        const val KEY_HIGHLIGHT = "highlight_active"
        const val KEY_AI_LABELS = "ai_labels"
        const val KEY_MOTION_BOXES = "motion_boxes"
        const val KEY_SOUND_ON = "sound_on"
        const val KEY_DOWNLOAD_FOLDER = "download_folder"
        const val KEY_EXPORT_REENCODE = "export_reencode"
        const val KEY_EXPORT_PROFILE = "export_profile"
        const val KEY_EXPORT_AUDIO = "export_audio"
        const val KEY_EXPORT_OVERLAY = "export_overlay"
        const val KEY_VOLUME = "sound_volume"
        const val KEY_MOTION_SENS = "motion_sensitivity"
        const val KEY_QUALITY = "video_quality"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_LAST_STOP = "last_stop_millis"
    }
}

/**
 * The cameras to swipe through while watching [current]: the ones shown in the grid's group, in the grid's order
 * (including any order the owner made), or every shown camera if [current] is not among them (as the web app does).
 */
fun SessionState.playerCameras(current: String): List<Camera> {
    val list = cameras ?: return emptyList()
    // Watching a group: swiping moves between the groups that have a layout (as the web app does).
    if (list.groups.any { it.optionValue == current }) {
        return list.groups.filter { it.isGroup && it.rects != null && (it.width ?: 0) > 0 }
    }
    val group = list.groups.firstOrNull { it.optionValue == selectedGroup } ?: list.groups.firstOrNull { it.optionValue == "index" }
    val key = group?.optionValue ?: "index"
    val shown = (arrangements[key] ?: GroupArrangement()).sorted(list.visibleCameras(group, showHidden)) { it.optionValue }
    return if (shown.any { it.optionValue == current }) shown else list.visibleCameras(null, showHidden)
}

/**
 * The group to open when the group button is pressed while watching the camera [current] (as the web app chooses): the
 * grid's own group if it holds the camera, else All cameras, else the first group that does. Only groups Blue Iris
 * gives a layout for are used.
 */
fun SessionState.groupFor(current: String): Camera? {
    val list = cameras ?: return null
    fun usable(g: Camera) = g.isGroup && g.rects != null && (g.width ?: 0) > 0 && (g.height ?: 0) > 0
    fun holds(g: Camera) = g.group?.any { it.equals(current, ignoreCase = true) } == true
    val grid = list.groups.firstOrNull { it.optionValue == selectedGroup }
    if (grid != null && usable(grid) && holds(grid)) return grid
    list.groups.firstOrNull { it.optionValue == "index" && usable(it) }?.let { return it }
    return list.groups.firstOrNull { usable(it) && holds(it) }
}

/** The camera to go back to from the group [group]: the one the group view was opened from, else the first shown camera of the group. */
fun SessionState.cameraToReturnTo(group: Camera, openedFrom: String?): Camera? {
    val list = cameras ?: return null
    val shown = list.visibleCameras(group, showHidden)
    return shown.firstOrNull { it.optionValue == openedFrom } ?: shown.firstOrNull()
}
