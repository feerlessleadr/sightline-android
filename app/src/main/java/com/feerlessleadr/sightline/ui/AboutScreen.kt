package com.feerlessleadr.sightline.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.BuildConfig
import com.feerlessleadr.sightline.data.Diagnostics

private const val PROJECT = "https://github.com/feerlessleadr/sightline-android"
private const val WEB_PROJECT = "https://github.com/rw377/Sightline-for-Blue-Iris"

/** About: versions, where to find the project, copy diagnostics for a problem report, and what the app is and is not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(session: SessionState, username: String, onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val cameras = session.cameras?.cameras.orEmpty()
    val rows = listOf(
        "Blue Iris server" to (session.info.systemName ?: "Unknown"),
        "Blue Iris version" to (session.info.version ?: "Not reported"),
        "Cameras" to cameras.count { !it.isGroup }.toString(),
        "Signed in as" to username.ifEmpty { "This session" },
        "Saved sign-in" to if (session.savedSignIn) "On" else "Off",
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("About") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readable().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Sightline for Android", style = MaterialTheme.typography.headlineSmall, color = Sl.Text)
            Text("Version ${BuildConfig.VERSION_NAME} · for Blue Iris", color = Sl.Muted, style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.padding(top = 12.dp)) {
                for ((k, v) in rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, color = Sl.Muted)
                        Text(v, color = Sl.Text, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            Column(Modifier.padding(top = 8.dp)) {
                LinkRow("Project page", "GitHub ↗") { onOpenUrl(PROJECT) }
                LinkRow("What’s new", "Releases ↗") { onOpenUrl("$PROJECT/releases") }
                LinkRow("Report a problem", "Issues ↗") { onOpenUrl("$PROJECT/issues") }
                LinkRow("The web version this app follows", "GitHub ↗") { onOpenUrl(WEB_PROJECT) }
            }
            Button(
                onClick = {
                    val settings = listOf(
                        "quality" to session.quality.label,
                        "live video" to yn(session.liveVideo),
                        "highlights" to yn(session.highlight),
                        "AI labels" to yn(session.aiLabels),
                        "motion boxes" to (yn(session.motionBoxes) + " (sensitivity ${session.motionSensitivity})"),
                        "pop-ups" to (if (session.popup.isOn) "on" else "off"),
                        "lock" to session.lockAfter.label,
                        "saved sign-in" to yn(session.savedSignIn),
                    )
                    val text = Diagnostics.text(BuildConfig.VERSION_NAME, session.info.version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}", cameras, settings)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sightline diagnostics", text))
                    Toast.makeText(context, "Diagnostics copied. Paste them into your GitHub issue.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Copy diagnostics") }
            Text(
                "For reporting a problem: versions, the phone, camera counts and settings. No camera names, addresses or passwords.",
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Paragraph("Unofficial.", "An independent viewer for Blue Iris. It is not affiliated with, endorsed by or supported by Perspective Software, the maker of Blue Iris, or by the author of the web Sightline. Blue Iris is a trademark of its owner. Please don’t contact Blue Iris support about problems with this app.")
            Paragraph("Private.", "This app talks only to your own Blue Iris server, over HTTPS. No accounts, cloud services, tracking or analytics. Settings are saved on this phone.")
            Paragraph("View-only.", "Recording, motion detection, AI and camera settings all stay in Blue Iris. Use Blue Iris or its web page to change them.")
            Paragraph("Free software.", "Licensed under the GNU GPL v3: free to use, change and share. Shared or modified versions must stay under the same licence, with source included. Provided as is, without warranty.")
            Paragraph("Credits.", "Follows the behaviour of Sightline for Blue Iris by rw377 (GPL v3); no code was copied. Video is played with Android’s own decoder.")
        }
    }
}

private fun yn(b: Boolean) = if (b) "yes" else "no"

@Composable
private fun LinkRow(title: String, where: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Sl.Text)
        Text(where, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Paragraph(head: String, body: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Sl.Text))
            append("$head ")
            pop()
            append(body)
        },
        color = Sl.Muted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
