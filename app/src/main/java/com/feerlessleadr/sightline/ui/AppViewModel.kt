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
import com.feerlessleadr.sightline.data.LockAfter
import com.feerlessleadr.sightline.data.LockPolicy
import com.feerlessleadr.sightline.data.LoginFailedException
import com.feerlessleadr.sightline.data.RecentAlert
import com.feerlessleadr.sightline.data.AlertItem
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
        if (left == 0L || _state.value.session == null || !store.hasSaved) return
        if (!LockPolicy.stillUnlocked(lockAfter, left, System.currentTimeMillis())) lockNow()
    }

    private fun lockNow() {
        val old = client
        client = null
        memoryCredentials = null
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

    /** The player's calls to Blue Iris (live and recorded video, stills, timeline, alerts). */
    val playerApi = PlayerApi(
        openLive = { camera, size, audio -> guarded { it.openVideo(camera, size, audio) } },
        openRecorded = { camera, pos, speed, size, audio -> guarded { it.openRecorded(camera, pos, speed, size, audio) } },
        liveStill = { camera, width -> fetchStill(camera, width) },
        openClip = { file, offset, speed, size, audio -> guarded { it.openClip(file, offset, speed, size, audio) } },
        recordedStill = { camera, pos, width -> guarded { it.recordedStill(camera, pos, width) } },
        timeline = { camera, start, end, step -> guarded { it.timeline(camera, start, end, step) } },
        alerts = { camera, start, end -> guarded { it.cameraAlerts(camera, start, end) } },
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
        memoryCredentials = null
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
        const val KEY_VOLUME = "sound_volume"
        const val KEY_MOTION_SENS = "motion_sensitivity"
        const val KEY_QUALITY = "video_quality"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_LAST_STOP = "last_stop_millis"
    }
}
