package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.Camera

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFF9A825)
private val Grey = Color(0xFF757575)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    session: SessionState,
    onSelectGroup: (String?) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.info.systemName ?: "Sightline") },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val list = session.cameras
            val groups = list?.groups.orEmpty()
            val selected = groups.firstOrNull { it.optionValue == session.selectedGroup }
                ?: groups.firstOrNull { it.optionValue == "index" }
            if (groups.size > 1) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.optionValue }) { g ->
                        FilterChip(
                            selected = g.optionValue == selected?.optionValue,
                            onClick = { onSelectGroup(g.optionValue) },
                            label = { Text(g.displayName) },
                        )
                    }
                }
            }
            session.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            Box(Modifier.fillMaxSize()) {
                val cameras = list?.visibleCameras(selected).orEmpty()
                if (session.loading && list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (cameras.isEmpty() && !session.loading) {
                    Text("No cameras to show.", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(cameras, key = { it.optionValue }) { cam ->
                            CameraRow(cam)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRow(cam: Camera) {
    val (color, note) = when {
        !cam.online -> Grey to "No signal"
        cam.isTriggered || cam.isAlerting -> Amber to "Triggered"
        cam.isMotion -> Color.White to "Motion"
        else -> Green to ""
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(cam.displayName, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
        }
        if (cam.ptz) Text("PTZ", style = MaterialTheme.typography.labelSmall)
    }
}
