package com.feerlessleadr.sightline.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The Sightline mark: a blue ring with a white centre. */
@Composable
fun SlLogo(size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.16f
        drawCircle(Sl.Accent, radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
        drawCircle(Color.White, radius = this.size.minDimension * 0.17f)
    }
}

enum class Glyph { Refresh, FullScreen, ExitFullScreen, Menu, Live, Alerts, Play, Pause, PrevAlert, NextAlert, Calendar, Sound, Muted }

/** Small line icons drawn by hand, so the app needs no icon library. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.09f
        val st = Stroke(w, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Refresh -> {
                val inset = s * 0.18f
                drawArc(color, startAngle = -40f, sweepAngle = 280f, useCenter = false, topLeft = Offset(inset, inset), size = Size(s - 2 * inset, s - 2 * inset), style = st)
                val p = Path().apply {
                    moveTo(s * 0.80f, s * 0.12f); lineTo(s * 0.80f, s * 0.36f); lineTo(s * 0.56f, s * 0.30f)
                }
                drawPath(p, color, style = st)
            }
            Glyph.FullScreen -> corners(color, st, s, outward = true)
            Glyph.ExitFullScreen -> corners(color, st, s, outward = false)
            Glyph.Menu -> for (i in 0..2) drawCircle(color, radius = s * 0.075f, center = Offset(s / 2f, s * (0.22f + 0.28f * i)))
            Glyph.Live -> {
                drawCircle(color, radius = s * 0.38f, style = st)
                drawCircle(color, radius = s * 0.14f)
            }
            Glyph.Sound, Glyph.Muted -> {
                val speaker = Path().apply {
                    moveTo(s * 0.14f, s * 0.38f); lineTo(s * 0.32f, s * 0.38f); lineTo(s * 0.52f, s * 0.20f)
                    lineTo(s * 0.52f, s * 0.80f); lineTo(s * 0.32f, s * 0.62f); lineTo(s * 0.14f, s * 0.62f); close()
                }
                drawPath(speaker, color)
                if (glyph == Glyph.Sound) {
                    drawArc(color, -45f, 90f, false, Offset(s * 0.38f, s * 0.32f), Size(s * 0.36f, s * 0.36f), style = st)
                    drawArc(color, -45f, 90f, false, Offset(s * 0.26f, s * 0.20f), Size(s * 0.60f, s * 0.60f), style = st)
                } else {
                    drawLine(color, Offset(s * 0.64f, s * 0.38f), Offset(s * 0.88f, s * 0.62f), strokeWidth = w, cap = StrokeCap.Round)
                    drawLine(color, Offset(s * 0.88f, s * 0.38f), Offset(s * 0.64f, s * 0.62f), strokeWidth = w, cap = StrokeCap.Round)
                }
            }
            Glyph.Calendar -> {
                drawRoundRect(color, Offset(s * 0.16f, s * 0.22f), Size(s * 0.68f, s * 0.62f), CornerRadius(s * 0.08f), style = st)
                drawLine(color, Offset(s * 0.16f, s * 0.42f), Offset(s * 0.84f, s * 0.42f), strokeWidth = w)
                drawLine(color, Offset(s * 0.34f, s * 0.12f), Offset(s * 0.34f, s * 0.30f), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.12f), Offset(s * 0.66f, s * 0.30f), strokeWidth = w, cap = StrokeCap.Round)
            }
            Glyph.Play -> drawPath(
                Path().apply { moveTo(s * 0.30f, s * 0.18f); lineTo(s * 0.82f, s * 0.5f); lineTo(s * 0.30f, s * 0.82f); close() },
                color,
            )
            Glyph.Pause -> {
                drawRect(color, Offset(s * 0.26f, s * 0.18f), Size(s * 0.18f, s * 0.64f))
                drawRect(color, Offset(s * 0.56f, s * 0.18f), Size(s * 0.18f, s * 0.64f))
            }
            Glyph.PrevAlert -> {
                drawRect(color, Offset(s * 0.18f, s * 0.2f), Size(s * 0.09f, s * 0.6f))
                drawPath(Path().apply { moveTo(s * 0.82f, s * 0.2f); lineTo(s * 0.34f, s * 0.5f); lineTo(s * 0.82f, s * 0.8f); close() }, color)
            }
            Glyph.NextAlert -> {
                drawRect(color, Offset(s * 0.73f, s * 0.2f), Size(s * 0.09f, s * 0.6f))
                drawPath(Path().apply { moveTo(s * 0.18f, s * 0.2f); lineTo(s * 0.66f, s * 0.5f); lineTo(s * 0.18f, s * 0.8f); close() }, color)
            }
            Glyph.Alerts -> {
                val p = Path().apply {
                    moveTo(s * 0.20f, s * 0.74f); lineTo(s * 0.80f, s * 0.74f)
                    lineTo(s * 0.72f, s * 0.62f); lineTo(s * 0.72f, s * 0.42f)
                    cubicTo(s * 0.72f, s * 0.22f, s * 0.28f, s * 0.22f, s * 0.28f, s * 0.42f)
                    lineTo(s * 0.28f, s * 0.62f); close()
                }
                drawPath(p, color, style = st)
                drawCircle(color, radius = s * 0.07f, center = Offset(s / 2f, s * 0.86f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.corners(color: Color, st: Stroke, s: Float, outward: Boolean) {
    val a = s * 0.16f
    val b = s * 0.40f
    val c = s * 0.60f
    val d = s * 0.84f
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val p = Path().apply { moveTo(x + dx, y); lineTo(x, y); lineTo(x, y + dy) }
        drawPath(p, color, style = st)
    }
    if (outward) {
        corner(a, a, b - a, b - a); corner(d, a, -(b - a), b - a)
        corner(a, d, b - a, -(b - a)); corner(d, d, -(b - a), -(b - a))
    } else {
        corner(b, b, -(b - a), -(b - a)); corner(c, b, b - a, -(b - a))
        corner(b, c, -(b - a), b - a); corner(c, c, b - a, b - a)
    }
}

/** A round 44 dp touch target with an icon. */
@Composable
fun IconAction(glyph: Glyph, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Sl.Text) }
}

