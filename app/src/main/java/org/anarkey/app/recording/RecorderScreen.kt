package org.anarkey.app.recording

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.anarkey.app.R
import org.anarkey.app.ui.AppShape
import org.anarkey.app.recording.data.RecordingEntity
import org.anarkey.app.recording.data.RecordingMarkerEntity
import org.anarkey.app.recording.data.SessionEntity
import org.anarkey.app.ui.Line
import org.anarkey.app.ui.NeonSoft
import org.anarkey.app.ui.Panel
import org.anarkey.app.ui.SelectorButton
import org.anarkey.app.ui.AnarkeyFormFieldColors
import org.anarkey.app.ui.FormFieldShape
import org.anarkey.app.ui.Warning
import org.anarkey.core.recording.RecordingStatus
import org.anarkey.core.recording.WaveformCodec

@Composable
fun RecorderScreen(model: RecorderViewModel, requestPermission: () -> Unit,
    onShare: (RecordingEntity) -> Unit, onExport: (RecordingEntity) -> Unit, initialRecordingId: String? = null,
    onDismissInitialRecording: () -> Unit = {}) {
    val recordings by model.recordings.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val runtime by model.runtime.collectAsStateWithLifecycle()
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var selectedRecording by remember { mutableStateOf<RecordingEntity?>(null) }
    var createSession by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteRecording by remember { mutableStateOf<RecordingEntity?>(null) }
    var openedInitialRecording by remember(initialRecordingId) { mutableStateOf(false) }
    val largeType = LocalDensity.current.fontScale >= 1.25f
    val configuration = LocalConfiguration.current
    val compactLayout = largeType || configuration.screenWidthDp < 330 || configuration.screenHeightDp < 600
    val shortViewport = configuration.screenHeightDp < 480
    LaunchedEffect(initialRecordingId, recordings) {
        if (!openedInitialRecording && initialRecordingId != null) recordings.firstOrNull { it.id == initialRecordingId }?.let {
            selectedRecording = it; openedInitialRecording = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)
        .then(if (compactLayout && runtime.phase in listOf(RecorderPhase.IDLE, RecorderPhase.RECORDING, RecorderPhase.PAUSED))
            Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
        when (runtime.phase) {
            RecorderPhase.IDLE -> {
                Column(Modifier.fillMaxWidth().padding(top = if (shortViewport) 8.dp else 20.dp,
                    bottom = if (shortViewport) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = requestPermission, modifier = Modifier.size(if (shortViewport) 72.dp else 80.dp), shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.FiberManualRecord, stringResource(R.string.start_recording), modifier = Modifier.size(30.dp))
                    }
                    Text(stringResource(R.string.start_recording), color = NeonSoft,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = largeType || maxWidth < 330.dp
                    @Composable fun viewChip(sessionsView: Boolean) {
                        FilterChip(selectedSession.let { if (sessionsView) it == "__sessions__" else it != "__sessions__" },
                            { selectedSession = if (sessionsView) "__sessions__" else if (selectedSession == "__sessions__") null else selectedSession },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            label = { Text(stringResource(if (sessionsView) R.string.sessions_title else R.string.recordings_title), maxLines = 1) })
                    }
                    if (compact) Column { viewChip(false); viewChip(true) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { viewChip(false); viewChip(true) }
                }
                if (selectedSession == "__sessions__") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { createSession = true }) { Text(stringResource(R.string.session_new)) }
                    }
                } else {
                    var filterMenu by remember { mutableStateOf(false) }
                    val filterName = when (selectedSession) {
                        null -> stringResource(if (compactLayout) R.string.recording_filter_all_short else R.string.recording_filter_all)
                        "__none__" -> stringResource(R.string.no_session)
                        else -> sessions.firstOrNull { it.id == selectedSession }?.name ?: stringResource(R.string.recording_filter_all)
                    }
                    Box {
                        SelectorButton(filterName, onClick = { filterMenu = true })
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.recording_filter_all)) },
                                onClick = { selectedSession = null; filterMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.no_session)) },
                                onClick = { selectedSession = "__none__"; filterMenu = false })
                            sessions.forEach { session -> DropdownMenuItem(text = { Text(session.name) },
                                onClick = { selectedSession = session.id; filterMenu = false }) }
                        }
                    }
                }
            }
            RecorderPhase.RECORDING, RecorderPhase.PAUSED -> RecordingControls(runtime, model)
            RecorderPhase.STARTING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            RecorderPhase.FINALIZING -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.recording_saving), modifier = Modifier.align(Alignment.CenterHorizontally))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
            RecorderPhase.ERROR -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(runtime.message ?: stringResource(R.string.recording_error), color = Warning)
                Button(onClick = model::stop, shape = AppShape) { Text(stringResource(R.string.stop_recording)) }
            }
        }

        if (runtime.phase == RecorderPhase.IDLE && selectedSession == "__sessions__") {
            if (sessions.isEmpty()) EmptyRecorderMessage(stringResource(R.string.session_empty))
            @Composable fun sessionCard(session: SessionEntity) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { selectedSession = session.id }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(session.name, style = MaterialTheme.typography.titleMedium)
                                if (session.notes.isNotBlank()) Text(session.notes, style = MaterialTheme.typography.bodySmall)
                                Text("${recordings.count { it.sessionId == session.id }} · ${stringResource(R.string.recordings_title)}", color = NeonSoft)
                            }
                            TextButton(onClick = { editingSession = session }) { Text(stringResource(R.string.edit)) }
                        }
                    }
            }
            if (compactLayout) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                sessions.forEach { sessionCard(it) }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(sessions, key = { it.id }) { sessionCard(it) }
            }
        } else if (runtime.phase == RecorderPhase.IDLE || runtime.phase == RecorderPhase.ERROR) {
            val shown = recordings.filter {
                when (selectedSession) {
                    null -> true
                    "__none__" -> it.sessionId == null
                    "__sessions__" -> false
                    else -> it.sessionId == selectedSession
                }
            }
            if (shown.isEmpty()) EmptyRecorderMessage(stringResource(if (selectedSession == null) R.string.recording_empty else R.string.recording_filter_empty))
            @Composable fun recordingCard(recording: RecordingEntity) = RecordingCard(recording,
                onOpen = { selectedRecording = recording }, onDelete = { deleteRecording = recording })
            if (compactLayout) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                shown.forEach { recordingCard(it) }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(shown, key = { it.id }) { recordingCard(it) }
            }
        }
    }

    if (createSession) SessionEditor(null, { name, notes -> model.createSession(name, notes); createSession = false }, { createSession = false })
    editingSession?.let { session -> SessionEditor(session,
        { name, notes -> model.saveSession(session.id, name, notes); editingSession = null },
        { editingSession = null }, onDelete = { model.deleteSession(session.id); editingSession = null }) }
    selectedRecording?.let { row -> RecordingDetail(row, sessions, model,
        onDismiss = { selectedRecording = null; if (initialRecordingId != null) onDismissInitialRecording() }, onShare = { onShare(row) }, onExport = { onExport(row) },
        onDelete = { selectedRecording = null; deleteRecording = row }) }
    deleteRecording?.let { row ->
        AlertDialog(onDismissRequest = { deleteRecording = null }, title = { Text(stringResource(R.string.delete_recording_title)) },
            text = { Text(row.displayName) }, confirmButton = { TextButton(onClick = { model.delete(row); deleteRecording = null; if (initialRecordingId != null) onDismissInitialRecording() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteRecording = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable private fun EmptyRecorderMessage(message: String) {
    Text(message, color = NeonSoft, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp))
}

@Composable private fun RecordingControls(runtime: RecorderRuntime, model: RecorderViewModel) {
    val paused = runtime.phase == RecorderPhase.PAUSED
    var markerCount by remember(runtime.recordingId) { mutableIntStateOf(0) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    val largeType = LocalDensity.current.fontScale >= 1.25f
    val configuration = LocalConfiguration.current
    val compact = largeType || configuration.screenWidthDp < 330 || configuration.screenHeightDp < 600
    LaunchedEffect(runtime.recordingId) {
        runtime.recordingId?.let { id -> model.observeMarkers(id).collect { markerCount = it.size } }
    }
    Column((if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).padding(bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.recording_more)) }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.cancel_recording)) },
                    onClick = { moreMenu = false; confirmRecovery = true })
            }
        }
        Column(if (compact) Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp) else Modifier.weight(1f),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatTime(runtime.durationMs), fontSize = 44.sp, fontWeight = FontWeight.Medium, color = NeonSoft)
            Text(stringResource(if (paused) R.string.recording_state_paused else R.string.recording_state_active),
                color = NeonSoft, style = MaterialTheme.typography.titleMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = compact || maxWidth < 340.dp
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecorderActionButton(paused, { if (paused) model.resume() else model.pause() }, Modifier.fillMaxWidth())
                MarkerButton(markerCount, model::mark, Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecorderActionButton(paused, { if (paused) model.resume() else model.pause() }, Modifier.weight(1f))
                MarkerButton(markerCount, model::mark, Modifier.weight(1f))
            }
        }
        Button(onClick = model::stop, shape = AppShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save_recording), textAlign = TextAlign.Center)
        }
    }
    if (confirmRecovery) AlertDialog(onDismissRequest = { confirmRecovery = false },
        title = { Text(stringResource(R.string.cancel_recording)) },
        text = { Text(stringResource(R.string.recording_recovery_explanation)) },
        confirmButton = { TextButton(onClick = { confirmRecovery = false; model.cancel() }) { Text(stringResource(R.string.cancel_recording)) } },
        dismissButton = { TextButton(onClick = { confirmRecovery = false }) { Text(stringResource(R.string.continue_recording)) } })
}

