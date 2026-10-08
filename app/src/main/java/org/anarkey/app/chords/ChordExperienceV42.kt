package org.anarkey.app.chords

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.anarkey.app.R
import org.anarkey.app.ui.*
import androidx.compose.ui.draw.rotate
import org.anarkey.core.music.*

@Composable
fun ChordDictionaryScreenV42(model: ChordFavoriteViewModel, naming: NoteNaming, mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit) {
    var symbol by rememberSaveable { mutableStateOf("C") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 42.dp)) {
            Text(stringResource(R.string.chords_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            ModeMenuV42(mode, setMode)
        }
        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = SelectorShape, border = BorderStroke(1.dp, NeonSoft.copy(alpha = 0.8f)),
            contentPadding = SelectorPadding) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(chordFullNameV42(symbol, naming), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                Text(ChordSymbolFormatter.format(symbol, naming), maxLines = 1, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NeonSoft)
        }
        ChordDisplayV42(symbol, model, naming, mode, modifier = Modifier.weight(1f))
    }
    if (pickerOpen) ChordPickerV42(symbol, naming, mode, onDismiss = { pickerOpen = false }, onConfirm = { symbol = it; pickerOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordLookupSheetV42(
    symbol: String, model: ChordFavoriteViewModel, instrumentId: String?, tuningId: String?, capo: Int,
    naming: NoteNaming, mode: ChordPresentationMode,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ChordDisplayV42(symbol, model, naming, mode, instrumentId, tuningId, capo, compact = true,
            defaultToGuitar = false, modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(horizontal = 12.dp))
    }
}

@Composable
private fun ChordDisplayV42(
    symbol: String, model: ChordFavoriteViewModel, naming: NoteNaming, mode: ChordPresentationMode,
    preferredInstrument: String? = null, preferredTuning: String? = null, capo: Int = 0,
    compact: Boolean = false, defaultToGuitar: Boolean = true, modifier: Modifier = Modifier,
) {
    var instrumentId by rememberSaveable(preferredInstrument, preferredTuning, defaultToGuitar) { mutableStateOf(preferredInstrument ?: if (defaultToGuitar) "guitar" else null) }
    var tuningId by rememberSaveable(preferredInstrument, preferredTuning, defaultToGuitar) {
        mutableStateOf(preferredTuning ?: when { preferredInstrument == "guitar" || (preferredInstrument == null && defaultToGuitar) -> "guitar.standard"; preferredInstrument != null -> TuningCatalog.forInstrument(preferredInstrument).firstOrNull()?.id; else -> null })
    }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var showFingerGuide by rememberSaveable { mutableStateOf(false) }
    var showDiagramHelp by rememberSaveable { mutableStateOf(false) }
    var showMusicDetails by rememberSaveable { mutableStateOf(false) }
    val favoriteIds by model.favoriteIds.collectAsStateWithLifecycle()
    val chord = remember(symbol) { ChordTheory.resolve(symbol) }
    var activeVoicingId by rememberSaveable(symbol, instrumentId, tuningId, capo) { mutableStateOf<String?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (compact) Text(chordFullNameV42(symbol, naming), style = MaterialTheme.typography.titleLarge, color = NeonSoft, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (chord == null) {
            Text(stringResource(R.string.chord_unknown_symbol), color = Muted, style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 42.dp)) {
            if (compact) {
                val instrumentLabel = instrumentId?.let { instrumentNameV42(it) }.orEmpty()
                val tuningLabel = tuningId?.let { tuningNameV42(it) }.orEmpty().removeInstrumentPrefixV42(instrumentLabel)
                val contextLabel = if (capo > 0) stringResource(R.string.chord_context_line_capo,
                    instrumentLabel, tuningLabel, capo)
                else stringResource(R.string.chord_context_line, instrumentLabel, tuningLabel)
                Text(contextLabel,
                    Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                OrientationControlV42(leftHanded) { leftHanded = it }
            } else {
                InstrumentTuningControl(instrumentId, tuningId, onInstrument = { selected ->
                    instrumentId = selected
                    tuningId = selected?.let { TuningCatalog.forInstrument(it).firstOrNull()?.id }
                }, onTuning = { tuningId = it })
                Spacer(Modifier.weight(1f))
                OrientationControlV42(leftHanded) { leftHanded = it }
            }
        }
        val forms = ChordVoicingCatalog.forChord(symbol, instrumentId, tuningId)
        val voicings = if (mode == ChordPresentationMode.BEGINNER) ChordVoicingCatalog.prioritizeForBeginner(forms) else forms
        val index = voicings.indexOfFirst { it.id == activeVoicingId }.let { if (it < 0) 0 else it }
        val current = voicings.getOrNull(index)
        if (current == null) {
            Text(stringResource(R.string.chord_no_voicings_short), color = NeonSoft, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showMusicDetails = !showMusicDetails }, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(stringResource(if (showMusicDetails) R.string.chord_hide_music else R.string.chord_more_music))
            }
            if (showMusicDetails) AdvancedDetailsV42(symbol, chord, naming)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 38.dp)) {
                        Text(stringResource(R.string.chord_position_count, index + 1, voicings.size), Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium)
                        val favoriteDescription = chordTextV42(if (current.id in favoriteIds) R.string.chord_remove_favorite else R.string.chord_add_favorite)
                        IconButton(onClick = { model.setFavorite(current.id, current.id !in favoriteIds) }, modifier = Modifier.size(44.dp).semantics {
                            contentDescription = favoriteDescription
                        }) {
                            Icon(if (current.id in favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = NeonSoft)
                        }
                    }
                    ChordDiagram(current, leftHanded, capo)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        val previousDescription = chordTextV42(R.string.chord_previous_position)
                        IconButton(enabled = index > 0, onClick = { activeVoicingId = voicings[index - 1].id }, modifier = Modifier.size(44.dp).semantics {
                            contentDescription = previousDescription
                        }) {
                            Text("‹", fontSize = 28.sp, color = NeonSoft)
                        }
                        Spacer(Modifier.weight(1f))
                        val nextDescription = chordTextV42(R.string.chord_next_position)
                        IconButton(enabled = index < voicings.lastIndex, onClick = { activeVoicingId = voicings[index + 1].id }, modifier = Modifier.size(44.dp).semantics {
                            contentDescription = nextDescription
                        }) {
                            Text("›", fontSize = 28.sp, color = NeonSoft)
                        }
                    }
                }
            }
            if (mode == ChordPresentationMode.BEGINNER) {
                val strings = TuningCatalog.tuning(current.tuningId)?.strings.orEmpty()
                val played = strings.indices.filter { current.frets[it] != null }.map { strings[it].number }.joinToString(", ")
                val avoided = strings.indices.filter { current.frets[it] == null }.map { strings[it].number }.joinToString(", ").ifEmpty { "—" }
                Text(stringResource(R.string.chord_play_avoid_short, played, avoided), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                DisclosureButtonV42(stringResource(if (showFingerGuide) R.string.chord_hide_fingers else R.string.chord_how_fingers), showFingerGuide) { showFingerGuide = !showFingerGuide }
                if (showFingerGuide) BeginnerFingerDetails(current)
                DisclosureButtonV42(stringResource(R.string.chord_diagram_help), showDiagramHelp) { showDiagramHelp = !showDiagramHelp }
                if (showDiagramHelp) Text(stringResource(R.string.chord_diagram_legend), color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                DisclosureButtonV42(stringResource(if (showMusicDetails) R.string.chord_hide_music else R.string.chord_more_music), showMusicDetails) { showMusicDetails = !showMusicDetails }
                if (showMusicDetails) AdvancedDetailsV42(symbol, chord, naming)
            }
        }
        if (!compact && capo > 0) Text(stringResource(R.string.chord_capo_relative, capo), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InstrumentTuningControl(instrumentId: String?, tuningId: String?, onInstrument: (String?) -> Unit, onTuning: (String) -> Unit) {
    var instrumentMenu by remember { mutableStateOf(false) }
    var tuningMenu by remember { mutableStateOf(false) }
    Box {
        SelectorButton(instrumentId?.let { instrumentNameV42(it) } ?: stringResource(R.string.chord_theory_only),
            onClick = { instrumentMenu = true }, modifier = Modifier.heightIn(min = 40.dp),
            textStyle = MaterialTheme.typography.labelMedium)
        DropdownMenu(instrumentMenu, { instrumentMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.chord_theory_only)) }, onClick = { onInstrument(null); instrumentMenu = false })
            TuningCatalog.instruments.forEach { item -> DropdownMenuItem(text = { Text(instrumentNameV42(item.id)) }, onClick = { onInstrument(item.id); instrumentMenu = false }) }
        }
    }
    if (instrumentId != null) Box {
        SelectorButton(tuningId?.let { tuningNameV42(it) } ?: stringResource(R.string.chord_select_tuning),
            onClick = { tuningMenu = true }, modifier = Modifier.heightIn(min = 40.dp),
            textStyle = MaterialTheme.typography.labelSmall)
        DropdownMenu(tuningMenu, { tuningMenu = false }) {
            TuningCatalog.forInstrument(instrumentId).forEach { tuning -> DropdownMenuItem(text = { Text(tuningNameV42(tuning.id)) }, onClick = { onTuning(tuning.id); tuningMenu = false }) }
        }
    }
}

