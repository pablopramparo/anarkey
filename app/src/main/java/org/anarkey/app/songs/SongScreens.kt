package org.anarkey.app.songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import org.anarkey.app.chords.InstrumentTuningControl

@Composable
fun SongLibraryScreen(model: SongViewModel, openSong: (String) -> Unit) {
    val songs by model.songs.collectAsStateWithLifecycle()
    val tags by model.tags.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selectedTag = tags.firstOrNull { it.id == tagFilter }
    val verseLabel = stringResource(R.string.song_section_verse)
    val chorusLabel = stringResource(R.string.song_section_chorus)
    val demoMissing = DemoSongs.all.any { demo -> songs.none { it.title == demo.title } }
    val sectionTitle: (String, Int, Int) -> String = { kind, ordinal, total ->
        val label = if (kind == "chorus") chorusLabel else verseLabel
        if (total > 1) "$label $ordinal" else label
    }
    val addDemos = { model.addDemoSongs(sectionTitle); Unit }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importPreview by model.importPreview.collectAsStateWithLifecycle()
    val exchangeError by model.exchangeError.collectAsStateWithLifecycle()
    val untitledSong = stringResource(R.string.exchange_untitled_song)
    var backupFile by remember { mutableStateOf<SongViewModel.ExportFile?>(null) }
    var libraryMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val (text, title) = withContext(Dispatchers.IO) { readDocument(context, uri) }
            model.previewImport(text, title)
        }
    }
    var importInfo by remember { mutableStateOf(false) }
    // The system picker shows every file, so say first which ones this accepts.
    val openPicker = { importInfo = true }
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
            if (songs.isNotEmpty()) Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                // Creating and importing are the two ways to add a song, so both are in plain sight.
                OutlinedButton(onClick = openPicker, shape = AppShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.exchange_import_short))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { creating = true }, shape = AppShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.song_new))
                }
                Box {
                    val moreDescription = stringResource(R.string.exchange_more)
                    IconButton(onClick = { libraryMenu = true }, Modifier.semantics { contentDescription = moreDescription }) {
                        Icon(Icons.Default.MoreVert, null, tint = Muted)
                    }
                    DropdownMenu(libraryMenu, { libraryMenu = false }) {
                        if (demoMissing) DropdownMenuItem(text = { Text(stringResource(R.string.song_demo_add)) }, onClick = { libraryMenu = false; addDemos() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.exchange_backup)) },
                            onClick = { libraryMenu = false; model.exportNative(null) { backupFile = it } })
                    }
                }
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
                OutlinedButton(onClick = openPicker, shape = AppShape) { Text(stringResource(R.string.exchange_import)) }
                if (demoMissing) OutlinedButton(onClick = addDemos, shape = AppShape) { Text(stringResource(R.string.song_demo_add)) }
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
    if (importInfo) ImportInfoDialog(onChoose = { importInfo = false; picker.launch(arrayOf("*/*")) }, onDismiss = { importInfo = false })
    importPreview?.let { candidates ->
        ImportPreviewDialog(candidates, onDismiss = model::clearImport, onConfirm = { chosen ->
            model.confirmImport(chosen, sectionTitle, untitledSong) { added, first ->
                Toast.makeText(context, context.getString(R.string.exchange_imported, added), Toast.LENGTH_SHORT).show()
                if (added == 1 && first != null) openSong(first)
            }
        })
    }
    backupFile?.let { file ->
        ExportDialog(file, native = true, onDismiss = { backupFile = null },
            onShare = { shareExport(context, file, file.fileName); backupFile = null })
    }
    exchangeError?.let { code ->
        AlertDialog(onDismissRequest = model::clearExchangeError, title = { Text(stringResource(R.string.exchange_error_title)) },
            text = { Text(exchangeErrorText(code)) },
            confirmButton = { TextButton(onClick = model::clearExchangeError) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
private fun CreateSongDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.song_new)) },
        text = { Column { Text(stringResource(R.string.song_create_hint), color = Muted); OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors()) } },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