/** Logo, the "Sightline for Blue Iris" tag above the server name, and the header buttons. */
@Composable
fun SightlineHeader(serverName: String, onFullScreen: () -> Unit, onRefresh: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Sl.Surface)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SlLogo()
        Column(Modifier.weight(1f)) {
            Text("SIGHTLINE FOR BLUE IRIS", fontSize = 10.sp, letterSpacing = 1.sp, color = Sl.Muted, maxLines = 1)
            Text(serverName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        IconAction(Glyph.FullScreen, "Full screen", onFullScreen)
        IconAction(Glyph.Refresh, "Refresh", onRefresh)
        IconAction(Glyph.Menu, "Menu", onMenu)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sl.Line))
}

enum class HomeTab(val label: String, val glyph: Glyph) { Live("Live", Glyph.Live), Alerts("Alerts", Glyph.Alerts) }

/** The bottom bar: Live and Alerts. */
@Composable
fun SightlineTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Sl.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sl.Line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HomeTab.entries.forEach { tab ->
                val on = tab == selected
                val tint = if (on) Sl.Accent else Sl.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlyphIcon(tab.glyph, tint)
                    Text(tab.label, color = tint, fontSize = 12.sp)
                }
            }
        }
    }
}

/** A rounded card in the web app's surface colour with a thin line around it. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(Sl.Surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .border(1.dp, Sl.Line, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) { content() }
}

/** The "sightline" mark above a title, used on the sign-in and lock screens. */
@Composable
fun BrandBlock(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SlLogo(40.dp)
        Column {
            Text("Sightline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("for Blue Iris", style = MaterialTheme.typography.bodySmall, color = Sl.Muted)
        }
    }
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sl.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 18.dp))
}

/** Hides the status and navigation bars while [on] (the web app's full-screen button). */
@Composable
fun SystemBarsHidden(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (on) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