/** A text link that opens a section; the chevron makes it read as expandable and turns when open. */
@Composable
private fun DisclosureButtonV42(label: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.heightIn(min = 40.dp)) {
        Text(label)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = Muted)
    }
}

@Composable
private fun BeginnerFingerDetails(voicing: ChordVoicing) {
    val strings = TuningCatalog.tuning(voicing.tuningId)?.strings.orEmpty()
    voicing.fingers.sortedBy { it.finger }.forEach { finger ->
        strings.getOrNull(finger.stringIndex)?.let { string ->
            Text(stringResource(R.string.chord_place_finger, finger.finger, string.number, finger.fret), style = MaterialTheme.typography.bodySmall)
        }
    }
    voicing.barreFret?.let { Text(stringResource(R.string.chord_place_barre, it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun AdvancedDetailsV42(symbol: String, chord: ChordDefinition, naming: NoteNaming) {
    Text(stringResource(R.string.chord_notes, chord.tones.joinToString(" · ") { ChordSymbolFormatter.format(it.note, naming) }), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.chord_intervals, chord.tones.joinToString(" · ") { "${it.intervalName} ${ChordSymbolFormatter.format(it.note, naming)}" }), color = Muted, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.chord_formula, chord.tones.joinToString(" · ") { it.intervalName }), color = Muted, style = MaterialTheme.typography.bodySmall)
    chord.bass?.let { Text(stringResource(R.string.chord_inversion, ChordSymbolFormatter.format(it, naming)), color = Muted, style = MaterialTheme.typography.bodySmall) }
}

private data class ChordChoiceV42(val id: String, val suffix: String, val label: Int)
private val chordChoicesV42 = ChordQuality.entries.map { quality ->
    ChordChoiceV42(quality.id, quality.suffix, when (quality) {
        ChordQuality.MAJOR -> R.string.chord_quality_major
        ChordQuality.MINOR -> R.string.chord_quality_minor
        ChordQuality.DOMINANT7 -> R.string.chord_quality_dominant7
        ChordQuality.MAJOR7 -> R.string.chord_quality_major7
        ChordQuality.MINOR7 -> R.string.chord_quality_minor7
        ChordQuality.DIMINISHED -> R.string.chord_quality_dim
        ChordQuality.AUGMENTED -> R.string.chord_quality_aug
        ChordQuality.SUS2 -> R.string.chord_quality_sus2
        ChordQuality.SUS4 -> R.string.chord_quality_sus4
        ChordQuality.DIM7 -> R.string.chord_quality_dim7
        ChordQuality.HALF_DIM7 -> R.string.chord_quality_half_dim7
        ChordQuality.SIXTH -> R.string.chord_quality_sixth
        ChordQuality.MINOR6 -> R.string.chord_quality_minor6
        ChordQuality.ADD9 -> R.string.chord_quality_add9
        ChordQuality.MINOR_ADD9 -> R.string.chord_quality_minor_add9
        ChordQuality.NINTH -> R.string.chord_quality_ninth
        ChordQuality.ELEVENTH -> R.string.chord_quality_eleventh
        ChordQuality.THIRTEENTH -> R.string.chord_quality_thirteenth
        ChordQuality.POWER -> R.string.chord_quality_power
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChordPickerV42(currentSymbol: String, naming: NoteNaming, mode: ChordPresentationMode, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initial = remember(currentSymbol) { ChordSymbolParser.parse(currentSymbol) }
    val flats = naming == NoteNaming.LETTERS_FLATS || naming == NoteNaming.SOLFEGE_FLATS
    val roots = remember(naming) { (0..11).map { ChordSymbolFormatter.format(SpelledNote.fromMidi(it, flats), naming) } }
    var rootPc by remember(currentSymbol) { mutableIntStateOf(SpelledNote.parse(initial.rootLetter, initial.rootAccidental)?.pitchClass ?: 0) }
    var choiceId by remember(currentSymbol) { mutableStateOf(choiceForV42(initial).id) }
    var bassPc by remember(currentSymbol) { mutableStateOf(initial.bassLetter?.let { SpelledNote.parse(it, initial.bassAccidental)?.pitchClass }) }
    var query by remember(currentSymbol) { mutableStateOf("") }
    var changed by remember(currentSymbol) { mutableStateOf(false) }
    var moreTypes by rememberSaveable { mutableStateOf(mode == ChordPresentationMode.ADVANCED) }
    var showBass by rememberSaveable { mutableStateOf(false) }
    val rootName = roots.firstOrNull { rootPitchClassV42(it) == rootPc } ?: "C"
    val suffix = chordChoicesV42.firstOrNull { it.id == choiceId }?.suffix.orEmpty()
    val bassName = bassPc?.let { pc -> roots.firstOrNull { rootPitchClassV42(it) == pc } }
    val draft = when {
        query.isNotBlank() -> query.trim()
        changed -> rootName + suffix + (bassName?.let { "/$it" } ?: "")
        else -> currentSymbol
    }
    val shownChoices = if (mode == ChordPresentationMode.BEGINNER && !moreTypes) chordChoicesV42.take(3) else chordChoicesV42

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * .9f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.chord_picker_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { value ->
                query = value.take(40)
                val parsed = ChordSymbolParser.parse(query)
                if (parsed.interpretable) {
                    rootPc = SpelledNote.parse(parsed.rootLetter, parsed.rootAccidental)?.pitchClass ?: rootPc
                    choiceId = choiceForV42(parsed).id
                    bassPc = parsed.bassLetter?.let { SpelledNote.parse(it, parsed.bassAccidental)?.pitchClass }
                    changed = true
                }
            }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.chord_search_optional)) }, singleLine = true)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.chord_choose_root), style = MaterialTheme.typography.labelMedium, color = Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(roots) { root ->
                        val pc = rootPitchClassV42(root) ?: 0
                        FilterChip(rootPc == pc, { rootPc = pc; query = ""; changed = true }, label = { Text(root) })
                    }
                }
                Text(stringResource(R.string.chord_choose_quality), style = MaterialTheme.typography.labelMedium, color = Muted)
                shownChoices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { option ->
                            FilterChip(choiceId == option.id, { choiceId = option.id; query = ""; changed = true }, modifier = Modifier.weight(1f),
                                label = { Text(stringResource(option.label), maxLines = 2) })
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (mode == ChordPresentationMode.BEGINNER && !moreTypes) TextButton(onClick = { moreTypes = true }) { Text(stringResource(R.string.chord_more_types)) }
                if (mode == ChordPresentationMode.ADVANCED) {
                    TextButton(onClick = { showBass = !showBass }) { Text(stringResource(if (showBass) R.string.chord_hide_bass else R.string.chord_alternate_bass)) }
                    if (showBass) {
                        FilterChip(bassPc == null, { bassPc = null; query = ""; changed = true }, label = { Text(stringResource(R.string.chord_no_bass)) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(roots) { bass ->
                                val pc = rootPitchClassV42(bass)
                                FilterChip(bassPc == pc, { bassPc = pc; query = ""; changed = true }, label = { Text(bass) })
                            }
                        }
                    }
                }
            }
            Text(chordFullNameV42(draft, naming), color = NeonSoft, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Button(onClick = { onConfirm(draft) }, shape = AppShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.chord_view_chord), maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
        }
        }
    }
}