@Composable private fun RecorderActionButton(paused: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, shape = AppShape, modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (paused) R.string.resume_recording else R.string.pause_recording), maxLines = 1)
    }
}

@Composable private fun MarkerButton(count: Int, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, shape = AppShape, modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(Icons.Default.Star, null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.marker_button), maxLines = 1)
        if (count > 0) Text(" · $count", maxLines = 1)
    }
}

@Composable private fun RecordingCard(row: RecordingEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                val status = when (row.status) {
                    RecordingStatus.READY -> null
                    RecordingStatus.RECORDING -> R.string.recording_status_recording
                    RecordingStatus.PAUSED -> R.string.recording_status_paused
                    RecordingStatus.FINALIZING -> R.string.recording_status_finalizing
                    RecordingStatus.RECOVERY_REQUIRED -> R.string.recording_status_recovery
                    RecordingStatus.MISSING -> R.string.recording_status_missing
                    RecordingStatus.DELETING -> R.string.recording_status_deleting
                }
                Text(listOfNotNull(formatTime(row.durationMs), status?.let { stringResource(it) }).joinToString(" · "),
                    color = if (status == null) NeonSoft else Warning)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = NeonSoft) }
        }
    }
}

@Composable private fun SessionEditor(session: SessionEntity?, onSave: (String, String) -> Unit, onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null) {
    var name by remember(session) { mutableStateOf(session?.name.orEmpty()) }
    var notes by remember(session) { mutableStateOf(session?.notes.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(session?.name ?: stringResource(R.string.session_new)) },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.session_name)) }, singleLine = true,
                shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2,
                shape = FormFieldShape, colors = AnarkeyFormFieldColors())
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, notes) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row {
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        } })
}

