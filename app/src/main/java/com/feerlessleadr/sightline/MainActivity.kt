package com.feerlessleadr.sightline

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
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
import com.feerlessleadr.sightline.ui.LiveVideoScreen
import com.feerlessleadr.sightline.ui.LockScreen
import com.feerlessleadr.sightline.ui.LoginScreen
import com.feerlessleadr.sightline.ui.SightlineTheme
import javax.crypto.Cipher

// FragmentActivity (a ComponentActivity) is what BiometricPrompt needs.
class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                var showSettings by rememberSaveable { mutableStateOf(false) }
                // Leaving the signed-in state (sign-out, lock, expired session) also leaves these screens.
                LaunchedEffect(session == null) {
                    if (session == null) {
                        liveCamera = null
                        showSettings = false
                    }
                }
                val live = session?.cameras?.cameras?.firstOrNull { it.optionValue == liveCamera }
                BackHandler(enabled = live != null) { liveCamera = null }
                BackHandler(enabled = live == null && showSettings) { showSettings = false }

                when {
                    session != null && live != null -> LiveVideoScreen(
                        camera = live,
                        quality = session.quality,
                        openVideo = viewModel::openVideo,
                        loadStill = viewModel::fetchStill,
                        onQuality = viewModel::setQuality,
                        onBack = { liveCamera = null },
                    )
                    session != null && showSettings -> SettingsScreen(
                        session = session,
                        onBack = { showSettings = false },
                        onLiveVideo = viewModel::setLiveVideo,
                        onQuality = viewModel::setQuality,
                        onLockAfter = ::changeLock,
                        onForgetSaved = viewModel::forgetSaved,
                        onProbe = viewModel::probeVideo,
                        onSignOut = viewModel::signOut,
                    )
                    session != null -> CameraListScreen(
                        session = session,
                        onSelectGroup = viewModel::selectGroup,
                        onRefresh = viewModel::refresh,
                        loadStill = viewModel::fetchStill,
                        openVideo = viewModel::openVideo,
                        onOpenCamera = { liveCamera = it.optionValue },
                        onTileSize = viewModel::setTileSize,
                        onLiveVideo = viewModel::setLiveVideo,
                        onSettings = { showSettings = true },
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
