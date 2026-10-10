package org.anarkey.app.chords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.anarkey.app.R
import org.anarkey.app.ui.*
import org.anarkey.core.music.PianoVoicing

private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)
/** White keys that have a black key right after them (C, D, F, G, A). */
private val BLACK_AFTER = setOf(0, 2, 5, 7, 9)

/** A keyboard with the chord's keys lit; the root is brighter. Tapping it (or the button) plays the chord. */
@Composable
internal fun PianoChordDiagram(voicing: PianoVoicing, rootPitchClass: Int, description: String) {
    val scope = rememberCoroutineScope()
    val play = { scope.launch { ChordPlayer.playNotes(voicing.midi, strumGapS = 0.012) }; Unit }
    DisposableEffect(voicing.id) { onDispose { ChordPlayer.stop() } }
    // Always whole octaves from a C, at least two, so the shape of the keyboard stays recognisable.
    val firstC = voicing.midi.min() / 12 * 12
    val lastB = maxOf(voicing.midi.max() / 12 * 12 + 11, firstC + 23)
    val whites = (firstC..lastB).filter { it % 12 in WHITE_PITCH_CLASSES }
    val lit = voicing.midi.toSet()
    Box(Modifier.fillMaxWidth().clickable(onClick = play), contentAlignment = Alignment.Center) {
        IconButton(onClick = play, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.chord_play), tint = NeonSoft)
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 48.dp, bottom = 8.dp).height(118.dp)
            .semantics { contentDescription = description }) {
            val keyWidth = minOf(size.width / whites.size, 40.dp.toPx())
            val left = (size.width - keyWidth * whites.size) / 2f
            val whiteHeight = size.height
            val blackHeight = whiteHeight * 0.62f
            val blackWidth = keyWidth * 0.6f
            val outline = Color(0xFF807268)
            fun color(midi: Int, unlit: Color) = when {
                midi !in lit -> unlit
                midi % 12 == rootPitchClass -> Neon
                else -> NeonSoft
            }
            whites.forEachIndexed { i, midi ->
                val topLeft = Offset(left + i * keyWidth, 0f)
                drawRect(color(midi, Color(0xFFF2EBE0)), topLeft, Size(keyWidth, whiteHeight))
                drawRect(outline, topLeft, Size(keyWidth, whiteHeight), style = Stroke(1.dp.toPx()))
            }
            whites.forEachIndexed { i, midi ->
                if (midi % 12 !in BLACK_AFTER || midi + 1 > lastB) return@forEachIndexed
                val x = left + (i + 1) * keyWidth - blackWidth / 2f
                drawRect(color(midi + 1, Color(0xFF1A1714)), Offset(x, 0f), Size(blackWidth, blackHeight))
                drawRect(outline, Offset(x, 0f), Size(blackWidth, blackHeight), style = Stroke(1.dp.toPx()))
            }
        }
    }
}
