package org.anarkey.app.chords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.anarkey.app.R
import org.anarkey.app.ui.*
import org.anarkey.core.music.*
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ChordDictionaryScreen(model: ChordFavoriteViewModel, naming: NoteNaming, mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit) {
    var symbol by remember { mutableStateOf("C") }
    var root by remember { mutableStateOf("C") }
    var quality by remember { mutableStateOf("major") }
    var extension by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.chords_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(symbol, { symbol = it.trim().take(40) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chord_search)) }, singleLine = true)
        var showFilters by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showFilters = !showFilters }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text(stringResource(if (showFilters) R.string.chord_hide_filters else R.string.chord_show_filters))
        }
        if (showFilters) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.chord_choose_root), color = Muted, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val flats = naming == NoteNaming.LETTERS_FLATS || naming == NoteNaming.SOLFEGE_FLATS
            val roots = (0..11).map { ChordSymbolFormatter.format(SpelledNote.fromMidi(it, flats), naming) }
            val selectedPitch = ChordTheory.resolve(root)?.root?.pitchClass
            items(roots) { item ->
                FilterChip(selectedPitch == ChordTheory.resolve(item)?.root?.pitchClass, { root = item; symbol = buildChord(item, quality, extension) }, label = { Text(item) })
            }
        }
        Text(stringResource(R.string.chord_choose_quality), color = Muted, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(listOf("major","minor","diminished","augmented","sus2","sus4")) { item ->
                FilterChip(quality == item, { quality = item; symbol = buildChord(root, item, extension) },
                    label = { Text(qualityLabel(item)) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(listOf("", "7", "maj7", "m7", "m7b5", "dim7", "6", "add9")) { item ->
                FilterChip(extension == item, { extension = item; symbol = buildChord(root, quality, item) },
                    label = { Text(item.ifEmpty { stringResource(R.string.chord_triad) }) })
            }
        }
        }
        ChordLookupContent(symbol, model, naming = naming, mode = mode, setMode = setMode, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordLookupSheet(symbol: String, model: ChordFavoriteViewModel, instrumentId: String?, tuningId: String?, capo: Int = 0, naming: NoteNaming, mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChordLookupContent(symbol, model, instrumentId, tuningId, capo, naming, mode, setMode, Modifier.fillMaxHeight(.9f).padding(horizontal = 16.dp))
    }
}

@Composable
private fun ChordLookupContent(
    symbol: String, model: ChordFavoriteViewModel,
    preferredInstrument: String? = null, preferredTuning: String? = null, capo: Int = 0, naming: NoteNaming,
    mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit, modifier: Modifier = Modifier,
) {
    val initialInstrument = preferredInstrument ?: if (mode == ChordPresentationMode.BEGINNER) "guitar" else null
    val initialTuning = preferredTuning ?: if (initialInstrument == "guitar") "guitar.standard" else null
    var instrumentId by remember(symbol, preferredInstrument, mode) { mutableStateOf(initialInstrument) }
    var tuningId by remember(symbol, preferredTuning, mode) { mutableStateOf(initialTuning) }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showTheory by rememberSaveable { mutableStateOf(false) }
    val favoriteIds by model.favoriteIds.collectAsStateWithLifecycle()
    val chord = remember(symbol) { ChordTheory.resolve(symbol) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChordModePicker(mode, setMode)
        Text(ChordSymbolFormatter.format(symbol, naming), style = MaterialTheme.typography.headlineMedium, color = NeonSoft, fontWeight = FontWeight.Bold)
        if (chord == null) {
            Text(stringResource(R.string.chord_unknown_symbol), color = Muted)
            return@Column
        }
        if (mode == ChordPresentationMode.ADVANCED) Text(stringResource(R.string.chord_notes, chord.tones.joinToString(" · ") { ChordSymbolFormatter.format(it.note, naming) }), style = MaterialTheme.typography.titleMedium)
        if (mode == ChordPresentationMode.ADVANCED) {
            Text(stringResource(R.string.chord_intervals, chord.tones.joinToString(" · ") { "${it.intervalName} ${ChordSymbolFormatter.format(it.note, naming)}" }), color = Muted)
            chord.bass?.let { Text(stringResource(R.string.chord_inversion, ChordSymbolFormatter.format(it, naming)), color = Muted) }
        }
        if (capo > 0) Text(stringResource(R.string.chord_capo_relative, capo), color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chord_instrument), Modifier.weight(1f), color = Muted)
            var menu by remember { mutableStateOf(false) }
            Box {
                SelectorButton(instrumentId?.let { instrumentName(it) } ?: stringResource(R.string.chord_theory_only),
                    onClick = { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chord_theory_only)) }, onClick = { instrumentId = null; tuningId = null; menu = false })
                    TuningCatalog.instruments.forEach { item -> DropdownMenuItem(text = { Text(instrumentName(item.id)) }, onClick = {
                        instrumentId = item.id; tuningId = TuningCatalog.forInstrument(item.id).firstOrNull()?.id; menu = false
                    }) }
                }
            }
        }
        if (instrumentId != null) {
            val tunings = TuningCatalog.forInstrument(instrumentId!!)
            if (tunings.isNotEmpty()) {
                var tuningMenu by remember { mutableStateOf(false) }
                Box {
                    SelectorButton(tuningId?.let { tuningName(it) } ?: stringResource(R.string.chord_select_tuning),
                        onClick = { tuningMenu = true })
                    DropdownMenu(tuningMenu, { tuningMenu = false }) { tunings.forEach { tuning -> DropdownMenuItem(text = { Text(tuningName(tuning.id)) }, onClick = { tuningId = tuning.id; tuningMenu = false }) } }
                }
            }
        }
        if (instrumentId == "violin") Text(stringResource(R.string.chord_no_fret_diagram), color = Muted)
        if (instrumentId == null) Text(stringResource(R.string.chord_choose_instrument_for_shapes), color = Muted)
        val matchingVoicings = ChordVoicingCatalog.forChord(symbol, instrumentId, tuningId).filter { !favoritesOnly || it.id in favoriteIds }
        val voicings = if (mode == ChordPresentationMode.BEGINNER) ChordVoicingCatalog.prioritizeForBeginner(matchingVoicings) else matchingVoicings
        if (instrumentId != null && voicings.isEmpty()) Text(stringResource(R.string.chord_no_voicings), color = NeonSoft)
        voicings.forEachIndexed { index, voicing ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.chord_voicing_number, index + 1), Modifier.weight(1f), color = NeonSoft, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { model.setFavorite(voicing.id, voicing.id !in favoriteIds) }) {
                            Icon(if (voicing.id in favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.chord_favorite), tint = NeonSoft)
                        }
                    }
                    ChordDiagram(voicing, leftHanded, capo)
                    if (mode == ChordPresentationMode.BEGINNER && index == 0) Text(stringResource(R.string.chord_diagram_legend), color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (mode == ChordPresentationMode.BEGINNER) BeginnerVoicingGuide(voicing)
                    Text(voicing.frets.joinToString("  ") { it?.toString() ?: "×" }, color = Muted)
                    if (mode == ChordPresentationMode.ADVANCED) Text(stringResource(R.string.chord_playability_note), color = Muted, style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { showTheory = !showTheory }) { Text(stringResource(if (showTheory) R.string.chord_hide_theory else R.string.chord_show_theory)) }
                    if (mode == ChordPresentationMode.BEGINNER && showTheory) {
                        Text(stringResource(R.string.chord_notes, chord.tones.joinToString(" · ") { ChordSymbolFormatter.format(it.note, naming) }), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.chord_intervals, chord.tones.joinToString(" · ") { "${it.intervalName} ${ChordSymbolFormatter.format(it.note, naming)}" }), color = Muted)
                        chord.bass?.let { Text(stringResource(R.string.chord_inversion, ChordSymbolFormatter.format(it, naming)), color = Muted) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chord_orientation), Modifier.weight(1f), color = Muted)
            Switch(leftHanded, { leftHanded = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chord_favorites_only), Modifier.weight(1f), color = Muted)
            Switch(favoritesOnly, { favoritesOnly = it })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ChordDiagram(voicing: ChordVoicing, leftHanded: Boolean, capo: Int = 0) {
    val tuning = TuningCatalog.tuning(voicing.tuningId) ?: return
    val strings = tuning.strings.size
    val shownFrets = 5
    val maxFret = voicing.frets.filterNotNull().maxOrNull() ?: 1
    val firstFret = if (maxFret > shownFrets) voicing.baseFret else 1
    val density = LocalDensity.current
    val stringGap = 30.dp
    val fretGap = 32.dp
    val sidePad = 30.dp
    val topPad = 30.dp
    val stringColor = Color(0xFF807268)
    val dotRadius = with(density) { 10.dp.toPx() }
    val lineWidth = with(density) { 1.5.dp.toPx() }
    val barreWidth = dotRadius * 2
    val markerRadius = with(density) { 5.5.dp.toPx() }
    val fingerSize = with(density) { 13.sp.toPx() }
    val fretLabelSize = with(density) { 13.sp.toPx() }
    // Compact and centered like a printed chord chart: narrow strings, taller frets, markers above the nut.
    val scope = rememberCoroutineScope()
    val play = { scope.launch { ChordPlayer.play(voicing, capo) }; Unit }
    DisposableEffect(voicing.id) { onDispose { ChordPlayer.stop() } }
    Box(Modifier.fillMaxWidth().clickable(onClick = play), contentAlignment = Alignment.Center) {
        IconButton(onClick = play, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.chord_play), tint = NeonSoft)
        }
        Canvas(Modifier.width(stringGap * (strings - 1) + sidePad * 2).height(topPad + fretGap * shownFrets + 6.dp)) {
            val padX = sidePad.toPx(); val top = topPad.toPx()
            val dx = stringGap.toPx(); val dy = fretGap.toPx()
            val right = padX + dx * (strings - 1)
            val bottom = top + dy * shownFrets
            val numberPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Muted.toArgb(); textSize = fretLabelSize; textAlign = android.graphics.Paint.Align.RIGHT
            }
            if (firstFret > 1) drawContext.canvas.nativeCanvas.drawText("$firstFret", padX - dotRadius - 8.dp.toPx(), top + dy / 2f + fretLabelSize * .34f, numberPaint)
            for (visual in 0 until strings) {
                val x = padX + visual * dx
                drawLine(stringColor, Offset(x, top), Offset(x, bottom), strokeWidth = lineWidth)
            }
            // Paint the barre once, beneath all dots and finger labels; only its first dot carries the number.
            var barreLabelVisual = -1
            voicing.barreFret?.takeIf { it in firstFret until firstFret + shownFrets }?.let { fret ->
                val points = voicing.frets.indices.filter { voicing.frets[it] == fret }
                    .map { if (leftHanded) strings - 1 - it else it }.sorted()
                if (points.size > 1) {
                    barreLabelVisual = points.first()
                    val y = top + (fret - firstFret + .5f) * dy
                    drawLine(NeonSoft, Offset(padX + points.first() * dx, y), Offset(padX + points.last() * dx, y), strokeWidth = barreWidth, cap = StrokeCap.Round)
                }
            }
            val fingerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = fingerSize; textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            for (visual in 0 until strings) {
                val physical = if (leftHanded) strings - 1 - visual else visual
                val x = padX + visual * dx
                val fret = voicing.frets[physical]
                val markerY = top - 14.dp.toPx()
                when {
                    fret == null -> {
                        drawLine(White, Offset(x - markerRadius, markerY - markerRadius), Offset(x + markerRadius, markerY + markerRadius), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(White, Offset(x - markerRadius, markerY + markerRadius), Offset(x + markerRadius, markerY - markerRadius), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    fret == 0 -> drawCircle(White, radius = markerRadius, center = Offset(x, markerY), style = Stroke(width = 2.dp.toPx()))
                    fret in firstFret until firstFret + shownFrets -> {
                        val y = top + (fret - firstFret + .5f) * dy
                        drawCircle(NeonSoft, radius = dotRadius, center = Offset(x, y))
                        val onBarre = barreLabelVisual >= 0 && fret == voicing.barreFret && visual != barreLabelVisual
                        if (!onBarre) voicing.fingers.firstOrNull { it.stringIndex == physical }?.let { finger ->
                            drawContext.canvas.nativeCanvas.drawText(finger.finger.toString(), x, y + fingerSize * .34f, fingerPaint)
                        }
                    }
                }
            }
            for (fret in 0..shownFrets) {
                val y = top + fret * dy
                val nut = fret == 0
                drawLine(if (nut) White else stringColor, Offset(padX, y), Offset(right, y), strokeWidth = if (nut) 4.dp.toPx() else lineWidth)
            }
        }
    }
}

