package org.anarkey.app.songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.anarkey.app.R
import org.anarkey.app.recording.data.*
import org.anarkey.app.ui.*
import org.anarkey.core.music.*
import org.anarkey.app.chords.ChordFavoriteViewModel
import org.anarkey.app.chords.ChordLookupSheetV42

@Composable
fun SongLibraryScreen(model: SongViewModel, openSong: (String) -> Unit) {
    val songs by model.songs.collectAsStateWithLifecycle()
    val tags by model.tags.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selectedTag = tags.firstOrNull { it.id == tagFilter }
    val tagSongs by model.songsForTag(tagFilter).collectAsStateWithLifecycle(emptyList())
    val sourceSongs = if (tagFilter == null) songs else tagSongs
    val shown = sourceSongs.filter { song -> (!favoritesOnly || song.favorite) &&
        (query.isBlank() || song.title.contains(query, true) || song.artist.orEmpty().contains(query, true)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.song_search)) })
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text(stringResource(R.string.song_filter_favorites)) })
                var expanded by remember { mutableStateOf(false) }
                Box {
                    SelectorButton(selectedTag?.name ?: stringResource(R.string.song_filter_tag), onClick = { expanded = true },
                        textStyle = MaterialTheme.typography.labelSmall, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp))
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_filter_all)) }, onClick = { tagFilter = null; expanded = false })
                        tags.forEach { tag -> DropdownMenuItem(text = { Text(tag.name) }, onClick = { tagFilter = tag.id; expanded = false }) }
                    }
                }
            }
            if (songs.isNotEmpty()) TextButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.End)) {
                Text("+ " + stringResource(R.string.song_new))
            }
        }
        if (shown.isEmpty()) {
            if (songs.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(40.dp), tint = NeonSoft)
                Text(stringResource(R.string.song_empty), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.song_empty_hint), color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { creating = true }, shape = AppShape) { Text(stringResource(R.string.song_create_first)) }
            } else Text(stringResource(R.string.song_empty_filtered), color = Muted, modifier = Modifier.padding(top = 22.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(shown, key = { it.id }) { song ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { openSong(song.id) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = NeonSoft)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                            song.artist?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                            song.bpm?.let { Text("$it · ${if (song.timeNumerator == 6 && song.timeDenominator == 8) contextString(R.string.song_bpm_dotted_quarter) else contextString(R.string.song_bpm_quarter)}", color = NeonSoft, style = MaterialTheme.typography.labelSmall) }
                        }
                        Icon(if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(R.string.song_favorite), tint = if (song.favorite) NeonSoft else Muted,
                            modifier = Modifier.clickable { model.favorite(song.id, !song.favorite) }.padding(8.dp))
                    }
                }
            }
        }
    }
    if (creating) CreateSongDialog(onDismiss = { creating = false }, onCreate = { title -> model.create(title) { id -> creating = false; openSong(id) } })
}

@Composable
private fun CreateSongDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_new)) },
        text = { Column { Text(stringResource(R.string.song_create_hint), color = Muted); OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors()) } },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
