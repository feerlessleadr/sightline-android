package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown at launch when a sign-in is saved: a fingerprint unlocks it and signs in. */
@Composable
fun LockScreen(
    form: FormState,
    autoSignIn: Boolean,
    onUnlock: () -> Unit,
    onUsePassword: () -> Unit,
    onForget: () -> Unit,
) {
    // Ask for the fingerprint as soon as the screen appears.
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(Modifier.fillMaxWidth()) {
            BrandBlock(if (autoSignIn) "Signing in to Blue Iris…" else "Locked. Use your fingerprint to sign in.")
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }
            Button(onClick = onUnlock, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
                if (form.busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text(if (autoSignIn) "Try again" else "Unlock")
            }
            TextButton(onClick = onUsePassword, enabled = !form.busy) { Text("Use password instead") }
            TextButton(onClick = onForget, enabled = !form.busy) { Text("Forget saved sign-in") }
        }
    }
}
