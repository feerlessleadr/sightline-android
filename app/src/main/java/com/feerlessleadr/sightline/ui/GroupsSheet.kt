package com.feerlessleadr.sightline.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.Camera
import androidx.compose.foundation.layout.fillMaxWidth

/** "Groups on Live screen": which of Blue Iris's camera groups get a button above the cameras. At least one stays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsSheet(groups: List<Camera>, hidden: Set<String>, onToggle: (String) -> Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Groups on Live screen", style = MaterialTheme.typography.titleLarge, color = Sl.Text)
            Text(
                "Choose which group buttons appear above the cameras. These come from the camera groups set up in Blue Iris. With only one shown, the button row is hidden.",
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (groups.isEmpty()) {
                Text("Blue Iris has no camera groups yet, so the Live screen shows every camera.", color = Sl.Muted)
            }
            for (g in groups) {
                val count = g.group?.size ?: 0
                ToggleRow(
                    title = g.displayName,
                    sub = if (g.optionValue == "index") "Every camera" else "$count camera${if (count == 1) "" else "s"}",
                    on = g.optionValue !in hidden,
                    onRemove = null,
                ) {
                    if (!onToggle(g.optionValue)) Toast.makeText(context, "Keep at least one group", Toast.LENGTH_SHORT).show()
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Done") }
        }
    }
}