fun SongDetailScreen(model: SongViewModel, chordModel: ChordFavoriteViewModel, songId: String,
    openMetronome: (Int?, Int?, Int?) -> Unit,
    openRecording: (String) -> Unit, onDeleted: () -> Unit, onStartRecording: () -> Unit, a4Hz: Double, naming: NoteNaming,
    chordMode: ChordPresentationMode, setChordMode: (ChordPresentationMode) -> Unit,
    chordInstrument: ChordInstrumentChoice, setChordInstrument: (ChordInstrumentChoice) -> Unit) {
    val document by model.document.collectAsStateWithLifecycle()
    val allRecordings by model.recordings.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(songId) { model.load(songId) }
    DisposableEffect(songId) { onDispose { model.stopPlayback() } }
    // The screen stays awake while the chords play, so the highlighted lyric can be followed hands-free.
    val view = LocalView.current
    val isPlaying = playing != null
    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
    // Reading is the default; edits are written as they are made, so leaving edit mode never discards anything.
    var editing by rememberSaveable(songId) { mutableStateOf(false) }
    var collapsed by rememberSaveable(songId) { mutableStateOf("") }
    var showRecordings by rememberSaveable(songId) { mutableStateOf(false) }
    var showTags by rememberSaveable(songId) { mutableStateOf(false) }
    var editMetadata by rememberSaveable { mutableStateOf(false) }
    var showTranspose by rememberSaveable(songId) { mutableStateOf(false) }
    var addSection by rememberSaveable { mutableStateOf(false) }
    var addTag by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    var editLyrics by remember { mutableStateOf<SongSectionEntity?>(null) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var renameSection by remember { mutableStateOf<SongSectionEntity?>(null) }
    var sectionToDelete by remember { mutableStateOf<SongSectionEntity?>(null) }
    var pendingChord by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selectedChordId by remember { mutableStateOf<String?>(null) }
    var lookupChord by remember { mutableStateOf<String?>(null) }
    var editChord by remember { mutableStateOf<ChordPlacementEntity?>(null) }
    var cursor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var exportFile by remember { mutableStateOf<Pair<SongViewModel.ExportFile, Boolean>?>(null) }
    val context = LocalContext.current
    BackHandler(enabled = editing) { editing = false; selectedChordId = null }

    // Chords sit on lyric characters, so both rows use one monospace style and its measured advance.
    val lineStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(lineStyle, density) { with(density) { (measurer.measure("0".repeat(20), lineStyle).size.width / 20f).toDp() } }

    val song = document?.takeIf { it.song.id == songId }
    if (song == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    // A song keeps its own instrument; one that never had any follows the last instrument used for chords.
    val ownInstrument = song.song.instrumentId
    val instrumentId = ownInstrument ?: chordInstrument.instrumentId
    val tuningId = if (ownInstrument != null) song.song.tuningId else chordInstrument.tuningId
    val chooseInstrument = { id: String?, tuning: String? ->
        model.setInstrument(songId, id, tuning); setChordInstrument(ChordInstrumentChoice(id, tuning)); Unit
    }
    val collapsedIds = remember(collapsed) { collapsed.split(',').filter { it.isNotEmpty() }.toSet() }
    fun toggleSection(id: String) { collapsed = (if (id in collapsedIds) collapsedIds - id else collapsedIds + id).joinToString(",") }
    val selectedChord = selectedChordId?.let { id -> song.chords.values.flatten().firstOrNull { it.id == id } }
    val groups = buildList {
        val rootLines = song.lines.filter { it.sectionId == null }.sortedBy { it.position }
        if (rootLines.isNotEmpty() || editing) add(null to rootLines)
        song.sections.sortedBy { it.position }.forEach { section -> add(section to song.lines.filter { it.sectionId == section.id }.sortedBy { it.position }) }
    }
    fun sectionKey(section: SongSectionEntity?) = "section-${section?.id ?: "root"}"
    val recs = allRecordings.filter { it.songId == songId && it.status in setOf(org.anarkey.core.recording.RecordingStatus.READY, org.anarkey.core.recording.RecordingStatus.RECOVERY_REQUIRED) }
    val hasChords = song.chords.values.any { it.isNotEmpty() }

    // Follow the chord that is sounding: open its section and bring the section into view (the line scrolls itself).
    LaunchedEffect(playing?.lineId) {
        val lineId = playing?.lineId ?: return@LaunchedEffect
        val sectionId = song.lines.firstOrNull { it.id == lineId }?.sectionId
        val id = sectionId ?: "root"
        if (id in collapsedIds) toggleSection(id)
        val key = "section-$id"
        val index = groups.indexOfFirst { sectionKey(it.first) == key }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == key }) listState.animateScrollToItem(index + 1)
    }

    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)) {
        item(key = "top") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SongHeader(song.song, onFavorite = { model.favorite(songId, !song.song.favorite) },
                    onShareChordPro = { model.exportChordPro(songId) { exportFile = it to false } },
                    onShareNative = { model.exportNative(songId) { exportFile = it to true } },
                    onMetronome = { openMetronome(song.song.bpm, song.song.timeNumerator, song.song.timeDenominator) })
                if (editing) {
                    OutlinedButton(onClick = { editMetadata = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = AppShape) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.song_edit_details))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstrumentTuningControl(instrumentId, tuningId, allowTheoryOnly = false,
                        onInstrument = { id -> chooseInstrument(id, id?.let { TuningCatalog.forInstrument(it).firstOrNull()?.id }) },
                        onTuning = { chooseInstrument(instrumentId, it) })
                }
                ToolTile(Icons.Default.SwapVert, stringResource(R.string.song_transpose),
                    listOfNotNull(song.song.keyRoot, song.song.transposeOffset.takeIf { it != 0 }?.let { "+$it" }).joinToString(" · ").ifEmpty { null }, { showTranspose = true }, Modifier.fillMaxWidth())
            }
        }
        groups.forEachIndexed { groupIndex, (section, lines) ->
            item(key = sectionKey(section)) {
                val id = section?.id ?: "root"
                val expanded = id !in collapsedIds
                val number = song.sections.sortedBy { it.position }.indexOfFirst { it.id == section?.id } + 1
                var menu by remember { mutableStateOf(false) }
                ExpandableCard(expanded = expanded, onToggle = { toggleSection(id) }, header = {
                    SectionBadge(number, section?.kind == "chorus", expanded)
                    Text(section?.title ?: stringResource(R.string.song_lyrics), Modifier.weight(1f).padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    if (editing && section != null) Box {
                        val optionsDescription = stringResource(R.string.song_section_options)
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = optionsDescription }) {
                            Icon(Icons.Default.MoreVert, null, tint = Muted)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.song_rename_section)) }, onClick = { renameSection = section; menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.song_edit_lyrics)) }, onClick = { editLyrics = section; showLyricsEditor = true; menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.song_reorder_up)) }, enabled = section.position > 0,
                                onClick = { model.reorderSection(songId, section.id, -1); menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.song_reorder_down)) }, enabled = groupIndex < groups.lastIndex,
                                onClick = { model.reorderSection(songId, section.id, 1); menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = Warning) }, onClick = { sectionToDelete = section; menu = false })
                        }
                    }
                }) {
                    lines.forEach { line ->
                        SongLineRow(line, song.chords[line.id].orEmpty(), editing, selectedChord, song.sections, naming, lineStyle, charWidth,
                            activeStep = playing?.takeIf { it.lineId == line.id }, cursorPosition = cursor?.takeIf { it.first == line.id }?.second, cursorSet = cursor != null,
                            onCursor = { position -> cursor = line.id to position }, onAdd = { position -> pendingChord = line.id to position },
                            onChord = { chord ->
                                if (editing) selectedChordId = if (selectedChordId == chord.id) null else chord.id
                                else if (SongMarks.parse(chord.originalSymbol) != SongMark.Rest) lookupChord = chord.originalSymbol
                            },
                            onMoveSelected = { selectedChord?.let { chord ->
                                cursor?.let { (lineId, position) -> model.editChord(songId, chord.id, chord.originalSymbol, position, chord.figure, lineId); selectedChordId = null }
                            } },
                            onEditSelected = { editChord = selectedChord },
                            onDeleteSelected = { selectedChord?.let { model.deleteChord(songId, it.id); selectedChordId = null } },
                            onDeselect = { selectedChordId = null },
                            onEditText = { value -> model.updateLineText(songId, line.id, value) },
                            onMoveLine = { target -> model.moveLine(songId, line.id, target) },
                            onReorder = { delta -> model.reorderLine(songId, line.id, delta) })
                    }
                    if (editing) TextButton(onClick = { editLyrics = section; showLyricsEditor = true }, Modifier.heightIn(min = 48.dp)) {
                        Text("+ " + stringResource(R.string.song_edit_lyrics))
                    }
                }
            }
        }
        if (editing) item(key = "add-section") {
            OutlinedButton(onClick = { addSection = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = AppShape,
                border = BorderStroke(1.dp, Neon)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.song_add_section))
            }
        }
        item(key = "recordings") {
            ExpandableCard(expanded = showRecordings, onToggle = { showRecordings = !showRecordings }, header = {
                Icon(Icons.Default.Mic, null, tint = NeonSoft)
                Text(stringResource(R.string.song_recordings), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
                CountBadge(recs.size)
            }) {
                if (recs.isEmpty()) Text(stringResource(R.string.song_no_recordings), color = Muted)
                recs.forEach { recording -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openRecording(recording.id) }, Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(recording.displayName, Modifier.weight(1f, fill = false), maxLines = 1)
                    }
                    if (editing) TextButton(onClick = { model.setRecordingSong(recording.id, null) }) { Text(stringResource(R.string.song_unlink_recording)) }
                } }
                TextButton(onClick = onStartRecording) { Text(stringResource(R.string.song_record_new)) }
                Box {
                    TextButton(onClick = { linkMenu = true }) { Text(stringResource(R.string.song_link_recording)) }
                    DropdownMenu(linkMenu, { linkMenu = false }) {
                        allRecordings.filter { it.songId == null && it.status == org.anarkey.core.recording.RecordingStatus.READY }.forEach { row ->
                            DropdownMenuItem(text = { Text(row.displayName) }, onClick = { model.setRecordingSong(row.id, songId); linkMenu = false })
                        }
                    }
                }
            }
        }
        item(key = "tags") {
            ExpandableCard(expanded = showTags, onToggle = { showTags = !showTags }, header = {
                Icon(Icons.Default.Sell, null, tint = NeonSoft)
                Text(stringResource(R.string.song_tags), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
                CountBadge(song.tags.size)
            }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    song.tags.forEach { tag ->
                        Row(Modifier.clip(CircleShape).background(Line).padding(start = 12.dp, end = if (editing) 4.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(tag.name, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                            if (editing) {
                                val removeDescription = stringResource(R.string.delete) + ": " + tag.name
                                IconButton(onClick = { model.removeTag(songId, tag.id) }, Modifier.size(40.dp).semantics { contentDescription = removeDescription }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Muted)
                                }
                            }
                        }
                    }
                    if (editing) TextButton(onClick = { addTag = true }) { Text("+ " + stringResource(R.string.song_add_tag)) }
                }
                if (song.tags.isEmpty() && !editing) Text(stringResource(R.string.song_tags_empty), color = Muted)
            }
        }
        if (editing) item(key = "footer") {
            OutlinedButton(onClick = { showDelete = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = AppShape,
                border = BorderStroke(1.dp, Warning)) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Warning); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.song_delete_title), color = Warning)
            }
        }
    }
    // Always within reach, also while the lyric scrolls along with the sounding chord.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTile(if (playing != null) Icons.Default.Stop else Icons.Default.PlayArrow,
            stringResource(if (playing != null) R.string.song_stop_chords else R.string.song_play_chords), null,
            { editing = false; selectedChordId = null; model.togglePlayback(song, a4Hz, instrumentId, tuningId) }, Modifier.weight(1f),
            enabled = hasChords, accent = true)
        if (editing) ToolTile(Icons.Default.MenuBook, stringResource(R.string.song_read), null,
            { editing = false; selectedChordId = null }, Modifier.weight(1f))
        else ToolTile(Icons.Default.Edit, stringResource(R.string.song_edit_song), null, { editing = true }, Modifier.weight(1f))
    }
    }
    if (showTranspose) SongTransposeDialog(song, naming, onDismiss = { showTranspose = false },
        onApply = { interval, flats, done -> model.transpose(songId, interval, flats) { showTranspose = false; done() } },
        onRestore = { flats, done -> model.restoreOriginalKey(songId, flats) { showTranspose = false; done() } })
    if (editMetadata) SongMetadataDialog(song.song, onDismiss = { editMetadata = false }, onSave = { args ->
        model.saveMetadata(songId, args.title, args.artist, args.keyRoot, args.keyMode, args.bpm, args.numerator, args.denominator,
            args.instrument, args.tuning, args.capo, args.notes); editMetadata = false
    })
    if (showLyricsEditor) LyricsDialog(song, editLyrics, onDismiss = { showLyricsEditor = false },
        onSave = { sectionId, text -> model.replaceLyrics(songId, sectionId, text); showLyricsEditor = false })
    renameSection?.let { section -> TextEntryDialog(stringResource(R.string.song_section_title), stringResource(R.string.song_section_title),
        { renameSection = null }, { model.renameSection(songId, section.id, it); renameSection = null }, initial = section.title) }
    sectionToDelete?.let { section -> AlertDialog(onDismissRequest = { sectionToDelete = null },
        title = { Text(stringResource(R.string.song_delete_section_title, section.title)) },
        text = { Text(stringResource(R.string.song_delete_section_body)) },
        confirmButton = { TextButton(onClick = { model.deleteSection(songId, section.id); sectionToDelete = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { sectionToDelete = null }) { Text(stringResource(android.R.string.cancel)) } }) }
    if (addSection) SectionDialog(onDismiss = { addSection = false }, onAdd = { title, kind -> model.createSection(songId, title, kind); addSection = false })
    if (addTag) TextEntryDialog(stringResource(R.string.song_add_tag), stringResource(R.string.song_tag_name), { addTag = false }, { model.addTag(songId, it); addTag = false })
    pendingChord?.let { (lineId, pos) -> MarkDialog(null, null, naming, { pendingChord = null }, { symbol, figure -> model.placeChord(songId, lineId, pos, symbol, figure); pendingChord = null }) }
    editChord?.let { chord -> MarkDialog(chord.originalSymbol, chord.figure, naming, { editChord = null }, { symbol, figure ->
        // Editing keeps the mark where it is; only "move here" changes its anchor.
        model.editChord(songId, chord.id, symbol, chord.position, figure, chord.lineId); editChord = null
    }) }
    lookupChord?.let { symbol ->
        val mark = SongMarks.parse(symbol)
        if (mark is SongMark.Note) NoteSheet(symbol, mark, naming, tuningId) { lookupChord = null }
        else ChordLookupSheetV42(symbol, chordModel, instrumentId, tuningId, song.song.capo,
            naming, chordMode, onInstrumentChange = chooseInstrument) { lookupChord = null }
    }
    exportFile?.let { (file, native) ->
        ExportDialog(file, native, onDismiss = { exportFile = null },
            onShare = { shareExport(context, file, song.song.title); exportFile = null })
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text(stringResource(R.string.song_delete_title)) },
        text = { Text(stringResource(R.string.song_delete_body)) },
        confirmButton = { TextButton(onClick = { model.delete(songId) { showDelete = false; onDeleted() } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(android.R.string.cancel)) } })
    error?.let { message -> AlertDialog(onDismissRequest = model::clearError, title = { Text(stringResource(R.string.song_edit_details)) },
        text = { Text(userError(message)) }, confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(android.R.string.ok)) } }) }
}