fun SongDetailScreen(model: SongViewModel, chordModel: ChordFavoriteViewModel, songId: String, openTuner: (TunerConfiguration) -> Unit,
    openMetronome: (Int?, Int?, Int?) -> Unit,
    openRecording: (String) -> Unit, onDeleted: () -> Unit, onStartRecording: () -> Unit, a4Hz: Double, naming: NoteNaming,
    chordMode: ChordPresentationMode, setChordMode: (ChordPresentationMode) -> Unit) {
    val document by model.document.collectAsStateWithLifecycle()
    val allRecordings by model.recordings.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    LaunchedEffect(songId) { model.load(songId) }
    var editMetadata by remember { mutableStateOf(false) }
    var showTranspose by remember(songId) { mutableStateOf(false) }
    var editLyrics by remember { mutableStateOf<SongSectionEntity?>(null) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var renameSection by remember { mutableStateOf<SongSectionEntity?>(null) }
    var addSection by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var pendingChord by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selectedChord by remember { mutableStateOf<ChordPlacementEntity?>(null) }
    var lookupChord by remember { mutableStateOf<String?>(null) }
    var editChord by remember { mutableStateOf<ChordPlacementEntity?>(null) }
    var cursor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val song = document?.takeIf { it.song.id == songId }
    if (song == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                song.song.artist?.let { Text(it, color = Muted) }
                val beatUnit = if (song.song.timeNumerator == 6 && song.song.timeDenominator == 8) R.string.song_bpm_dotted_quarter else R.string.song_bpm_quarter
                val songMeta = listOfNotNull(song.song.keyRoot?.let { it + (song.song.keyMode?.let { mode -> if (mode == "minor") " minor" else " major" } ?: "") },
                    song.song.bpm?.let { "$it · ${contextString(beatUnit)}" },
                    if (song.song.timeNumerator != null) "${song.song.timeNumerator}/${song.song.timeDenominator}" else null,
                    song.song.capo.takeIf { it > 0 }?.let { "Capo $it" }).joinToString("   ·   ")
                if (songMeta.isNotBlank()) Text(songMeta, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { model.favorite(songId, !song.song.favorite) }) {
                Icon(if (song.song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(R.string.song_favorite), tint = NeonSoft)
            }
            IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = NeonSoft) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editMetadata = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.song_edit_details)) }
            TextButton(onClick = { reading = !reading }, modifier = Modifier.weight(1f)) { Text(stringResource(if (reading) R.string.edit else R.string.song_read)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editLyrics = null; showLyricsEditor = true }) { Text(stringResource(R.string.song_edit_lyrics)) }
            TextButton(onClick = { showTranspose = true }) { Text(stringResource(R.string.song_transpose)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onStartRecording) { Text(stringResource(R.string.song_record_new)) }
            TextButton(onClick = { openMetronome(song.song.bpm, song.song.timeNumerator, song.song.timeDenominator) }) { Text(stringResource(R.string.metronome_title)) }
            if (song.song.instrumentId != null && song.song.tuningId != null) TextButton(onClick = {
                runCatching { openTuner(TunerConfiguration(TunerSelection(song.song.instrumentId, song.song.tuningId), a4Hz)) }
                    .onFailure { model.reportError(it.message ?: "tuning_unavailable") }
            }) { Text(stringResource(R.string.tuner_title)) }
        }
        if (song.song.instrumentId == null || song.song.tuningId == null) Text(stringResource(R.string.song_tuner_missing), color = Muted, style = MaterialTheme.typography.bodySmall)
        val recs = allRecordings.filter { it.songId == songId && it.status in setOf(org.anarkey.core.recording.RecordingStatus.READY, org.anarkey.core.recording.RecordingStatus.RECOVERY_REQUIRED) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.song_recordings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                SelectorButton(stringResource(R.string.song_link_recording), onClick = { linkMenu = true })
                DropdownMenu(linkMenu, { linkMenu = false }) {
                    allRecordings.filter { it.songId == null && it.status == org.anarkey.core.recording.RecordingStatus.READY }.forEach { row ->
                        DropdownMenuItem(text = { Text(row.displayName) }, onClick = { model.setRecordingSong(row.id, songId); linkMenu = false })
                    }
                }
            }
        }
        if (recs.isEmpty()) Text(stringResource(R.string.song_no_recordings), color = Muted)
        recs.forEach { recording -> Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { openRecording(recording.id) }, modifier = Modifier.weight(1f)) { Text("▶  ${recording.displayName}") }
            TextButton(onClick = { model.setRecordingSong(recording.id, null) }) { Text(stringResource(R.string.song_unlink_recording)) }
        } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.song_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { addTag = true }) { Text(stringResource(R.string.song_add_tag)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { song.tags.forEach { tag ->
            InputChip(selected = false, onClick = { model.removeTag(songId, tag.id) }, label = { Text(tag.name) })
        } }

        if (!reading) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.song_lyrics), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { addSection = true }) { Text("+ " + stringResource(R.string.song_add_section)) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            val groups = buildList {
                add(null to song.lines.filter { it.sectionId == null }.sortedBy { it.position })
                song.sections.sortedBy { it.position }.forEach { section -> add(section to song.lines.filter { it.sectionId == section.id }.sortedBy { it.position }) }
            }
            groups.forEach { (section, lines) ->
                if (section != null) item(key = "header-${section.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, color = NeonSoft, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!reading) {
                        TextButton(onClick = { editLyrics = section; showLyricsEditor = true }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { model.reorderSection(songId, section.id, -1) }, enabled = section.position > 0, contentPadding = PaddingValues(2.dp)) { Text(stringResource(R.string.song_reorder_up)) }
                            TextButton(onClick = { model.reorderSection(songId, section.id, 1) }, contentPadding = PaddingValues(2.dp)) { Text(stringResource(R.string.song_reorder_down)) }
                            TextButton(onClick = { renameSection = section }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { model.deleteSection(songId, section.id) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
                if (lines.isNotEmpty()) items(lines, key = { it.id }) { line ->
                    SongLineRow(line, song.chords[line.id].orEmpty(), reading, selectedChord?.id, song.sections, naming,
                        onCursor = { position -> cursor = line.id to position }, onAdd = { position -> pendingChord = line.id to position },
                        onChord = { if (reading) lookupChord = it.originalSymbol else selectedChord = it }, onMoveSelected = { selectedChord?.let { chord ->
                            cursor?.let { (lineId, position) -> model.editChord(songId, chord.id, chord.originalSymbol, position, lineId); selectedChord = null }
                        } }, onEditText = { value -> model.updateLineText(songId, line.id, value) },
                        onMoveLine = { target -> model.moveLine(songId, line.id, target) },
                        onReorder = { delta -> model.reorderLine(songId, line.id, delta) },
                        onDeleteChord = { id -> model.deleteChord(songId, id); if (selectedChord?.id == id) selectedChord = null })
                }
                if (!reading) item(key = "edit-${section?.id ?: "root"}") {
                    TextButton(onClick = { editLyrics = section; showLyricsEditor = true }) { Text("+ " + stringResource(R.string.song_edit_lyrics)) }
                }
            }
        }
    }
    if (showTranspose) SongTransposeDialog(song, naming, onDismiss = { showTranspose = false },
        onApply = { interval, flats, done -> model.transpose(songId, interval, flats) { showTranspose = false; done() } })
    if (editMetadata) SongMetadataDialog(song.song, onDismiss = { editMetadata = false }, onSave = { args ->
        model.saveMetadata(songId, args.title, args.artist, args.keyRoot, args.keyMode, args.bpm, args.numerator, args.denominator,
            args.instrument, args.tuning, args.capo, args.notes); editMetadata = false
    })
    if (showLyricsEditor) LyricsDialog(song, editLyrics, onDismiss = { showLyricsEditor = false },
        onSave = { sectionId, text -> model.replaceLyrics(songId, sectionId, text); showLyricsEditor = false })
    renameSection?.let { section -> TextEntryDialog(stringResource(R.string.song_section_title), stringResource(R.string.song_section_title),
        { renameSection = null }, { model.renameSection(songId, section.id, it); renameSection = null }, initial = section.title) }
    if (addSection) SectionDialog(onDismiss = { addSection = false }, onAdd = { title, kind -> model.createSection(songId, title, kind); addSection = false })
    if (addTag) TextEntryDialog(stringResource(R.string.song_add_tag), stringResource(R.string.song_tag_name), { addTag = false }, { model.addTag(songId, it); addTag = false })
    pendingChord?.let { (lineId, pos) -> ChordDialog(null, { pendingChord = null }, { symbol -> model.placeChord(songId, lineId, pos, symbol); pendingChord = null }) }
    selectedChord?.let { chord -> if (pendingChord == null) {
        // Selecting a chip exposes edit, delete, and a cursor-based move affordance without numeric coordinates.
        AlertDialog(onDismissRequest = { selectedChord = null }, title = { Text(chord.originalSymbol) },
            text = { Text(stringResource(R.string.song_move_chord)) },
            confirmButton = { TextButton(enabled = cursor != null, onClick = {
                cursor?.let { (lineId, position) -> model.editChord(songId, chord.id, chord.originalSymbol, position, lineId) }
                selectedChord = null
            }) { Text(stringResource(R.string.song_move_chord)) } },
            dismissButton = { Row {
                TextButton(onClick = { editChord = chord; selectedChord = null }) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { model.deleteChord(songId, chord.id); selectedChord = null }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = { selectedChord = null }) { Text(stringResource(R.string.close)) }
            } })
    } }
    editChord?.let { chord -> ChordDialog(chord.originalSymbol, { editChord = null }, { symbol ->
        val target = cursor ?: (chord.lineId to chord.position)
        model.editChord(songId, chord.id, symbol, target.second, target.first); editChord = null
    }) }
    lookupChord?.let { symbol -> ChordLookupSheetV42(symbol, chordModel, song.song.instrumentId, song.song.tuningId, song.song.capo,
        naming, chordMode) { lookupChord = null } }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text(stringResource(R.string.song_delete_title)) },
        text = { Text(stringResource(R.string.song_delete_body)) },
        confirmButton = { TextButton(onClick = { model.delete(songId) { showDelete = false; onDeleted() } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(android.R.string.cancel)) } })
    error?.let { message -> AlertDialog(onDismissRequest = model::clearError, title = { Text(stringResource(R.string.song_edit_details)) },
        text = { Text(userError(message)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(android.R.string.ok)) } }) }
}

