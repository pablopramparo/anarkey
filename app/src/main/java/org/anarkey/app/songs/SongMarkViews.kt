package org.anarkey.app.songs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.anarkey.app.R
import org.anarkey.app.chords.PianoChordDiagram
import org.anarkey.app.ui.*
import org.anarkey.core.music.*

/** What a mark looks like on the lyric: text to show and the columns reserved for its note-value glyph. */
internal class MarkLook(val text: String, val glyphChars: Int, val duration: NoteDuration?, val rest: Boolean)

internal fun markLook(symbol: String, figure: String?, naming: NoteNaming): MarkLook {
    val duration = NoteDuration.parse(figure)
    return when (SongMarks.parse(symbol)) {
        SongMark.Rest -> MarkLook("", 2, duration ?: NoteDuration(NoteFigure.QUARTER), true)
        is SongMark.Note -> MarkLook(SongMarks.display(symbol, naming), 2, duration ?: NoteDuration(NoteFigure.QUARTER), false)
        is SongMark.Chord -> MarkLook(SongMarks.display(symbol, naming), if (duration != null) 2 else 0, duration, false)
    }
}

@Composable
internal fun figureName(duration: NoteDuration): String {
    val base = stringResource(when (duration.figure) {
        NoteFigure.WHOLE -> R.string.figure_whole
        NoteFigure.HALF -> R.string.figure_half
        NoteFigure.QUARTER -> R.string.figure_quarter
        NoteFigure.EIGHTH -> R.string.figure_eighth
        NoteFigure.SIXTEENTH -> R.string.figure_sixteenth
    })
    return if (duration.dotted) base + " · " + stringResource(R.string.figure_dotted).lowercase() else base
}

/**
 * Note value drawn as in sheet music: note head, stem and flags, or the matching rest. Drawn on a 24-unit tall
 * grid so it scales with the height it is given; the width only needs to be about 14 units.
 */