@Composable
private fun ChordModePicker(mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(mode == ChordPresentationMode.BEGINNER, { setMode(ChordPresentationMode.BEGINNER) },
            label = { Text(stringResource(R.string.chord_mode_beginner)) })
        FilterChip(mode == ChordPresentationMode.ADVANCED, { setMode(ChordPresentationMode.ADVANCED) },
            label = { Text(stringResource(R.string.chord_mode_advanced)) })
    }
}

@Composable
private fun BeginnerVoicingGuide(voicing: ChordVoicing) {
    val tuning = TuningCatalog.tuning(voicing.tuningId) ?: return
    val played = tuning.strings.indices.filter { voicing.frets[it] != null }.map { tuning.strings[it].number }
    val avoided = tuning.strings.indices.filter { voicing.frets[it] == null }.map { tuning.strings[it].number }
    Text(stringResource(R.string.chord_strings_to_play, played.joinToString(", ").ifEmpty { "—" }), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.chord_strings_to_avoid, avoided.joinToString(", ").ifEmpty { "—" }), style = MaterialTheme.typography.bodyMedium, color = Muted)
    voicing.fingers.sortedBy { it.finger }.forEach { finger ->
        val stringNumber = tuning.strings.getOrNull(finger.stringIndex)?.number ?: return@forEach
        Text(stringResource(R.string.chord_place_finger, finger.finger, stringNumber, finger.fret), style = MaterialTheme.typography.bodyMedium)
    }
    voicing.barreFret?.let { Text(stringResource(R.string.chord_place_barre, it), style = MaterialTheme.typography.bodyMedium) }
}

