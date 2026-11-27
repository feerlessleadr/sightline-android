package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** The widest a page of settings, lists or text is allowed to be before it gets side margins (tablets and large screens). */
private const val READABLE_WIDTH_DP = 720

/** Keeps a text page to a comfortable width, centred, on a wide screen. Does nothing on a phone. */
@Composable
fun Modifier.readable(): Modifier {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width > READABLE_WIDTH_DP) padding(horizontal = ((width - READABLE_WIDTH_DP) / 2).dp) else this
}
