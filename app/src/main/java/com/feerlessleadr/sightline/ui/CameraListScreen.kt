package com.feerlessleadr.sightline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.CameraTag
import com.feerlessleadr.sightline.data.RecentAlert
import com.feerlessleadr.sightline.data.mergeTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest

/** The home screen: header, the Live or Alerts tab, and the bottom tab bar. Full screen hides everything but the grid. */
@Composable
fun CameraListScreen(
    session: SessionState,
    onSelectGroup: (String?) -> Unit,
    onRefresh: () -> Unit,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpenCamera: (Camera) -> Unit,
    onTileSize: (group: String, size: TileSize) -> Unit,
    onArrangement: (group: String, value: GroupArrangement) -> Unit,
    onResetLayout: (group: String) -> Unit,
    onLiveVideo: (Boolean) -> Unit,
    onMotionBoxes: (Boolean) -> Unit,
    onPollStatus: suspend () -> Unit,
    onRecentAlerts: suspend () -> List<RecentAlert>,
    onSignInAgain: () -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Live) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    SystemBarsHidden(fullScreen && tab == HomeTab.Live)

    Column(Modifier.fillMaxSize().background(Sl.Background)) {
        if (!fullScreen) {
            SightlineHeader(
                serverName = session.info.systemName ?: "Blue Iris",
                onFullScreen = { tab = HomeTab.Live; fullScreen = true },
                onRefresh = onRefresh,
                onMenu = onSettings,
            )
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Live -> LiveTab(session, onSelectGroup, loadStill, openVideo, onOpenCamera, onTileSize, onArrangement, onResetLayout, onLiveVideo, onMotionBoxes, onPollStatus, onRecentAlerts, onSignInAgain, fullScreen)
                HomeTab.Alerts -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Alerts are not built yet. They arrive in a later version.", color = Sl.Muted, modifier = Modifier.padding(24.dp))
                }
            }
        }
        if (!fullScreen) SightlineTabBar(tab) { tab = it }
    }
}

@Composable
private fun LiveTab(
    session: SessionState,
    onSelectGroup: (String?) -> Unit,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpenCamera: (Camera) -> Unit,
    onTileSize: (group: String, size: TileSize) -> Unit,
    onArrangement: (group: String, value: GroupArrangement) -> Unit,
    onResetLayout: (group: String) -> Unit,
    onLiveVideo: (Boolean) -> Unit,
    onMotionBoxes: (Boolean) -> Unit,
    onPollStatus: suspend () -> Unit,
    onRecentAlerts: suspend () -> List<RecentAlert>,
    onSignInAgain: () -> Unit,
    fullScreen: Boolean,
) {
    // Blue Iris's own motion/trigger state every 2 s and the latest AI results every 4 s, only while this grid is on
    // screen and the app is in front (the web app does the same). With highlights and labels off, a slow check keeps
    // the status dots and the connection banner honest.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val wantsStatus = session.highlight || session.aiLabels
    var aiTags by remember { mutableStateOf<Map<String, CameraTag>>(emptyMap()) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shown by rememberUpdatedState(
        session.cameras?.cameras?.filter { it.isEnabled && !it.hidden }?.map { it.optionValue.lowercase() }?.toSet().orEmpty(),
    )
    LaunchedEffect(wantsStatus) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                onPollStatus()
                nowMs = System.currentTimeMillis()
                delay(if (wantsStatus) 2_000L else 8_000L)
            }
        }
    }
    LaunchedEffect(wantsStatus) {
        if (!wantsStatus) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val alerts = onRecentAlerts()
                aiTags = mergeTags(aiTags, alerts) { shown.isEmpty() || it.lowercase() in shown }
                nowMs = System.currentTimeMillis()
                delay(4_000L)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (!session.online) {
            Row(
                Modifier.fillMaxWidth().background(Sl.Surface2).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reconnecting to Blue Iris…", color = Sl.Text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { scope.launch { onPollStatus() } }) { Text("Try now") }
                TextButton(onClick = onSignInAgain) { Text("Sign in again") }
            }
        }
        val list = session.cameras
        val groups = list?.groups.orEmpty()
        val selected = groups.firstOrNull { it.optionValue == session.selectedGroup }
            ?: groups.firstOrNull { it.optionValue == "index" }
        val groupKey = selected?.optionValue ?: "index"
        val size = session.tileSizes[groupKey] ?: TileSize.Auto
        if (!fullScreen) {
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
                TextButton(onClick = { onMotionBoxes(!session.motionBoxes) }) {
                    Text("Boxes", color = if (session.motionBoxes) Sl.Accent else Sl.Muted)
                }
                TextButton(onClick = { onLiveVideo(!session.liveVideo) }) {
                    Text(if (session.liveVideo) "Live" else "Stills")
                }
            }
        }
        session.error?.let {
            Text(it, color = Sl.Live, modifier = Modifier.padding(16.dp))
        }
        Box(Modifier.fillMaxSize()) {
            val cameras = list?.visibleCameras(selected).orEmpty()
            if (session.loading && list == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (cameras.isEmpty() && !session.loading) {
                Text("No cameras to show.", color = Sl.Muted, modifier = Modifier.align(Alignment.Center))
            } else {
                LiveGrid(
                    group = selected,
                    cameras = cameras,
                    tileSize = size,
                    arrangement = session.arrangements[groupKey] ?: GroupArrangement(),
                    onArrangement = { onArrangement(groupKey, it) },
                    onResetLayout = { onResetLayout(groupKey) },
                    highlight = session.highlight,
                    aiLabels = session.aiLabels,
                    aiTags = aiTags,
                    nowMs = nowMs,
                    motionBoxes = session.motionBoxes,
                    motionSensitivity = session.motionSensitivity,
                    liveVideo = session.liveVideo,
                    loadStill = loadStill,
                    openVideo = openVideo,
                    onOpenCamera = onOpenCamera,
                    onDiagnostics = {},
                )
            }
        }
    }
}
