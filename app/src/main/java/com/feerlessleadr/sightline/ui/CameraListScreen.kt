package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.Camera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFF9A825)
private val Grey = Color(0xFF757575)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    session: SessionState,
    onSelectGroup: (String?) -> Unit,
    onRefresh: () -> Unit,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(cameras, key = { it.optionValue }) { cam ->
                            CameraTile(cam, loadStill)
                        }
                    }
                }
            }
        }
    }
}

private const val STILL_REFRESH_MS = 2_000L
private const val STILL_WIDTH = 480

/**
 * One camera: a live still that refreshes every couple of seconds while the tile is on screen and the app
 * is in the foreground (lazy grid tiles leave composition when scrolled away, which stops their loop).
 */
@Composable
private fun CameraTile(cam: Camera, loadStill: suspend (String, Int) -> ByteArray?) {
    val (color, note) = when {
        !cam.online -> Grey to "No signal"
        cam.isTriggered || cam.isAlerting -> Amber to "Triggered"
        cam.isMotion -> Color.White to "Motion"
        else -> Green to ""
    }
    var picture by remember(cam.optionValue) { mutableStateOf<ImageBitmap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cam.optionValue) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val bytes = loadStill(cam.optionValue, STILL_WIDTH)
                if (bytes != null) {
                    val decoded = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (decoded != null) picture = decoded
                }
                delay(STILL_REFRESH_MS)
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black),
    ) {
        picture?.let {
            Image(it, contentDescription = cam.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(cam.displayName, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            if (note.isNotEmpty()) Text(note, color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (cam.ptz) Text("PTZ", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}
