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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import com.feerlessleadr.sightline.data.AlertsUi
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
import com.feerlessleadr.sightline.data.PopupEvent
import com.feerlessleadr.sightline.data.PopupTracker
import androidx.compose.runtime.SideEffect
import com.feerlessleadr.sightline.data.RecentAlert
import com.feerlessleadr.sightline.data.mergeTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
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
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    alerts: AlertsUi,
    alertsListState: LazyListState,
    alertsActions: AlertsActions,
) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    SystemBarsHidden(fullScreen && tab == HomeTab.Live)

    Column(Modifier.fillMaxSize().background(Sl.Background)) {
        if (!fullScreen) {
            SightlineHeader(
                serverName = session.info.systemName ?: "Blue Iris",
                onFullScreen = { onTab(HomeTab.Live); fullScreen = true },
                onMenu = onSettings,
            )
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Live -> LiveTab(session, onSelectGroup, onRefresh, loadStill, openVideo, onOpenCamera, onTileSize, onArrangement, onResetLayout, onLiveVideo, onMotionBoxes, onPollStatus, onRecentAlerts, onSignInAgain, fullScreen)
                HomeTab.Alerts -> AlertsTab(
                    ui = alerts,
                    groups = session.cameras?.groups.orEmpty(),
                    cameras = session.cameras?.visibleCameras(null).orEmpty(),
                    listState = alertsListState,
                    actions = alertsActions,
                )
            }
        }
        if (!fullScreen) SightlineTabBar(tab) { onTab(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTab(
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
    // Pop-ups: a new detection of what the owner chose pops that camera up (only cameras in the group on screen).
    val popupConfig by rememberUpdatedState(session.popup)
    val tracker = remember { PopupTracker() }
    var popupEvent by remember { mutableStateOf<PopupEvent?>(null) }
    var gridNames by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(session.popup.isOn) { if (!session.popup.isOn) popupEvent = null }
    val needsAlerts = wantsStatus || session.popup.isOn
    LaunchedEffect(needsAlerts) {
        if (!needsAlerts) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val alerts = onRecentAlerts()
                if (wantsStatus) aiTags = mergeTags(aiTags, alerts) { shown.isEmpty() || it.lowercase() in shown }
                tracker.consider(alerts, popupConfig, { it.lowercase() in gridNames }, popupEvent != null)?.let { popupEvent = it }
                nowMs = System.currentTimeMillis()
                delay(4_000L)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
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
        // Pull down from the top of the grid to reload the camera list from Blue Iris (shown for at least a moment).
        var refreshing by remember { mutableStateOf(false) }
        val loading by rememberUpdatedState(session.loading)
        LaunchedEffect(refreshing) {
            if (refreshing) {
                delay(700)
                while (loading) delay(100)
                refreshing = false
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; onRefresh() }, modifier = Modifier.fillMaxSize()) {
            val cameras = list?.visibleCameras(selected).orEmpty()
            if (session.loading && list == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (cameras.isEmpty() && !session.loading) {
                // Scrollable, so that it can be pulled to refresh too.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("No cameras to show. Pull down to refresh.", color = Sl.Muted, modifier = Modifier.fillMaxWidth().padding(top = 96.dp, start = 24.dp, end = 24.dp), textAlign = TextAlign.Center)
                }
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
    val allCameras = session.cameras
    val shownGroup = allCameras?.groups.orEmpty().let { g -> g.firstOrNull { it.optionValue == session.selectedGroup } ?: g.firstOrNull { it.optionValue == "index" } }
    val gridCameras = allCameras?.visibleCameras(shownGroup).orEmpty()
    SideEffect { gridNames = gridCameras.map { it.optionValue.lowercase() }.toSet() }
    val popupCamera = popupEvent?.let { ev -> allCameras?.cameras?.firstOrNull { it.optionValue.equals(ev.camera, ignoreCase = true) } }
    if (popupEvent != null && popupCamera != null) {
        PopupBox(
            event = popupEvent!!,
            camera = popupCamera,
            liveVideo = session.liveVideo,
            loadStill = loadStill,
            openVideo = openVideo,
            onOpen = { popupEvent = null; onOpenCamera(popupCamera) },
            onClose = { popupEvent = null },
            modifier = Modifier.align(Alignment.Center).padding(12.dp),
        )
    }
    }
}