@Composable
private fun SongHeader(song: SongEntity, onFavorite: () -> Unit, onShareChordPro: () -> Unit, onShareNative: () -> Unit, onMetronome: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                song.artist?.let { Text(it, color = Muted, style = MaterialTheme.typography.titleMedium) }
            }
            IconButton(onClick = onFavorite) {
                Icon(if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.song_favorite), tint = NeonSoft)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                val toolsDescription = stringResource(R.string.song_more_tools)
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = toolsDescription }) {
                    Icon(Icons.Default.MoreVert, null, tint = Muted)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.metronome_title) + song.bpm?.let { " · " + stringResource(R.string.song_bpm_short, it) }.orEmpty()) },
                        leadingIcon = { Icon(Icons.Default.Timer, null, tint = NeonSoft) }, onClick = { menu = false; onMetronome() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.exchange_share_chordpro)) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = NeonSoft) }, onClick = { menu = false; onShareChordPro() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.exchange_share_native)) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = NeonSoft) }, onClick = { menu = false; onShareNative() })
                }
            }
        }
        val key = song.keyRoot?.let { root ->
            root + when (song.keyMode) { "minor" -> " " + stringResource(R.string.song_minor).lowercase(); "major" -> " " + stringResource(R.string.song_major).lowercase(); else -> "" }
        }
        val facts = listOfNotNull(
            key,
            song.bpm?.let { stringResource(R.string.song_bpm_short, it) + if (song.timeNumerator == 6 && song.timeDenominator == 8) " ♩." else "" },
            song.timeNumerator?.let { "$it/${song.timeDenominator}" },
            song.capo.takeIf { it > 0 }?.let { "Capo $it" },
        )
        if (facts.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { fact ->
                Text(fact, Modifier.clip(CircleShape).background(Panel).padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge, color = Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, accent: Boolean = false) {
    Row(modifier.heightIn(min = 60.dp).clip(AppShape).background(Panel).border(1.dp, if (accent && enabled) Neon else Line, AppShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (accent && enabled) Neon else Line), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (accent && enabled) Color.Black else if (enabled) NeonSoft else Muted)
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, color = if (enabled) MaterialTheme.colorScheme.onSurface else Muted)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
        }
    }
}

