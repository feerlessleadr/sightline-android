package com.feerlessleadr.sightline

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.DeepLink
import com.feerlessleadr.sightline.ui.AlertsActions
import com.feerlessleadr.sightline.ui.HomeTab
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.feerlessleadr.sightline.data.LockAfter
import com.feerlessleadr.sightline.ui.SettingsScreen
import com.feerlessleadr.sightline.ui.AppViewModel
import com.feerlessleadr.sightline.ui.CameraListScreen
import com.feerlessleadr.sightline.ui.PlayerScreen
import com.feerlessleadr.sightline.ui.cameraToReturnTo
import com.feerlessleadr.sightline.ui.groupFor
import com.feerlessleadr.sightline.ui.playerCameras
import com.feerlessleadr.sightline.ui.LockScreen
import com.feerlessleadr.sightline.ui.LoginScreen
import com.feerlessleadr.sightline.ui.SightlineTheme
import javax.crypto.Cipher

// FragmentActivity (a ComponentActivity) is what BiometricPrompt needs.
class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var prompting = false

    /** A notification link waiting to be opened (see [DeepLink]). */
    private var pendingLink by mutableStateOf<DeepLink?>(null)

    /** Opens a web address in the phone's browser. */
    private fun openInBrowser(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "No browser found to open that.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.parse(intent.data?.toString())?.let { pendingLink = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (after the system stopped the app) must not open the same link again.
        if (savedInstanceState == null) pendingLink = DeepLink.parse(intent?.data?.toString())
        // Dark only, like the web app: light icons on the status and navigation bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Coming back after the lock time (or always, by default) needs the fingerprint again.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> viewModel.onBackgrounded(isChangingConfigurations)
                    Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                    else -> Unit
                }
            },
        )
        setContent {
            SightlineTheme {
                val state by viewModel.state.collectAsState()
                val session = state.session

                // A sign-in the owner asked to remember: the fingerprint authorises saving it.
                LaunchedEffect(state.pendingSave != null) {
                    if (state.pendingSave != null) {
                        val cipher = viewModel.saveCipher()
                        if (cipher != null) {
                            promptFingerprint(
                                title = "Remember this sign-in",
                                cipher = cipher,
                                onSuccess = viewModel::completeSave,
                                onFailure = { viewModel.cancelSave() },
                            )
                        }
                    }
                }

                var liveCamera by rememberSaveable { mutableStateOf<String?>(null) }
                // An alert opened from the Alerts tab: the player starts at it. Cleared when the camera changes or the player closes.
                var alertToPlay by remember { mutableStateOf<AlertItem?>(null) }
                var homeTab by rememberSaveable { mutableStateOf(HomeTab.Live) }
                val alertsUi by viewModel.alerts.state.collectAsState()
                val alertsListState = rememberLazyListState()
                val alertsActions = remember(viewModel) {
                    AlertsActions(
                        ensureLoaded = viewModel.alerts::ensureLoaded,
                        reload = viewModel.alerts::reload,
                        loadMore = viewModel.alerts::loadMore,
                        select = viewModel.alerts::select,
                        setCamera = viewModel.alerts::setCamera,
                        setRange = viewModel.alerts::setRange,
                        lookFurtherBack = viewModel.alerts::lookFurtherBack,
                        retry = viewModel.alerts::retry,
                        thumbnail = viewModel::alertThumbnail,
                        open = { alert ->
                            val camera = viewModel.state.value.session?.cameras?.cameras?.firstOrNull { it.optionValue.equals(alert.camera, ignoreCase = true) }
                            if (camera != null) {
                                alertToPlay = alert
                                liveCamera = camera.optionValue
                            }
                        },
                        changeFlags = viewModel::changeAlertFlags,
                        retainFilters = viewModel.alerts::retainFilters,
                        filterSettings = viewModel.alertFilters,
                    )
                }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showServer by rememberSaveable { mutableStateOf(false) }
                var showLog by rememberSaveable { mutableStateOf(false) }
                var showActivity by rememberSaveable { mutableStateOf(false) }
                var showAbout by rememberSaveable { mutableStateOf(false) }
                var showLegend by rememberSaveable { mutableStateOf(false) }
                var logProblems by rememberSaveable { mutableStateOf(false) }
                // Leaving the signed-in state (sign-out, lock, expired session) also leaves these screens.
                LaunchedEffect(session == null) {
                    if (session == null) {
                        liveCamera = null
                        showSettings = false
                        showServer = false
                        showLog = false
                        showActivity = false
                        showAbout = false
                        showLegend = false
                    }
                }
                // The player shows a camera, or a whole group (which Blue Iris offers as one combined picture).
                var groupFrom by rememberSaveable { mutableStateOf<String?>(null) }
                val live = session?.cameras?.let { list -> list.cameras.firstOrNull { it.optionValue == liveCamera } ?: list.groups.firstOrNull { it.optionValue == liveCamera } }
                // A link from a notification (sightline://...): waits here until the app is unlocked and the camera list is in.
                val link = pendingLink
                val ready = session?.cameras != null
                LaunchedEffect(link, ready) {
                    if (link == null || !ready || session == null) return@LaunchedEffect
                    // The link is cleared only at the end: clearing it restarts this effect, which would cancel the alert lookup.
                    showSettings = false
                    showServer = false
                    showLog = false
                    showActivity = false
                    showAbout = false
                    showLegend = false
                    groupFrom = null
                    val cams = session.cameras?.cameras.orEmpty()
                    fun find(name: String) = cams.firstOrNull { it.optionValue.equals(name, ignoreCase = true) && it.isEnabled }
                    fun say(text: String) = android.widget.Toast.makeText(this@MainActivity, text, android.widget.Toast.LENGTH_LONG).show()
                    when (link) {
                        DeepLink.Alerts -> { liveCamera = null; alertToPlay = null; homeTab = HomeTab.Alerts }
                        DeepLink.Cameras -> { liveCamera = null; alertToPlay = null; homeTab = HomeTab.Live }
                        is DeepLink.Live -> {
                            alertToPlay = null
                            val camera = find(link.camera)
                            if (camera != null) liveCamera = camera.optionValue else { liveCamera = null; say("No camera called ${link.camera}") }
                        }
                        is DeepLink.Alert -> {
                            val camera = find(link.camera)
                            if (camera == null) {
                                liveCamera = null
                                alertToPlay = null
                                say("No camera called ${link.camera}")
                            } else {
                                val alert = viewModel.findAlert(camera.optionValue, link.id)
                                alertToPlay = alert
                                liveCamera = camera.optionValue
                                if (alert == null) say("Couldn't find that alert, showing the live view")
                            }
                        }
                    }
                    pendingLink = null
                }
                BackHandler(enabled = live != null) { liveCamera = null }
                BackHandler(enabled = live == null && showSettings) { showSettings = false }
                BackHandler(enabled = live == null && showServer) { showServer = false }
                BackHandler(enabled = live == null && showLog) { showLog = false }
                BackHandler(enabled = live == null && showActivity) { showActivity = false }
                BackHandler(enabled = live == null && showAbout) { showAbout = false }
                BackHandler(enabled = live == null && showLegend) { showLegend = false }

                when {
                    // Keyed so that a link to another alert on the same camera starts the player afresh.
                    session != null && live != null -> androidx.compose.runtime.key(live.optionValue, alertToPlay?.path) { PlayerScreen(
                        camera = live,
                        quality = session.quality,
                        api = viewModel.playerApi,
                        cameras = session.playerCameras(live.optionValue),
                        onSwitchCamera = { target ->
                            // Remember where the group view was opened from, so its button can go back there.
                            if (target.isGroup && !live.isGroup) groupFrom = live.optionValue
                            if (!target.isGroup) groupFrom = null
                            alertToPlay = null
                            liveCamera = target.optionValue
                        },
                        groupTarget = if (live.isGroup) session.cameraToReturnTo(live, groupFrom) else session.groupFor(live.optionValue),
                        findCamera = { name ->
                            session.cameras?.cameras?.firstOrNull { it.optionValue.equals(name, ignoreCase = true) && it.isEnabled && !it.hidden }
                        },
                        motionBoxes = session.motionBoxes,
                        sensitivity = session.motionSensitivity,
                        onMotionBoxes = viewModel::setMotionBoxes,
                        soundOn = session.soundOn,
                        volume = session.volume,
                        onSound = viewModel::setSound,
                        exportOptions = session.export,
                        onExportOptions = viewModel::setExportOptions,
                        download = session.download,
                        onDownloadAlert = viewModel::startAlertDownload,
                        onDownloadRange = viewModel::startRangeDownload,
                        onCancelDownload = viewModel::cancelDownload,
                        onDismissDownload = viewModel::dismissDownload,
                        onQuality = viewModel::setQuality,
                        onBack = { liveCamera = null; alertToPlay = null },
                        initialAlert = alertToPlay,
                    ) }
                    session != null && showLegend -> com.feerlessleadr.sightline.ui.ColorLegendScreen(onBack = { showLegend = false })
                    session != null && showAbout -> com.feerlessleadr.sightline.ui.AboutScreen(
                        session = session,
                        username = state.form.username,
                        onOpenUrl = ::openInBrowser,
                        onBack = { showAbout = false },
                    )
                    session != null && showActivity -> com.feerlessleadr.sightline.ui.ActivityScreen(
                        feed = viewModel.activity,
                        cameras = session.cameras?.cameras.orEmpty(),
                        onShowAlerts = { camera, type ->
                            showActivity = false
                            showAbout = false
                            showLegend = false
                            showSettings = false
                            viewModel.alerts.setCamera(camera)
                            viewModel.alerts.select(type, false)
                            homeTab = HomeTab.Alerts
                        },
                        onBack = { showActivity = false },
                    )
                    session != null && showLog -> com.feerlessleadr.sightline.ui.LogScreen(
                        firstFilter = if (logProblems) com.feerlessleadr.sightline.data.LogFilter.Problems else com.feerlessleadr.sightline.data.LogFilter.All,
                        load = viewModel::loadLog,
                        onBack = { showLog = false },
                    )
                    session != null && showServer -> com.feerlessleadr.sightline.ui.ServerStatusScreen(
                        serverName = session.info.systemName,
                        version = session.info.version,
                        load = viewModel::serverView,
                        onOpenLog = { logProblems = true; showLog = true },
                        onBack = { showServer = false },
                    )
                    session != null && showSettings -> SettingsScreen(
                        session = session,
                        onBack = { showSettings = false },
                        onLiveVideo = viewModel::setLiveVideo,
                        onHighlight = viewModel::setHighlight,
                        onAiLabels = viewModel::setAiLabels,
                        onMotionBoxes = viewModel::setMotionBoxes,
                        onMotionSensitivity = viewModel::setMotionSensitivity,
                        onPopup = viewModel::setPopup,
                        filterSettings = viewModel.alertFilters,
                        onQuality = viewModel::setQuality,
                        onResetLayout = { viewModel.resetLayout(session.selectedGroup ?: "index") },
                        onDownloadFolder = viewModel::setDownloadFolder,
                        onOpeningPicker = viewModel::openingPicker,
                        onLockAfter = ::changeLock,
                        onForgetSaved = viewModel::forgetSaved,
                        onProbe = viewModel::probeVideo,
                        onSignOut = viewModel::signOut,
                        onServerStatus = { showServer = true },
                        onSystemLog = { logProblems = false; showLog = true },
                        onActivity = { showActivity = true },
                        onAbout = { showAbout = true },
                        onLegend = { showLegend = true },
                        onOpenWebPage = { viewModel.webPageUrl()?.let(::openInBrowser) ?: android.widget.Toast.makeText(this@MainActivity, "Can't make the web page address. Check the file name in Settings.", android.widget.Toast.LENGTH_LONG).show() },
                        onWebPage = viewModel::setWebPage,
                    )
                    session != null -> CameraListScreen(
                        session = session,
                        onSelectGroup = viewModel::selectGroup,
                        onRefresh = viewModel::refresh,
                        loadStill = viewModel::fetchStill,
                        openVideo = viewModel::openVideo,
                        onOpenCamera = { liveCamera = it.optionValue },
                        onTileSize = viewModel::setTileSize,
                        onArrangement = viewModel::setArrangement,
                        onResetLayout = viewModel::resetLayout,
                        onLiveVideo = viewModel::setLiveVideo,
                        onMotionBoxes = viewModel::setMotionBoxes,
                        onPollStatus = viewModel::pollStatus,
                        onRecentAlerts = viewModel::recentAlerts,
                        onSignInAgain = viewModel::signInAgainNow,
                        onSettings = { showSettings = true },
                        tab = homeTab,
                        onTab = { homeTab = it },
                        alerts = alertsUi,
                        alertsListState = alertsListState,
                        alertsActions = alertsActions,
                    )
                    state.locked -> LockScreen(
                        form = state.form,
                        autoSignIn = state.autoSignIn,
                        onUnlock = ::unlock,
                        onUsePassword = viewModel::usePassword,
                        onForget = viewModel::forgetSaved,
                    )
                    else -> LoginScreen(
                        form = state.form,
                        canRemember = canUseFingerprint(),
                        onSignIn = viewModel::signIn,
                    )
                }
            }
        }
    }

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True if the phone has a strong biometric or a screen lock (PIN, pattern or password) set up. */
    private fun canUseFingerprint(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** Lock setting changed. Moving to or from "Never" re-saves the sign-in, which needs one fingerprint check. */
    private fun changeLock(value: LockAfter) {
        val current = viewModel.state.value.session?.lockAfter ?: return
        when {
            value == LockAfter.Never && current != LockAfter.Never -> {
                val cipher = viewModel.neverCipher() ?: return
                promptFingerprint("Stop asking for the fingerprint", cipher, viewModel::completeNever, onFailure = {})
            }
            current == LockAfter.Never && value != LockAfter.Never -> {
                val cipher = viewModel.protectCipher() ?: return
                promptFingerprint("Protect the sign-in with the fingerprint", cipher, { viewModel.completeProtect(it, value) }, onFailure = {})
            }
            else -> viewModel.setLockAfter(value)
        }
    }

    private fun unlock() {
        if (viewModel.state.value.autoSignIn) {
            viewModel.signInAgain()
            return
        }
        if (prompting || viewModel.state.value.form.busy) return
        val cipher = viewModel.unlockCipher() ?: return
        promptFingerprint(
            title = "Unlock Sightline",
            cipher = cipher,
            onSuccess = viewModel::completeUnlock,
            onFailure = {},
        )
    }

    private fun promptFingerprint(title: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFailure: () -> Unit) {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    val authorised = result.cryptoObject?.cipher
                    if (authorised != null) onSuccess(authorised) else onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    onFailure()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
