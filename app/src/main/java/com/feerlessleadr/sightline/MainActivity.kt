package com.feerlessleadr.sightline

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.feerlessleadr.sightline.ui.AppViewModel
import com.feerlessleadr.sightline.ui.CameraListScreen
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
        setContent {
            SightlineTheme {
                val state by viewModel.state.collectAsState()
                val session = state.session

                // A sign-in the owner asked to remember: the fingerprint authorises saving it.
                LaunchedEffect(state.pendingSave != null) {
                    if (state.pendingSave != null) {
                        val cipher = viewModel.saveCipher()
                        if (cipher == null) {
                            viewModel.cancelSave()
                        } else {
                            promptFingerprint(
                                title = "Remember this sign-in",
                                cipher = cipher,
                                onSuccess = viewModel::completeSave,
                                onFailure = viewModel::cancelSave,
                            )
                        }
                    }
                }

                when {
                    session != null -> CameraListScreen(
                        session = session,
                        onSelectGroup = viewModel::selectGroup,
                        onRefresh = viewModel::refresh,
                        loadStill = viewModel::fetchStill,
                        onSignOut = viewModel::signOut,
                    )
                    state.locked -> LockScreen(
                        form = state.form,
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

    private fun canUseFingerprint(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun unlock() {
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
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
