package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One gesture detector for a camera tile: a quick tap ([onTap]); or press and hold ([onLongPress]) followed by
 * dragging ([onDrag] with each movement) and then letting go ([onEnd], told whether the finger moved). A press that
 * turns into a scroll before the hold time is left to the scrolling parent.
 */
@Composable
fun Modifier.tileGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (moved: Boolean) -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var released = false
            var up: Boolean? = null
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                up = waitForUpOrCancellation() != null
                released = true
            }
            if (released) {
                if (up == true) tap()
                return@awaitEachGesture
            }
            long()
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) break
                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    moved = true
                    drag(delta)
                    change.consume()
                }
            }
            end(moved)
        }
    }
}