@Composable
private fun SongLineRow(line: SongLineEntity, chords: List<ChordPlacementEntity>, reading: Boolean, selectedChordId: String?,
    sections: List<SongSectionEntity>, naming: NoteNaming,
    onCursor: (Int) -> Unit, onAdd: (Int) -> Unit, onChord: (ChordPlacementEntity) -> Unit,
    onMoveSelected: () -> Unit, onEditText: (String) -> Unit, onMoveLine: (String?) -> Unit,
    onReorder: (Int) -> Unit, onDeleteChord: (String) -> Unit) {
    var field by remember(line.id) { mutableStateOf(TextFieldValue(line.text, TextRange(line.text.length))) }
    LaunchedEffect(line.text) { if (line.text != field.text) field = TextFieldValue(line.text, TextRange(line.text.length)) }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (chords.isNotEmpty()) {
            val chordLine = buildString {
                var placed = 0
                chords.sortedWith(compareBy<ChordPlacementEntity> { it.position }.thenBy { it.orderInPosition }).forEach { chord ->
                    val target = chord.position.coerceAtLeast(placed)
                    repeat(target - placed) { append(' ') }
                    append(chord.originalSymbol)
                    placed = target + chord.originalSymbol.length
                }
            }
            if (reading) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                var placed = 0
                chords.sortedWith(compareBy<ChordPlacementEntity> { it.position }.thenBy { it.orderInPosition }).forEach { chord ->
                    val target = chord.position.coerceAtLeast(placed)
                    if (target > placed) Spacer(Modifier.width(((target - placed) * 10).dp))
                    TextButton(onClick = { onChord(chord) }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                        Text(ChordSymbolFormatter.format(chord.originalSymbol, naming), style = style.copy(color = NeonSoft), maxLines = 1)
                    }
                    placed = target + chord.originalSymbol.length
                }
            } else Text(chordLine, style = style.copy(color = NeonSoft), modifier = Modifier.fillMaxWidth())
        }
        if (reading) Text(line.text.ifEmpty { " " }, style = style, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            val addChordDescription = stringResource(R.string.song_add_chord)
            BasicTextField(value = field, onValueChange = { updated ->
                field = updated; onCursor(updated.selection.start.codePointOffset(updated.text)); onEditText(updated.text)
            }, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textStyle = style,
                keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = false), singleLine = true,
                decorationBox = { inner -> if (field.text.isEmpty()) Text("…", color = Muted); inner() })
            TextButton(onClick = { onCursor(field.selection.start.codePointOffset(field.text)); onAdd(field.selection.start.codePointOffset(field.text)) },
                contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.semantics { contentDescription = addChordDescription }) { Text("+ ♬") }
        }
        if (!reading) {
            var sectionMenu by remember(line.id) { mutableStateOf(false) }
            val reorderUpDescription = stringResource(R.string.song_reorder_up)
            val reorderDownDescription = stringResource(R.string.song_reorder_down)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onReorder(-1) }, modifier = Modifier.semantics { contentDescription = reorderUpDescription }) { Icon(Icons.Default.KeyboardArrowUp, reorderUpDescription, tint = NeonSoft) }
                IconButton(onClick = { onReorder(1) }, modifier = Modifier.semantics { contentDescription = reorderDownDescription }) { Icon(Icons.Default.KeyboardArrowDown, reorderDownDescription, tint = NeonSoft) }
                Box {
                    SelectorButton(stringResource(R.string.song_section_choose), onClick = { sectionMenu = true },
                        textStyle = MaterialTheme.typography.labelSmall,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp))
                    DropdownMenu(sectionMenu, { sectionMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_lyrics)) }, onClick = { onMoveLine(null); sectionMenu = false })
                        sections.forEach { section -> DropdownMenuItem(text = { Text(section.title) }, onClick = { onMoveLine(section.id); sectionMenu = false }) }
                    }
                }
            }
        }
        if (!reading && chords.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chords.forEach { chord ->
                val description = if (chord.rootLetter == null) "${chord.originalSymbol}. ${contextString(R.string.song_unknown_chord)}" else chord.originalSymbol
                AssistChip(onClick = { onChord(chord) }, label = { Text(chord.originalSymbol) }, modifier = Modifier.semantics { contentDescription = description },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (chord.id == selectedChordId) Line else Panel))
            }
        }
    }
}