private fun rootPitchClassV42(symbol: String): Int? {
    val parsed = ChordSymbolParser.parse(symbol)
    return SpelledNote.parse(parsed.rootLetter, parsed.rootAccidental)?.pitchClass
}

private fun choiceForV42(parsed: ParsedChord): ChordChoiceV42 = when (parsed.extension) {
    "5" -> chordChoicesV42.first { it.id == "power" }
    "7" -> chordChoicesV42.first { it.id == when (parsed.quality) { "m", "min", "minor" -> "minor7"; "dim" -> "dim7"; "maj" -> "major7"; else -> "dominant7" } }
    "maj7" -> chordChoicesV42.first { it.id == "major7" }
    "m7" -> chordChoicesV42.first { it.id == "minor7" }
    "7b5" -> chordChoicesV42.first { it.id == "halfDim7" }
    "6" -> chordChoicesV42.first { it.id == if (parsed.quality in setOf("m", "min", "minor")) "minor6" else "sixth" }
    "add9" -> chordChoicesV42.first { it.id == if (parsed.quality in setOf("m", "min", "minor")) "minorAdd9" else "add9" }
    "9" -> chordChoicesV42.first { it.id == "ninth" }
    "11", "add11" -> chordChoicesV42.first { it.id == "eleventh" }
    "13" -> chordChoicesV42.first { it.id == "thirteenth" }
    else -> chordChoicesV42.first { it.id == when (parsed.quality) { "m", "min", "minor" -> "minor"; "dim" -> "diminished"; "aug" -> "augmented"; "sus2" -> "sus2"; "sus4", "sus" -> "sus4"; else -> "major" } }
}