@Composable private fun RecordingDetail(row: RecordingEntity, sessions: List<SessionEntity>, model: RecorderViewModel,
    onDismiss: () -> Unit, onShare: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var name by remember(row.id) { mutableStateOf(row.displayName) }
    var notes by remember(row.id) { mutableStateOf(row.notes) }
    var sessionId by remember(row.id) { mutableStateOf(row.sessionId) }
    var sessionMenu by remember { mutableStateOf(false) }
    var editingDetails by remember(row.id) { mutableStateOf(false) }
    var newMarkerAt by remember(row.id) { mutableStateOf<Long?>(null) }
    var newMarkerNote by remember(row.id) { mutableStateOf("") }
    var markers by remember { mutableStateOf(emptyList<RecordingMarkerEntity>()) }
    val detailScroll = rememberScrollState()
    LaunchedEffect(editingDetails) { detailScroll.scrollTo(0) }
    LaunchedEffect(row.id) { model.observeMarkers(row.id).collect { markers = it } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(row.id) {
        if (row.status == RecordingStatus.READY) runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(model.playbackFile(row))))
                prepare()
            }
        }.getOrNull() else null
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (player != null) { isPlaying = player.isPlaying; position = player.currentPosition.coerceAtLeast(0); delay(250) }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (editingDetails) stringResource(R.string.recording_edit_details) else row.displayName) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(detailScroll)) {
                if (editingDetails) {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.name)) }, singleLine = true,
                        shape = FormFieldShape, colors = AnarkeyFormFieldColors())
                    OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.notes)) }, minLines = 2,
                        shape = FormFieldShape, colors = AnarkeyFormFieldColors())
                    Box {
                        SelectorButton(sessions.firstOrNull { it.id == sessionId }?.name ?: stringResource(R.string.no_session),
                            onClick = { sessionMenu = true })
                        DropdownMenu(expanded = sessionMenu, onDismissRequest = { sessionMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.no_session)) }, onClick = { sessionId = null; sessionMenu = false })
                            sessions.forEach { session -> DropdownMenuItem(text = { Text(session.name) }, onClick = { sessionId = session.id; sessionMenu = false }) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingDetails = true }) { Text(stringResource(R.string.edit)) }
                    }
                    if (player != null) {
                        Waveform(row.waveformPeaks, position, row.durationMs, Modifier.fillMaxWidth().height(54.dp))
                        Slider(value = position.toFloat().coerceIn(0f, row.durationMs.coerceAtLeast(1).toFloat()),
                            onValueChange = { player.seekTo(it.toLong()) }, valueRange = 0f..row.durationMs.coerceAtLeast(1).toFloat())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            }
                            IconButton(onClick = { player.pause(); player.seekTo(0); position = 0 }) { Icon(Icons.Default.Stop, null) }
                            Text("${formatTime(position)} / ${formatTime(row.durationMs)}")
                        }
                    } else Text(stringResource(R.string.recording_recovery_export), color = Warning)
                    if (markers.isNotEmpty() || player != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.markers_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (player != null) TextButton(onClick = {
                                player.pause()
                                newMarkerAt = player.currentPosition.coerceIn(0L, row.durationMs)
                            }) {
                                Icon(Icons.Default.Add, null)
                                Text(stringResource(R.string.marker_add_short))
                            }
                        }
                    }
                    markers.forEach { marker -> MarkerRow(marker, onSeek = { player?.seekTo(marker.positionMs) },
                        onSave = { model.editMarker(marker.id, row.id, it) }, onDelete = { model.deleteMarker(marker.id, row.id) }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onShare, enabled = row.status == RecordingStatus.READY) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                            Text(stringResource(R.string.share), fontSize = 11.sp)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = onExport, enabled = row.status == RecordingStatus.READY || row.status == RecordingStatus.RECOVERY_REQUIRED) {
                                Text(stringResource(R.string.export), fontSize = 11.sp)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            Text(stringResource(R.string.delete), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (editingDetails) TextButton(enabled = name.isNotBlank(), onClick = {
                model.saveDetails(row, name, notes, sessionId); onDismiss()
            }) { Text(stringResource(R.string.save)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            if (editingDetails) TextButton(onClick = {
                name = row.displayName; notes = row.notes; sessionId = row.sessionId; editingDetails = false
            }) { Text(stringResource(android.R.string.cancel)) }
        })
    newMarkerAt?.let { at ->
        AlertDialog(onDismissRequest = { newMarkerAt = null; newMarkerNote = "" },
            title = { Text(stringResource(R.string.marker_new)) },
            text = { Column {
                Text(stringResource(R.string.marker_position, formatTime(at)))
                OutlinedTextField(newMarkerNote, { newMarkerNote = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.marker_note)) }, singleLine = true,
                    shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            } },
            confirmButton = { TextButton(onClick = {
                model.addMarkerAfterRecording(row.id, at, newMarkerNote)
                newMarkerAt = null; newMarkerNote = ""
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { newMarkerAt = null; newMarkerNote = "" }) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}

@Composable private fun MarkerRow(marker: RecordingMarkerEntity, onSeek: () -> Unit, onSave: (String?) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(marker.text) { mutableStateOf(marker.text.orEmpty()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (expanded) {
            Text(formatTime(marker.positionMs), color = NeonSoft, modifier = Modifier.clickable(onClick = onSeek))
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true, placeholder = { Text(stringResource(R.string.marker_note)) },
                shape = FormFieldShape, colors = AnarkeyFormFieldColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { text = marker.text.orEmpty(); expanded = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onSave(text); expanded = false }) { Text(stringResource(R.string.save)) }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(marker.positionMs), color = NeonSoft, modifier = Modifier.clickable(onClick = onSeek))
            Text(marker.text ?: "★", Modifier.weight(1f).clickable { expanded = true }.padding(start = 8.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable private fun Waveform(peaks: ByteArray?, position: Long, duration: Long, modifier: Modifier = Modifier) {
    val values = remember(peaks) { WaveformCodec.decode(peaks) }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val middle = size.height / 2
        val step = size.width / values.size
        values.forEachIndexed { index, peak ->
            val x = index * step + step / 2
            val amp = (peak * size.height * 0.46f).coerceAtLeast(1f)
            val fraction = if (duration <= 0) 0f else position.toFloat() / duration
            drawLine(if (index * step / size.width <= fraction) NeonSoft else Line,
                androidx.compose.ui.geometry.Offset(x, middle - amp), androidx.compose.ui.geometry.Offset(x, middle + amp), step.coerceAtLeast(1f))
        }
    }
}

private fun formatTime(valueMs: Long): String {
    val seconds = (valueMs.coerceAtLeast(0) / 1_000)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
