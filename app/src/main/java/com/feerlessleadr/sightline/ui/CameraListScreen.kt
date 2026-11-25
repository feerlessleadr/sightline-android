package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    session: SessionState,
    onSelectGroup: (String?) -> Unit,
    onRefresh: () -> Unit,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpenCamera: (Camera) -> Unit,
    onTileSize: (group: String, size: TileSize) -> Unit,
    onLiveVideo: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    var diagnostics by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.info.systemName ?: "Sightline") },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                    TextButton(onClick = onSettings) { Text("Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val list = session.cameras
            val groups = list?.groups.orEmpty()
            val selected = groups.firstOrNull { it.optionValue == session.selectedGroup }
                ?: groups.firstOrNull { it.optionValue == "index" }
            val groupKey = selected?.optionValue ?: "index"
            val size = session.tileSizes[groupKey] ?: TileSize.Auto
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (groups.size > 1) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
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
                } else {
                    Box(Modifier.weight(1f))
                }
                TextButton(onClick = { onTileSize(groupKey, TileSize.entries[(size.ordinal + 1) % TileSize.entries.size]) }) {
                    Text(size.label)
                }
                TextButton(onClick = { onLiveVideo(!session.liveVideo) }) {
                    Text(if (session.liveVideo) "Live" else "Stills")
                }
            }
            if (diagnostics.isNotEmpty()) {
                // Temporary diagnostics while live video is being proven on real phones.
                Text(diagnostics, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
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
                    LiveGrid(
                        group = selected,
                        cameras = cameras,
                        tileSize = size,
                        liveVideo = session.liveVideo,
                        loadStill = loadStill,
                        openVideo = openVideo,
                        onOpenCamera = onOpenCamera,
                        onDiagnostics = { diagnostics = it },
                    )
                }
            }
        }
    }
}