private fun buildChord(root: String, quality: String, extension: String): String {
    val suffix = when (quality) { "minor" -> "m"; "diminished" -> "dim"; "augmented" -> "aug"; "sus2" -> "sus2"; "sus4" -> "sus4"; else -> "" }
    return root + suffix + extension
}
private fun qualityLabel(value: String) = when (value) {
    "major" -> "major"; "minor" -> "minor"; "diminished" -> "dim"; "augmented" -> "aug"; else -> value
}
@Composable
private fun instrumentName(id: String) = stringResource(when (id) {
    "guitar" -> R.string.guitar; "ukulele" -> R.string.ukulele; "bass" -> R.string.bass
    "mandolin" -> R.string.mandolin; "banjo" -> R.string.banjo; else -> R.string.violin
})

@Composable
private fun tuningName(id: String) = stringResource(when (id) {
    "guitar.standard" -> R.string.chord_tuning_standard_short; "guitar.drop_d" -> R.string.chord_tuning_drop_d_short
    "guitar.dadgad" -> R.string.chord_tuning_dadgad_short; "ukulele.high_g" -> R.string.chord_tuning_high_g_short
    "ukulele.low_g" -> R.string.chord_tuning_low_g_short; "banjo.open_g" -> R.string.chord_tuning_open_g_short
    else -> R.string.chord_tuning_violin_short
})