@Composable
private fun ExpandableCard(expanded: Boolean, onToggle: () -> Unit, header: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(AppShape).background(Panel)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Button, onClick = onToggle).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            header()
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(horizontal = 4.dp).rotate(if (expanded) 180f else 0f), tint = Muted)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SectionBadge(number: Int, star: Boolean, active: Boolean) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(if (active) Neon else Line), contentAlignment = Alignment.Center) {
        val tint = if (active) Color.Black else Muted
        when {
            star -> Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = tint)
            number > 0 -> Text("$number", color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            else -> Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp), tint = tint)
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text("$count", Modifier.clip(CircleShape).background(Line).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium, color = Muted)
}

@Composable
private fun SongLineRow(line: SongLineEntity, chords: List<ChordPlacementEntity>, editing: Boolean, selectedChord: ChordPlacementEntity?,
    sections: List<SongSectionEntity>, naming: NoteNaming, style: TextStyle, charWidth: Dp, activeStep: PlaybackStep?,
    cursorPosition: Int?, cursorSet: Boolean,
    onCursor: (Int) -> Unit, onAdd: (Int) -> Unit, onChord: (ChordPlacementEntity) -> Unit,
    onMoveSelected: () -> Unit, onEditSelected: () -> Unit, onDeleteSelected: () -> Unit, onDeselect: () -> Unit,
    onEditText: (String) -> Unit, onMoveLine: (String?) -> Unit, onReorder: (Int) -> Unit) {
    var field by remember(line.id) { mutableStateOf(TextFieldValue(line.text, TextRange(line.text.length))) }
    LaunchedEffect(line.text) { if (line.text != field.text) field = TextFieldValue(line.text, TextRange(line.text.length)) }
    // A new, empty line opens its text field straight away; for the rest, typing is one tap away.
    var textOpen by remember(line.id) { mutableStateOf(line.text.isEmpty()) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(activeStep?.chordId) { if (activeStep != null) requester.bringIntoView() }
    // In edit mode each line is its own block, so its buttons clearly belong to it and not to the next line.
    val block = if (editing) Modifier.clip(RoundedCornerShape(10.dp)).background(Ink).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(block).bringIntoViewRequester(requester)) {
        // The same wrapped layout reads and edits: chords stay exactly over their characters however long the line is.
        WrappedLine(line.text, chords, naming, style, NeonSoft, charWidth, activeStep?.chordId,
            if (editing) selectedChord?.id else null, activeStep, if (editing) cursorPosition else null, onChord,
            if (editing) onCursor else null)
        if (editing) {
            if (textOpen) BasicTextField(value = field, onValueChange = { updated ->
                field = updated; onCursor(updated.selection.start.codePointOffset(updated.text)); onEditText(updated.text)
            }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Panel).padding(10.dp),
                textStyle = style, cursorBrush = SolidColor(Neon),
                keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = false),
                decorationBox = { inner -> if (field.text.isEmpty()) Text("…", color = Muted, style = style); inner() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val textDescription = stringResource(R.string.song_edit_line_text)
                IconButton(onClick = { textOpen = !textOpen }, Modifier.semantics { contentDescription = textDescription }) {
                    Icon(if (textOpen) Icons.Default.Check else Icons.Default.Edit, null, tint = if (textOpen) Neon else Muted)
                }
                val addDescription = stringResource(R.string.song_add_chord)
                IconButton(onClick = {
                    val at = if (textOpen) field.selection.start.codePointOffset(field.text)
                        else cursorPosition ?: ChordAnchors.codePointCount(line.text)
                    onCursor(at); onAdd(at)
                }, Modifier.semantics { contentDescription = addDescription }) { Icon(Icons.Default.MusicNote, null, tint = Neon) }
                var menu by remember(line.id) { mutableStateOf(false) }
                Box {
                    val optionsDescription = stringResource(R.string.song_line_options)
                    IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = optionsDescription }) {
                        Icon(Icons.Default.MoreVert, null, tint = Muted)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_reorder_up)) }, onClick = { onReorder(-1); menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_reorder_down)) }, onClick = { onReorder(1); menu = false })
                        if (line.sectionId != null) DropdownMenuItem(text = { Text(stringResource(R.string.song_section_choose) + ": " + stringResource(R.string.song_lyrics)) },
                            onClick = { onMoveLine(null); menu = false })
                        sections.filter { it.id != line.sectionId }.forEach { section ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.song_section_choose) + ": " + section.title) },
                                onClick = { onMoveLine(section.id); menu = false })
                        }
                    }
                }
            }
            if (selectedChord != null && chords.any { it.id == selectedChord.id }) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(8.dp)) {
                Text(markLook(selectedChord.originalSymbol, selectedChord.figure, naming).text.ifEmpty { stringResource(R.string.mark_kind_rest) },
                    color = Neon, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = cursorSet, onClick = onMoveSelected) { Text(stringResource(R.string.song_chord_move_here)) }
                    TextButton(onClick = onEditSelected) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDeleteSelected) { Text(stringResource(R.string.delete), color = Warning) }
                    TextButton(onClick = onDeselect) { Text(stringResource(R.string.close)) }
                }
                Text(stringResource(R.string.song_chord_move_hint), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * A lyric laid out in monospace rows that wrap between words, each row with its own chords above it. Chords and
 * letters share one character grid, so they line up exactly. With [onTap] a tap on a letter places the cursor
 * there; [caret] marks it.
 */
@Composable
private fun WrappedLine(text: String, chords: List<ChordPlacementEntity>, naming: NoteNaming, style: TextStyle, chordColor: Color,
    charWidth: Dp, soundingId: String?, selectedId: String?, activeStep: PlaybackStep?, caret: Int?,
    onChord: (ChordPlacementEntity) -> Unit, onTap: ((Int) -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = (maxWidth / charWidth).toInt().coerceAtLeast(8)
        val rows = remember(text, perRow) { LyricWrap.rows(text, perRow) }
        val points = remember(text) { text.codePoints().toArray() }
        val charPx = with(LocalDensity.current) { charWidth.toPx() }
        Column {
            rows.forEachIndexed { index, row ->
                val last = index == rows.lastIndex
                val rowChords = chords.filter { it.position >= row.first && (it.position < row.last + 1 || last) }
                    .map { it.copy(position = (it.position - row.first).coerceAtLeast(0)) }
                if (rowChords.isNotEmpty()) PlacedChords(rowChords, naming, charWidth, style.copy(color = chordColor), soundingId, selectedId, onChord)
                val rowText = String(points, row.first, row.count())
                val lyric = remember(rowText, activeStep?.spanStart, activeStep?.spanEnd, caret) {
                    buildAnnotatedString {
                        append(rowText.ifEmpty { " " })
                        if (activeStep != null) {
                            // Spans are in code points of the whole line; the row string is indexed in UTF-16 units.
                            val from = maxOf(activeStep.spanStart, row.first) - row.first
                            val to = minOf(activeStep.spanEnd, row.last + 1) - row.first
                            if (to > from) addStyle(SpanStyle(background = Neon.copy(alpha = 0.22f), color = Neon, fontWeight = FontWeight.Bold),
                                rowText.offsetByCodePoints(0, from), rowText.offsetByCodePoints(0, to))
                        }
                        if (caret != null) {
                            val local = caret - row.first
                            val mark = SpanStyle(background = Neon.copy(alpha = 0.45f))
                            when {
                                local in 0 until row.count() -> addStyle(mark, rowText.offsetByCodePoints(0, local), rowText.offsetByCodePoints(0, local + 1))
                                last && local == row.count() && rowText.isNotEmpty() -> { append(" "); addStyle(mark, length - 1, length) }
                                rowText.isEmpty() && local == 0 -> addStyle(mark, 0, 1)
                            }
                        }
                    }
                }
                val tap = if (onTap == null) Modifier else Modifier.pointerInput(row, charPx, text) {
                    detectTapGestures { offset -> onTap(row.first + (offset.x / charPx).toInt().coerceIn(0, row.count())) }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = if (onTap != null) 36.dp else 0.dp).then(tap), contentAlignment = Alignment.CenterStart) {
                    Text(lyric, style = style, softWrap = false, modifier = Modifier.padding(bottom = if (last && onTap == null) 4.dp else 0.dp))
                }
            }
        }
    }
}

