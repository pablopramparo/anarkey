package org.anarkey.app

import android.Manifest
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.anarkey.app.navigation.AppShell
import org.anarkey.app.settings.SettingsViewModel
import org.anarkey.app.recording.RecorderScreen
import org.anarkey.app.recording.RecorderViewModel
import org.anarkey.app.songs.SongViewModel
import org.anarkey.app.chords.ChordFavoriteViewModel
import org.anarkey.app.metronome.MetronomeViewModel
import org.anarkey.app.recording.data.RecordingEntity
import org.anarkey.app.ui.AnarkeyTheme
import org.anarkey.app.ui.TunerScreen
import org.anarkey.core.music.*

class MainActivity : ComponentActivity() {
    private val tuner: TunerViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    private val recorder: RecorderViewModel by viewModels()
    private val songs: SongViewModel by viewModels()
    private val chordFavorites: ChordFavoriteViewModel by viewModels()
    private val metronome: MetronomeViewModel by viewModels()
    private val permission = MutableStateFlow(false)
    private val notificationDenied = mutableStateOf(false)
    private var pendingExport: RecordingEntity? = null
    private var pendingRecordingStart = false
    private var pendingSongId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
        val row = pendingExport; pendingExport = null
        if (uri != null && row != null) lifecycleScope.launch { runCatching { recorder.export(row, uri) } }
    }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingRecordingStart) startPendingRecording()
        else if (!granted) { pendingRecordingStart = false; pendingSongId = null; notificationDenied.value = true }
    }
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission.value = it
        if (!it) { tuner.permissionMissing(); pendingRecordingStart = false; pendingSongId = null }
        else if (pendingRecordingStart) startRecordingRequest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent {
            AnarkeyTheme {
                val settingsState by settings.state.collectAsStateWithLifecycle()
                val writeFailed by settings.writeFailed.collectAsStateWithLifecycle()
                val granted by permission.collectAsStateWithLifecycle()
                val appLanguage = remember { mutableStateOf(AppLanguage.current(this@MainActivity)) }
                val preferences = settingsState.preferences
                if (preferences == null) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (settingsState.failed) {
                                Text(stringResource(R.string.preferences_read_error))
                                TextButton(onClick = settings::retry) { Text(stringResource(R.string.retry)) }
                            } else {
                                Image(painterResource(R.drawable.brand_logo_slogan), stringResource(R.string.app_name),
                                    Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(3921f / 960f))
                                Spacer(Modifier.height(24.dp))
                                CircularProgressIndicator()
                            }
                        }
                    }
                } else {
                    AppShell(preferences, settings::setA4, settings::setNaming, settings::setChordPresentationMode,
                        appLanguage = appLanguage.value,
                        setAppLanguage = { language -> AppLanguage.set(this@MainActivity, language); recreate() },
                        recorder = { recordingId, onDismissInitial ->
                            RecorderScreen(recorder, { startRecordingRequest() },
                                onShare = ::shareRecording,
                                onExport = { row -> pendingExport = row; exportFile.launch(row.displayName + ".m4a") },
                                initialRecordingId = recordingId, onDismissInitialRecording = onDismissInitial)
                        },
                        songModel = songs,
                        chordModel = chordFavorites,
                        metronomeModel = metronome,
                        startSongRecording = { songId -> startRecordingRequest(songId) },
                        tuner = { context ->
                            TunerDestination(context, preferences, granted, tuner, settings::setSelection,
                                requestPermission = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                openPermissions = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) })
                        })
                }
                if (notificationDenied.value) AlertDialog(
                    onDismissRequest = { notificationDenied.value = false },
                    title = { Text(stringResource(R.string.notifications_needed_title)) },
                    text = { Text(stringResource(R.string.notifications_needed_text)) },
                    confirmButton = { TextButton(onClick = { notificationDenied.value = false; startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text(stringResource(R.string.open_settings)) } },
                    dismissButton = { TextButton(onClick = { notificationDenied.value = false }) { Text(stringResource(android.R.string.cancel)) } },
                )
                if (writeFailed) AlertDialog(
                    onDismissRequest = settings::clearWriteError,
                    title = { Text(stringResource(R.string.preferences_write_error)) },
                    confirmButton = { TextButton(onClick = settings::clearWriteError) { Text(stringResource(android.R.string.ok)) } },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permission.value = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecordingRequest(songId: String? = null) {
        if (songId != null) pendingSongId = songId
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecordingStart = true
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        pendingRecordingStart = true
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else { startPendingRecording() }
    }

    private fun startPendingRecording() {
        pendingRecordingStart = false
        val songId = pendingSongId
        pendingSongId = null
        recorder.start(songId)
    }

    private fun shareRecording(row: RecordingEntity) {
        lifecycleScope.launch {
            runCatching { recorder.shareUri(row) }.onSuccess { uri: Uri ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(row.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.share)))
            }
        }
    }
}

@Composable
private fun TunerDestination(
    context: TunerConfiguration?, preferences: TunerPreferences, granted: Boolean,
    model: TunerViewModel, setSelection: (TunerSelection) -> Unit,
    requestPermission: () -> Unit, openPermissions: () -> Unit,
) {
    // Context changes stay within this back-stack entry, never silently overwrite global defaults.
    var localInstrument by rememberSaveable(context) { mutableStateOf(context?.selection?.instrumentId) }
    var localTuning by rememberSaveable(context) { mutableStateOf(context?.selection?.tuningId) }
    val configuration = if (context == null) preferences.configuration
        else context.copy(selection = TunerSelection(localInstrument, localTuning))
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, granted, configuration) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (granted) model.foreground(configuration) else model.permissionMissing()
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val exportedAudio by model.exportedAudio.collectAsStateWithLifecycle()
    val hasRecentAudio by model.hasRecentAudio.collectAsStateWithLifecycle()
    val captureRunning by model.captureRunning.collectAsStateWithLifecycle()
    TunerScreen(
        configuration, preferences.noteNaming,
        onSelection = { selection ->
            if (context == null) setSelection(selection)
            else { localInstrument = selection.instrumentId; localTuning = selection.tuningId }
        },
        state = state, history = history, requestPermission = requestPermission, openSettings = openPermissions,
        toggleMicrophone = model::toggle, retry = model::retry, clearHistory = model::clearHistory,
        exportRecentAudio = model::saveRecentAudio, exportedAudio = exportedAudio,
        canSaveDiagnosticCapture = hasRecentAudio && !captureRunning, captureRunning = captureRunning,
        setTargetFrequency = model::setTargetFrequency,
    )
}