private fun Int.codePointOffset(text: String): Int = text.codePointCount(0, coerceIn(0, text.length))

private data class MetadataArgs(val title: String, val artist: String?, val keyRoot: String?, val keyMode: String?, val bpm: Int?,
    val numerator: Int?, val denominator: Int?, val instrument: String?, val tuning: String?, val capo: Int, val notes: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMetadataDialog(song: SongEntity, onDismiss: () -> Unit, onSave: (MetadataArgs) -> Unit) {
    var title by remember(song.id) { mutableStateOf(song.title) }; var artist by remember(song.id) { mutableStateOf(song.artist.orEmpty()) }
    var key by remember(song.id) { mutableStateOf(song.keyRoot.orEmpty()) }; var bpm by remember(song.id) { mutableStateOf(song.bpm?.toString().orEmpty()) }
    var mode by remember(song.id) { mutableStateOf(song.keyMode.orEmpty()) }
    var meter by remember(song.id) { mutableStateOf(if (song.timeNumerator == null) "" else "${song.timeNumerator}/${song.timeDenominator}") }
    var capo by remember(song.id) { mutableStateOf(song.capo.toString()) }; var notes by remember(song.id) { mutableStateOf(song.notes) }
    var instrument by remember(song.id) { mutableStateOf(song.instrumentId) }; var tuning by remember(song.id) { mutableStateOf(song.tuningId) }
    var instrumentExpanded by remember { mutableStateOf(false) }; var tuningExpanded by remember { mutableStateOf(false) }
    val catalog = TuningCatalog.instruments
    val availableTunings = instrument?.let(TuningCatalog::forInstrument).orEmpty()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_edit_details)) },
        text = { Column(Modifier.heightIn(max = 520.dp).widthIn(max = 440.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.song_artist)) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.song_metadata)) }, singleLine = true, placeholder = { Text("C, F#, Bb…") }, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            Row(verticalAlignment = Alignment.CenterVertically) {
                var modeMenu by remember { mutableStateOf(false) }
                SelectorButton(stringResource(R.string.song_key_mode) + ": " + if (mode == "minor") stringResource(R.string.song_minor) else stringResource(R.string.song_major),
                    onClick = { modeMenu = true })
                DropdownMenu(modeMenu, { modeMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.song_major)) }, onClick = { mode = "major"; modeMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.song_minor)) }, onClick = { mode = "minor"; modeMenu = false })
                }
            }
            OutlinedTextField(bpm, { bpm = it.filter(Char::isDigit) }, label = { Text(stringResource(if (meter == "6/8") R.string.song_bpm_dotted_quarter else R.string.song_bpm_quarter)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            OutlinedTextField(meter, { meter = it.filter { c -> c.isDigit() || c == '/' }.take(5) }, label = { Text(stringResource(R.string.song_meter)) }, placeholder = { Text("4/4") }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            OutlinedTextField(capo, { capo = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.song_capo)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            ExposedDropdownMenuBox(instrumentExpanded, { instrumentExpanded = it }) {
                OutlinedTextField(instrument?.let { id -> catalog.firstOrNull { it.id == id }?.let { instrumentName(it.id) } } ?: stringResource(R.string.song_no_instrument),
                    {}, readOnly = true, label = { Text(stringResource(R.string.song_instrument)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = instrumentExpanded) },
                    shape = FormFieldShape, colors = AnarkeyFormFieldColors(),
                    modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(instrumentExpanded, { instrumentExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.song_no_instrument)) }, onClick = { instrument = null; tuning = null; instrumentExpanded = false })
                    catalog.forEach { item -> DropdownMenuItem(text = { Text(instrumentName(item.id)) }, onClick = { instrument = item.id; tuning = null; instrumentExpanded = false }) }
                }
            }
            if (instrument != null) ExposedDropdownMenuBox(tuningExpanded, { tuningExpanded = it }) {
                OutlinedTextField(tuning?.let { tuningName(it) } ?: stringResource(R.string.song_no_instrument), {}, readOnly = true,
                    label = { Text(stringResource(R.string.tuning_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tuningExpanded) },
                    shape = FormFieldShape, colors = AnarkeyFormFieldColors(),
                    modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(tuningExpanded, { tuningExpanded = false }) { availableTunings.forEach { item ->
                    DropdownMenuItem(text = { Text(tuningName(item.id)) }, onClick = { tuning = item.id; tuningExpanded = false })
                } }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.song_notes)) }, minLines = 2, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
        } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val parts = meter.split('/'); val numerator = parts.getOrNull(0)?.toIntOrNull(); val denominator = parts.getOrNull(1)?.toIntOrNull()
            onSave(MetadataArgs(title, artist.ifBlank { null }, key.ifBlank { null }?.let(::canonicalKey), mode.takeIf { key.isNotBlank() }?.ifBlank { null }, bpm.toIntOrNull(), numerator, denominator, instrument, tuning, capo.toIntOrNull() ?: 0, notes))
        }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

private fun canonicalKey(raw: String): String = raw.trim().replace("♯", "#").replace("♭", "b").let { value ->
    value.take(1).uppercase() + value.drop(1).lowercase()
}

@Composable private fun instrumentName(id: String) = stringResource(when (id) { "guitar" -> R.string.guitar; "ukulele" -> R.string.ukulele; "violin" -> R.string.violin
    "bass" -> R.string.bass; "mandolin" -> R.string.mandolin; "banjo" -> R.string.banjo; else -> R.string.song_no_instrument })
@Composable private fun tuningName(id: String) = stringResource(when (id) {
    "guitar.standard", "violin.standard", "bass.standard", "mandolin.standard" -> R.string.tuning_standard
    "banjo.open_g" -> R.string.tuning_open_g
    "guitar.drop_d" -> R.string.tuning_drop_d; "guitar.dadgad" -> R.string.tuning_dadgad
    "ukulele.high_g" -> R.string.tuning_high_g; "ukulele.low_g" -> R.string.tuning_low_g; else -> R.string.song_no_instrument
})

@Composable private fun LyricsDialog(document: SongDocument, section: SongSectionEntity?, onDismiss: () -> Unit, onSave: (String?, String) -> Unit) {
    val lines = document.lines.filter { it.sectionId == section?.id }.sortedBy { it.position }
    var text by remember(document.song.id, section?.id) { mutableStateOf(lines.joinToString("\n") { it.text }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(section?.title ?: stringResource(R.string.song_lyrics)) },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 360.dp),
            placeholder = { Text(stringResource(R.string.song_lyrics_hint)) }, minLines = 6, maxLines = 18,
            shape = FormFieldShape, colors = AnarkeyFormFieldColors()) },
        confirmButton = { TextButton(onClick = { onSave(section?.id, text) }) { Text(stringResource(R.string.song_save_lyrics)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun SectionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val names = listOf(R.string.song_section_intro to stringResource(R.string.song_section_intro), R.string.song_section_verse to stringResource(R.string.song_section_verse),
        R.string.song_section_prechorus to stringResource(R.string.song_section_prechorus), R.string.song_section_chorus to stringResource(R.string.song_section_chorus),
        R.string.song_section_bridge to stringResource(R.string.song_section_bridge), R.string.song_section_solo to stringResource(R.string.song_section_solo),
        R.string.song_section_outro to stringResource(R.string.song_section_outro))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_add_section)) },
        text = { Column { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.song_section_title)) }, shape = FormFieldShape, colors = AnarkeyFormFieldColors()); names.chunked(2).forEach { row -> Row {
            row.forEach { (resId, name) -> TextButton(onClick = { onAdd(name, when (resId) {
                R.string.song_section_intro -> "intro"; R.string.song_section_verse -> "verse"; R.string.song_section_prechorus -> "prechorus"
                R.string.song_section_chorus -> "chorus"; R.string.song_section_bridge -> "bridge"; R.string.song_section_solo -> "solo"; else -> "outro"
            }) }) { Text(name) } }
        } } } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title, "custom") }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun ChordDialog(existing: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var symbol by remember(existing) { mutableStateOf(existing.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_chord_symbol)) },
        text = { OutlinedTextField(symbol, { symbol = it }, label = { Text(stringResource(R.string.song_chord_symbol)) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors()) },
        confirmButton = { TextButton(enabled = symbol.isNotBlank(), onClick = { onSave(symbol) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun TextEntryDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit, initial: String = "") {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors()) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun userError(message: String): String = stringResource(when {
    message.contains("bpm") -> R.string.song_error_bpm
    message.contains("time_signature") || message.contains("time_", true) -> R.string.song_error_meter
    message.contains("capo") -> R.string.song_error_capo
    message.contains("tuning") || message.contains("instrument") -> R.string.song_error_tuning
    message.contains("title") -> R.string.song_error_title
    else -> R.string.song_error_title
})

@Composable private fun contextString(id: Int): String = androidx.compose.ui.platform.LocalContext.current.getString(id)