@Composable
private fun chordFullNameV42(symbol: String, naming: NoteNaming): String {
    val chord = ChordTheory.resolve(symbol) ?: return symbol
    val root = ChordSymbolFormatter.format(chord.root, naming)
    val quality = stringResource(choiceForV42(ChordSymbolParser.parse(symbol)).label)
    val bass = chord.bass?.let { stringResource(R.string.chord_name_over, ChordSymbolFormatter.format(it, naming)) }
    return listOf(root, quality, bass).filterNotNull().joinToString(" ")
}

@Composable
private fun ModeMenuV42(mode: ChordPresentationMode, setMode: (ChordPresentationMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SelectorButton(stringResource(if (mode == ChordPresentationMode.BEGINNER) R.string.chord_mode_beginner else R.string.chord_mode_advanced),
            onClick = { open = true }, modifier = Modifier.heightIn(min = 40.dp), textStyle = MaterialTheme.typography.labelMedium)
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.chord_mode_beginner)) }, onClick = { setMode(ChordPresentationMode.BEGINNER); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.chord_mode_advanced)) }, onClick = { setMode(ChordPresentationMode.ADVANCED); open = false })
        }
    }
}

@Composable
private fun OrientationControlV42(leftHanded: Boolean, onSelect: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val orientationDescription = chordTextV42(R.string.chord_orientation)
    val handDescription = chordTextV42(if (leftHanded) R.string.chord_left else R.string.chord_right)
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp).semantics {
            contentDescription = "$orientationDescription: $handDescription"
        }) { Text("⇄", color = NeonSoft, fontSize = 20.sp) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.chord_right)) }, onClick = { onSelect(false); expanded = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.chord_left)) }, onClick = { onSelect(true); expanded = false })
        }
    }
}

@Composable
private fun instrumentNameV42(id: String) = stringResource(when (id) {
    "guitar" -> R.string.guitar; "ukulele" -> R.string.ukulele; "bass" -> R.string.bass
    "mandolin" -> R.string.mandolin; "banjo" -> R.string.banjo; else -> R.string.violin
})

@Composable
private fun tuningNameV42(id: String) = stringResource(when (id) {
    "guitar.standard" -> R.string.chord_tuning_standard_short; "guitar.drop_d" -> R.string.chord_tuning_drop_d_short
    "guitar.dadgad" -> R.string.chord_tuning_dadgad_short; "ukulele.high_g" -> R.string.chord_tuning_high_g_short
    "ukulele.low_g" -> R.string.chord_tuning_low_g_short; "banjo.open_g" -> R.string.chord_tuning_open_g_short
    else -> R.string.chord_tuning_violin_short
})

private fun String.removeInstrumentPrefixV42(instrument: String): String {
    if (instrument.isBlank()) return this
    val prefix = "$instrument · "
    return if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this
}

@Composable
private fun chordTextV42(id: Int): String = LocalContext.current.getString(id)