@Composable
internal fun FigureGlyph(duration: NoteDuration, rest: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.height / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val figure = duration.figure
        if (!rest) {
            val hollow = figure == NoteFigure.WHOLE || figure == NoteFigure.HALF
            val rx = (if (figure == NoteFigure.WHOLE) 5.2f else 4.4f) * u
            val ry = 3.2f * u
            val center = p(7f, 18.5f)
            rotate(-22f, center) {
                val topLeft = Offset(center.x - rx, center.y - ry)
                if (hollow) drawOval(color, topLeft, Size(rx * 2, ry * 2), style = Stroke(1.8f * u))
                else drawOval(color, topLeft, Size(rx * 2, ry * 2))
            }
            if (figure != NoteFigure.WHOLE) {
                val stemX = 10.9f * u
                drawLine(color, Offset(stemX, 17.3f * u), Offset(stemX, 2.5f * u), strokeWidth = 1.7f * u)
                val flags = when (figure) { NoteFigure.EIGHTH -> 1; NoteFigure.SIXTEENTH -> 2; else -> 0 }
                repeat(flags) { index ->
                    val y = (2.5f + index * 4.2f) * u
                    drawPath(Path().apply {
                        moveTo(stemX, y)
                        cubicTo(stemX + 2f * u, y + 3f * u, stemX + 6f * u, y + 4.5f * u, stemX + 5.2f * u, y + 9f * u)
                        cubicTo(stemX + 5.6f * u, y + 5.5f * u, stemX + 2.5f * u, y + 5.5f * u, stemX, y + 4.8f * u)
                        close()
                    }, color)
                }
            }
            if (duration.dotted) drawCircle(color, 1.3f * u, p(14.8f, 18f))
        } else {
            val line = 1.5f * u
            when (figure) {
                NoteFigure.WHOLE -> {
                    drawLine(color, p(3f, 8f), p(15f, 8f), strokeWidth = line)
                    drawRect(color, p(5f, 8f), Size(8f * u, 3.8f * u))
                }
                NoteFigure.HALF -> {
                    drawLine(color, p(3f, 13f), p(15f, 13f), strokeWidth = line)
                    drawRect(color, p(5f, 9.2f), Size(8f * u, 3.8f * u))
                }
                NoteFigure.QUARTER -> drawPath(Path().apply {
                    moveTo(8.5f * u, 3f * u); lineTo(13f * u, 8.5f * u); lineTo(8f * u, 13f * u); lineTo(12.5f * u, 18f * u)
                    cubicTo(8f * u, 17f * u, 6.5f * u, 20f * u, 9.5f * u, 22.5f * u)
                }, color, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                NoteFigure.EIGHTH, NoteFigure.SIXTEENTH -> {
                    val second = figure == NoteFigure.SIXTEENTH
                    drawLine(color, p(if (second) 14f else 13f, 4f), p(if (second) 8f else 8.5f, 21f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                    drawCircle(color, 2.2f * u, p(7.5f, 8f))
                    drawPath(Path().apply { moveTo(7.5f * u, 8f * u); quadraticTo(11f * u, 9.8f * u, 11.8f * u, 7.8f * u) }, color,
                        style = Stroke(1.6f * u, cap = StrokeCap.Round))
                    if (second) {
                        drawCircle(color, 2.2f * u, p(6.5f, 13.5f))
                        drawPath(Path().apply { moveTo(6.5f * u, 13.5f * u); quadraticTo(10f * u, 15.3f * u, 10.6f * u, 13.3f * u) }, color,
                            style = Stroke(1.6f * u, cap = StrokeCap.Round))
                    }
                }
            }
            if (duration.dotted) drawCircle(color, 1.3f * u, p(16.4f, 12f))
        }
    }
}

/** Chord, note or rest, with its note value. A chord left without a value lasts one bar. */
@Composable
internal fun MarkDialog(existing: String?, existingFigure: String?, naming: NoteNaming, onDismiss: () -> Unit,
    onSave: (symbol: String, figure: String?) -> Unit) {
    val parsed = remember(existing) { existing?.let(SongMarks::parse) }
    var kind by remember(existing) { mutableIntStateOf(when (parsed) { is SongMark.Note -> 1; SongMark.Rest -> 2; else -> 0 }) }
    var chord by remember(existing) { mutableStateOf((parsed as? SongMark.Chord)?.symbol.orEmpty()) }
    var pitchClass by remember(existing) { mutableIntStateOf((parsed as? SongMark.Note)?.note?.pitchClass ?: 0) }
    var octave by remember(existing) { mutableIntStateOf((parsed as? SongMark.Note)?.octave ?: SongMarks.DEFAULT_OCTAVE) }
    val initial = NoteDuration.parse(existingFigure)
    var figure by remember(existing, existingFigure) {
        mutableStateOf<NoteFigure?>(initial?.figure ?: if (parsed is SongMark.Note || parsed == SongMark.Rest) NoteFigure.QUARTER else null)
    }
    var dotted by remember(existing, existingFigure) { mutableStateOf(initial?.dotted == true) }
    val flats = naming == NoteNaming.LETTERS_FLATS || naming == NoteNaming.SOLFEGE_FLATS
    val kinds = listOf(R.string.mark_kind_chord, R.string.mark_kind_note, R.string.mark_kind_rest)
    fun pitchName(pc: Int) = ChordSymbolFormatter.format(SpelledNote.fromMidi(pc, flats), naming)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_add_mark)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.forEachIndexed { index, label ->
                    FilterChip(kind == index, onClick = {
                        kind = index
                        // Notes and rests always have a value; a chord may keep "one bar".
                        if (index != 0 && figure == null) figure = NoteFigure.QUARTER
                    }, label = { Text(stringResource(label), Modifier.fillMaxWidth(), maxLines = 1, textAlign = TextAlign.Center) }, modifier = Modifier.weight(1f))
                }
            }
            when (kind) {
                0 -> OutlinedTextField(chord, { chord = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.song_chord_symbol)) },
                    singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
                1 -> {
                    // Laid out like a keyboard: sharps sit between the naturals they belong to.
                    val sharps = listOf(1 to 1f, 3 to 1f, null to 1f, 6 to 1f, 8 to 1f, 10 to 1f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Spacer(Modifier.weight(0.5f))
                        sharps.forEach { (pc, weight) ->
                            if (pc == null) Spacer(Modifier.weight(weight)) else PitchKey(pitchName(pc), pitchClass == pc, { pitchClass = pc }, Modifier.weight(weight))
                        }
                        Spacer(Modifier.weight(0.5f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 2, 4, 5, 7, 9, 11).forEach { pc -> PitchKey(pitchName(pc), pitchClass == pc, { pitchClass = pc }, Modifier.weight(1f)) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = octave > 0, onClick = { octave-- }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("−") }
                        Text(stringResource(R.string.mark_octave, octave), Modifier.padding(horizontal = 4.dp))
                        TextButton(enabled = octave < 8, onClick = { octave++ }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        Text(pitchName(pitchClass) + octave, style = MaterialTheme.typography.titleMedium, color = NeonSoft)
                    }
                }
            }
            Text(stringResource(R.string.mark_duration), color = Muted, style = MaterialTheme.typography.labelLarge)
            // Every value is visible at once: three tiles per row, no sideways scrolling.
            val figureTiles = NoteFigure.entries
            val onSurface = MaterialTheme.colorScheme.onSurface
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val cells = buildList {
                    if (kind == 0) add(Triple("bar", figure == null && !dotted, stringResource(R.string.figure_bar)))
                    figureTiles.forEach { add(Triple(it.name, figure == it, figureName(NoteDuration(it)))) }
                    add(Triple("dotted", dotted, stringResource(R.string.figure_dotted)))
                }
                cells.chunked(3).forEach { rowCells ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowCells.forEach { (id, selected, label) ->
                            FigureTile(selected, label, Modifier.weight(1f), enabled = id != "dotted" || figure != null, onClick = {
                                when (id) {
                                    "bar" -> { figure = null; dotted = false }
                                    "dotted" -> dotted = !dotted
                                    else -> figure = NoteFigure.valueOf(id)
                                }
                            }) {
                                when (id) {
                                    "bar" -> Text("|", color = onSurface, style = MaterialTheme.typography.titleLarge)
                                    "dotted" -> FigureGlyph(NoteDuration(figure ?: NoteFigure.QUARTER, dotted = true), kind == 2, onSurface, Modifier.size(width = 22.dp, height = 30.dp))
                                    else -> FigureGlyph(NoteDuration(NoteFigure.valueOf(id)), kind == 2, onSurface, Modifier.size(width = 22.dp, height = 30.dp))
                                }
                            }
                        }
                        repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } },
        confirmButton = { TextButton(enabled = kind != 0 || chord.isNotBlank(), onClick = {
            val symbol = when (kind) {
                1 -> SongMarks.noteSymbol(SpelledNote.fromMidi(pitchClass, flats), octave)
                2 -> SongMarks.REST
                else -> chord.trim()
            }
            onSave(symbol, figure?.let { NoteDuration(it, dotted).code })
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun PitchKey(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.height(44.dp).clip(shape).background(if (selected) Neon.copy(alpha = 0.18f) else Color.Transparent)
        .border(1.dp, if (selected) Neon else Line, shape).selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, color = if (selected) Neon else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FigureTile(selected: Boolean, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.heightIn(min = 68.dp).clip(shape).background(if (selected) Neon.copy(alpha = 0.18f) else Color.Transparent)
        .border(1.dp, if (selected) Neon else Line, shape).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        glyph()
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Muted)
    }
}

/** Tapping a written note: the key on the keyboard and where it sits on the song's instrument. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteSheet(symbol: String, note: SongMark.Note, naming: NoteNaming, tuningId: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val name = SongMarks.display(symbol, naming)
            Text(name, style = MaterialTheme.typography.headlineSmall, color = NeonSoft)
            PianoChordDiagram(PianoVoicing("note.$symbol", symbol, listOf(note.midi), 0), note.note.pitchClass, name)
            val tuning = tuningId?.let { TuningCatalog.tuning(it) }
            if (tuning != null) {
                Text(stringResource(R.string.note_positions), style = MaterialTheme.typography.titleMedium)
                val spots = tuning.strings.mapNotNull { string -> (note.midi - string.midi).takeIf { it in 0..20 }?.let { string.number to it } }
                if (spots.isEmpty()) Text(stringResource(R.string.note_out_of_range), color = Muted)
                spots.forEach { (string, fret) -> Text(stringResource(R.string.note_position, string, fret)) }
            }
        }
    }
}
