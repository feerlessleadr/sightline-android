package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class Shape { Mark, Box, Block, Playhead, Dot, Border }

private class Entry(val shape: Shape, val color: Color, val title: String, val note: String, val glow: Boolean = false)

private val Online = Color(0xFF63C35B)
private val RecordedColor = Color(0xFF2B4D61)

private val sections = listOf(
    "Alerts (labels & timeline marks)" to listOf(
        Entry(Shape.Mark, Sl.Person, "Person", "AI found a person"),
        Entry(Shape.Mark, Sl.Vehicle, "Vehicle", "Car, truck, bus, motorcycle, bicycle, boat"),
        Entry(Shape.Mark, Sl.Animal, "Animal", "Dog, cat, bear, bird, deer and other wildlife"),
        Entry(Shape.Mark, Sl.Delivery, "Delivery", "Amazon, UPS, USPS, FedEx, DHL"),
        Entry(Shape.Mark, Sl.Motion, "Motion only", "Triggered without an AI label"),
        Entry(Shape.Box, Sl.Accent, "Motion box", "Something moved here (on camera tiles, when motion boxes are on)"),
    ),
    "Player timeline" to listOf(
        Entry(Shape.Block, RecordedColor, "Recorded video", "Footage exists for this time"),
        Entry(Shape.Block, Sl.Surface2, "No recording", "Gap, or nothing saved for this time"),
        Entry(Shape.Playhead, Color.White, "Playhead", "The moment on screen (recorded)"),
        Entry(Shape.Playhead, Sl.Live, "Live playhead", "Watching live"),
    ),
    "Cameras" to listOf(
        Entry(Shape.Dot, Online, "Online", "Camera is connected"),
        Entry(Shape.Dot, Sl.Live, "Triggered", "Motion or an alert is happening now", glow = true),
        Entry(Shape.Dot, Sl.Faint, "Offline", "No signal from the camera"),
    ),
    "Camera tiles" to listOf(
        Entry(Shape.Border, Color.White.copy(alpha = 0.55f), "White border", "Blue Iris sees motion"),
        Entry(Shape.Border, Sl.Live, "Red border", "Camera triggered (no AI label yet)"),
        Entry(Shape.Border, Sl.Animal, "Coloured border + label", "Triggered, coloured by what the AI saw (for example green “Dog 82%”)"),
    ),
)

/** What the colours on camera tiles, alerts and the player timeline mean (the web app's Color legend). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorLegendScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Colour legend") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            for ((title, entries) in sections) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                for (e in entries) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) { Swatch(e) }
                        Column {
                            Text(e.title, color = Sl.Text, fontWeight = FontWeight.SemiBold)
                            Text(e.note, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Swatch(e: Entry) {
    val round = RoundedCornerShape(4.dp)
    when (e.shape) {
        Shape.Mark -> Box(Modifier.size(width = 8.dp, height = 24.dp).background(e.color, round))
        Shape.Box -> Box(Modifier.size(28.dp, 22.dp).border(2.dp, e.color, round))
        Shape.Block -> Box(Modifier.size(28.dp, 18.dp).background(e.color, round).border(1.dp, Sl.Line, round))
        Shape.Playhead -> Box(Modifier.size(width = 3.dp, height = 26.dp).background(e.color))
        Shape.Dot -> Box(
            Modifier.size(if (e.glow) 18.dp else 12.dp).background(if (e.glow) e.color.copy(alpha = 0.25f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).background(e.color, CircleShape)) }
        Shape.Border -> Box(Modifier.size(28.dp, 22.dp).border(3.dp, e.color, round))
    }
}