/** Chords laid out over the lyric: each starts at its character position times the monospace advance. */
@Composable
private fun PlacedChords(chords: List<ChordPlacementEntity>, naming: NoteNaming, charWidth: Dp, style: TextStyle,
    soundingId: String?, selectedId: String?, onChord: (ChordPlacementEntity) -> Unit) {
    var next = 0
    val placed = chords.sortedWith(compareBy<ChordPlacementEntity> { it.position }.thenBy { it.orderInPosition }).map { chord ->
        val look = markLook(chord.originalSymbol, chord.figure, naming)
        // Neighbours that would collide are pushed right, keeping one blank column between them.
        val start = maxOf(chord.position, if (next > 0) next + 1 else 0)
        next = start + look.glyphChars + look.text.length
        Triple(chord, look, start)
    }
    Box(Modifier.width(charWidth * next + 12.dp).heightIn(min = 34.dp)) {
        placed.forEach { (chord, look, start) ->
            val highlighted = chord.id == soundingId || chord.id == selectedId
            val tint = if (highlighted) Neon else style.color
            val figureText = look.duration?.let { figureName(it) }
            val description = when {
                look.rest -> stringResource(R.string.mark_kind_rest) + ", " + figureText
                SongMarks.parse(chord.originalSymbol) is SongMark.Chord && chord.rootLetter == null ->
                    "${chord.originalSymbol}. ${contextString(R.string.song_unknown_chord)}"
                figureText != null -> look.text + ", " + figureText
                else -> look.text
            }
            Box(Modifier.offset(x = charWidth * start - 6.dp).heightIn(min = 34.dp).clip(RoundedCornerShape(6.dp))
                .background(if (highlighted) Neon.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(role = Role.Button) { onChord(chord) }
                .padding(horizontal = 6.dp).semantics { contentDescription = description },
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    look.duration?.let { FigureGlyph(it, look.rest, tint, Modifier.size(width = charWidth * look.glyphChars, height = 26.dp)) }
                    if (look.text.isNotEmpty()) Text(look.text, style = style.copy(fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold, color = tint),
                        maxLines = 1, softWrap = false)
                }
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
    val instrument = song.instrumentId; val tuning = song.tuningId
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
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.song_notes)) }, minLines = 2, shape = FormFieldShape, colors = AnarkeyFormFieldColors())
        } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val parts = meter.split('/'); val numerator = parts.getOrNull(0)?.toIntOrNull(); val denominator = parts.getOrNull(1)?.toIntOrNull()
            onSave(MetadataArgs(title, artist.ifBlank { null }, key.ifBlank { null }?.let(::canonicalKey), mode.takeIf { key.isNotBlank() }?.ifBlank { null }, bpm.toIntOrNull(), numerator, denominator, instrument, tuning, capo.toIntOrNull() ?: 0, notes))
        }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable private fun TextEntryDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit, initial: String = "") {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, shape = FormFieldShape, colors = AnarkeyFormFieldColors()) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
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
