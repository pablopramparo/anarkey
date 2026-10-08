package org.anarkey.app.metronome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.anarkey.app.R
import org.anarkey.app.ui.*
import org.anarkey.core.metronome.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    model: MetronomeViewModel,
    songBpm: Int? = null,
    songNumerator: Int? = null,
    songDenominator: Int? = null,
    contextual: Boolean = false,
) {
    val savedPreferences by model.preferences.collectAsStateWithLifecycle()
    val loadedPreferences = savedPreferences
    val playback by model.playback.collectAsStateWithLifecycle()
    val meterFromSong = Meter.from(songNumerator, songDenominator)
    val contextKey = "$contextual:$songBpm:$songNumerator:$songDenominator"
    val initial = loadedPreferences?.withSongContext(songBpm, meterFromSong)
        ?: MetronomePreferences().withSongContext(songBpm, meterFromSong)
    var options by rememberSaveable(contextKey, stateSaver = metronomePreferencesSaver) { mutableStateOf(initial) }
    var edited by rememberSaveable(contextKey) { mutableStateOf(false) }
    var showSettings by rememberSaveable(contextKey) { mutableStateOf(false) }
    val tapTempo = remember(contextKey) { TapTempoCalculator() }
    val view = LocalView.current
    val context = LocalContext.current
    // On Android 13+ the notification of the foreground service stays hidden until this permission is granted.
    // The metronome plays either way, so the request never blocks starting it. A service that is already running
    // does not show its notification when the permission arrives later, so it is posted again at that point.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && model.playback.value.isPlaying) MetronomeService.start(context)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(loadedPreferences, contextKey) {
        if (!edited && loadedPreferences != null) options = loadedPreferences.withSongContext(songBpm, meterFromSong)
    }
    // Playback is kept alive by MetronomeService, so it keeps sounding when the screen turns off or the
    // app goes to the background; it only stops when leaving this screen or from the button/notification.
    DisposableEffect(model) {
        onDispose { model.stop() }
    }
    DisposableEffect(view, options.keepScreenOn, playback.isPlaying) {
        view.keepScreenOn = options.keepScreenOn && playback.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    fun changeOptions(updated: MetronomePreferences) {
        options = updated
        edited = true
        if (!contextual) model.savePreferences(updated)
        if (playback.isPlaying) model.update(updated.settings)
    }

    val settings = options.settings
    val pulsePattern = List(settings.meter.clicksPerBar) { settings.accentAt(it) }
    val unitDescription = stringResource(
        if (settings.meter.isDottedQuarterTempo) R.string.metronome_unit_dotted_quarter else R.string.metronome_unit_quarter,
    )
    val bpmNotation = stringResource(
        if (settings.meter.isDottedQuarterTempo) R.string.metronome_bpm_dotted_notation else R.string.metronome_bpm_quarter_notation,
        settings.bpm,
    )
    val contextualWarning = when {
        contextual && songBpm != null && songBpm !in MetronomeSettings.MIN_BPM..MetronomeSettings.MAX_BPM ->
            stringResource(R.string.metronome_song_bpm_clamped, settings.bpm)
        contextual && (songNumerator != null || songDenominator != null) && meterFromSong == null ->
            stringResource(R.string.metronome_song_meter_unsupported, songNumerator ?: 0, songDenominator ?: 0)
        else -> null
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.metronome_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (contextual) Text(stringResource(R.string.metronome_song_context), color = NeonSoft, style = MaterialTheme.typography.labelMedium)
        }
        contextualWarning?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { changeOptions(options.copy(settings = settings.copy(bpm = (settings.bpm - 1).coerceAtLeast(MetronomeSettings.MIN_BPM)))) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Icon(Icons.Default.Remove, stringResource(R.string.metronome_decrease_bpm), tint = NeonSoft) }
                    Text(
                        settings.bpm.toString(), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = NeonSoft,
                        modifier = Modifier.padding(horizontal = 14.dp), maxLines = 1,
                    )
                    IconButton(
                        onClick = { changeOptions(options.copy(settings = settings.copy(bpm = (settings.bpm + 1).coerceAtMost(MetronomeSettings.MAX_BPM)))) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Icon(Icons.Default.Add, stringResource(R.string.metronome_increase_bpm), tint = NeonSoft) }
                }
                Text(stringResource(R.string.metronome_bpm_label), Modifier.align(Alignment.CenterHorizontally), color = Muted, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.bpm.toFloat(),
                    onValueChange = { changeOptions(options.copy(settings = settings.copy(bpm = it.roundToInt().coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM)))) },
                    valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat(),
                    modifier = Modifier.height(36.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        shape = AppShape,
                        onClick = {
                            tapTempo.tap(SystemClock.elapsedRealtime())?.let { bpm ->
                                changeOptions(options.copy(settings = settings.copy(bpm = bpm)))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text(stringResource(R.string.metronome_tap_tempo), maxLines = 1) }
                    Text(
                        bpmNotation,
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).semantics { contentDescription = unitDescription },
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metronome_pulse_indicator), style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.metronome_pulse_edit_hint), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                pulsePattern.forEachIndexed { beat, accent ->
                    if (settings.meter == Meter.SIX_EIGHT && beat == 3) {
                        Spacer(Modifier.width(7.dp))
                        VerticalDivider(color = Line, modifier = Modifier.height(30.dp).padding(end = 4.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val nextAccent = accent.next()
                        val description = stringResource(
                            R.string.metronome_pulse_a11y,
                            beat + 1,
                            stringResource(accent.label()),
                            stringResource(nextAccent.label()),
                        )
                        PulseAccentButton(
                            index = beat,
                            accent = accent,
                            active = playback.isPlaying && playback.beatInBar == beat,
                            description = description,
                            onClick = {
                                val updatedPattern = pulsePattern.toMutableList().also { it[beat] = nextAccent }
                                changeOptions(options.copy(settings = settings.copy(
                                    accentPatterns = settings.accentPatterns + (settings.meter to updatedPattern),
                                )))
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metronome_meter_title), style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f))
                Text("${settings.meter.numerator}/${settings.meter.denominator}", color = NeonSoft, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Meter.entries.forEach { meter ->
                    FilterChip(
                        selected = settings.meter == meter,
                        onClick = { changeOptions(options.copy(settings = settings.copy(meter = meter))) },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        label = { Text("${meter.numerator}/${meter.denominator}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                    )
                }
            }
        }

        OutlinedButton(
            shape = AppShape,
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Default.Tune, null, tint = NeonSoft)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.metronome_options_button, stringResource(settings.sound.label())), maxLines = 1)
        }

        playback.error?.let { Text(stringResource(R.string.metronome_audio_error), color = Warning, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        Spacer(Modifier.weight(1f))
        Button(
            shape = AppShape,
            onClick = {
                if (playback.isPlaying) model.stop() else {
                    showSettings = false
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    model.start(settings)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (playback.isPlaying) R.string.metronome_stop else R.string.metronome_start), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Panel,
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.metronome_settings_sheet_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.metronome_sound_title), style = MaterialTheme.typography.titleSmall, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClickSound.entries.forEach { sound ->
                        FilterChip(
                            selected = settings.sound == sound,
                            onClick = { changeOptions(options.copy(settings = settings.copy(sound = sound))) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            label = { Text(stringResource(sound.label()), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.metronome_volume), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("${(settings.volume * 100).roundToInt()}%", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Slider(value = settings.volume, onValueChange = { value ->
                    changeOptions(options.copy(settings = settings.copy(volume = value.coerceIn(0f, 1f))))
                })
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { changeOptions(options.copy(keepScreenOn = !options.keepScreenOn)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(options.keepScreenOn, onCheckedChange = { changeOptions(options.copy(keepScreenOn = it)) })
                    Text(stringResource(R.string.metronome_keep_screen_on), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = Line)
                Text(stringResource(R.string.metronome_advanced_accents), style = MaterialTheme.typography.titleSmall, color = Muted)
                Text(stringResource(R.string.metronome_accent_legend), style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(
                    onClick = {
                        changeOptions(options.copy(settings = settings.copy(accentPatterns = settings.accentPatterns - settings.meter)))
                    },
                    modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp),
                ) { Text(stringResource(R.string.metronome_reset_meter_accents)) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PulseAccentButton(index: Int, accent: Accent, active: Boolean, description: String, onClick: () -> Unit) {
    val background = when (accent) {
        Accent.PRIMARY -> NeonSoft
        Accent.SECONDARY -> Panel
        Accent.NORMAL -> Panel
    }
    val borderColor = when {
        active -> Color.White
        accent == Accent.PRIMARY -> NeonSoft
        accent == Accent.SECONDARY -> NeonSoft.copy(alpha = 0.7f)
        else -> Line
    }
    // Playback is indicated only by the additional white outline. Keep the
    // configured accent's foreground/background pair unchanged for contrast.
    val textColor = if (accent == Accent.PRIMARY) Ink else if (accent == Accent.SECONDARY) NeonSoft else Muted
    Surface(
        color = background,
        shape = CircleShape,
        border = BorderStroke(if (active) 2.dp else if (accent == Accent.NORMAL) 1.dp else 2.dp, borderColor),
        modifier = Modifier.size(44.dp).semantics { contentDescription = description; role = Role.Button }.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                (index + 1).toString(),
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (accent != Accent.NORMAL || active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun Accent.next() = when (this) {
    Accent.NORMAL -> Accent.PRIMARY
    Accent.PRIMARY -> Accent.SECONDARY
    Accent.SECONDARY -> Accent.NORMAL
}

private fun Accent.label() = when (this) {
    Accent.NORMAL -> R.string.metronome_accent_normal_state
    Accent.PRIMARY -> R.string.metronome_accent_primary_state
    Accent.SECONDARY -> R.string.metronome_accent_secondary_state
}

private fun ClickSound.label() = when (this) {
    ClickSound.WOOD -> R.string.metronome_sound_wood
    ClickSound.DIGITAL -> R.string.metronome_sound_digital
    ClickSound.BELL -> R.string.metronome_sound_bell
}

private fun MetronomePreferences.withSongContext(songBpm: Int?, songMeter: Meter?) = copy(
    settings = settings.copy(
        bpm = songBpm?.coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM) ?: settings.bpm,
        meter = songMeter ?: settings.meter,
    ),
)

private val metronomePreferencesSaver = listSaver<MetronomePreferences, Any>(
    save = { value -> listOf(
        value.settings.bpm, value.settings.meter.name, value.settings.primaryAccent,
        value.settings.secondaryAccent, value.settings.sound.name, value.settings.volume, value.keepScreenOn,
        encodeAccentPatterns(value.settings.accentPatterns),
    ) },
    restore = { values ->
        MetronomePreferences(
            settings = MetronomeSettings(
                bpm = values[0] as Int,
                meter = Meter.valueOf(values[1] as String),
                primaryAccent = values[2] as Boolean,
                secondaryAccent = values[3] as Boolean,
                sound = ClickSound.valueOf(values[4] as String),
                volume = values[5] as Float,
                accentPatterns = decodeAccentPatterns(values.getOrNull(7) as? String ?: ""),
            ),
            keepScreenOn = values[6] as Boolean,
        )
    },
)

private fun encodeAccentPatterns(patterns: Map<Meter, List<Accent>>) = patterns.entries.joinToString(";") { (meter, pattern) ->
    "${meter.name}=${pattern.joinToString(",") { it.name }}"
}

private fun decodeAccentPatterns(encoded: String): Map<Meter, List<Accent>> = encoded.split(';').mapNotNull { item ->
    val parts = item.split('=', limit = 2)
    if (parts.size != 2) return@mapNotNull null
    val meter = runCatching { Meter.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
    val pattern = parts[1].split(',').mapNotNull { runCatching { Accent.valueOf(it) }.getOrNull() }
    if (pattern.size == meter.clicksPerBar) meter to pattern else null
}.toMap()
