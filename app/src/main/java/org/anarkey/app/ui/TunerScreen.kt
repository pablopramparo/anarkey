package org.anarkey.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import org.anarkey.app.diagnostics.DetectionEntry
import org.anarkey.app.diagnostics.DetectionHistoryPanel
import org.anarkey.core.music.Note
import org.anarkey.core.music.PitchMath
import org.anarkey.core.music.TuningDirection
import org.anarkey.core.music.TuningIndicator
import org.anarkey.core.tuner.SignalStatus
import kotlin.math.abs


import org.anarkey.app.BuildConfig
import org.anarkey.app.R
import org.anarkey.app.CaptureStatus
import org.anarkey.app.DebugState
import org.anarkey.core.music.*
@Composable
internal fun TunerScreen(
    configuration: TunerConfiguration,
    naming: NoteNaming,
    onSelection: (TunerSelection) -> Unit,
    state: DebugState,
    history: List<DetectionEntry>,
    requestPermission: () -> Unit,
    openSettings: () -> Unit,
    toggleMicrophone: () -> Unit,
    retry: () -> Unit,
    clearHistory: () -> Unit,
    exportRecentAudio: (String) -> Unit,
    exportedAudio: String?,
    canSaveDiagnosticCapture: Boolean,
    captureRunning: Boolean,
    setTargetFrequency: (Double?) -> Unit,
) {
    val tuning = configuration.selection.tuning
    var selectedStringIndex by rememberSaveable(configuration.selection) { mutableIntStateOf(-1) }
    val selectedTarget = tuning?.strings?.getOrNull(selectedStringIndex)?.let { Note(it.midi) }
    val selectedTargetFrequency = selectedTarget?.let { PitchMath.frequency(it, configuration.a4Hz) }
    SideEffect { setTargetFrequency(selectedTargetFrequency) }
    val tuner = state.frame?.tuner
    val noteNames = noteNames(naming)
    val stablePitch = tuner?.takeIf {
        it.status == SignalStatus.STABLE || it.status == SignalStatus.HOLDING
    }
    val frequency = stablePitch?.frequencyHz
    val targetFrequency = selectedTargetFrequency ?: stablePitch?.note?.let { PitchMath.frequency(it, configuration.a4Hz) }
    val cents = when {
        frequency != null && selectedTargetFrequency != null -> PitchMath.cents(frequency, selectedTargetFrequency)
        else -> stablePitch?.cents
    }
    val tuningIndicator = remember(configuration) { TuningIndicator() }
    val direction = tuningIndicator.update(cents)
    val note = (selectedTarget ?: tuner?.note)?.let {
        stringResource(R.string.note_value, noteNames[it.pitchClass], it.octave)
    } ?: stringResource(R.string.note_placeholder)
    val noteSize = when { note.length > 4 -> 30.sp; note.length > 2 -> 40.sp; else -> 56.sp }
    val autoTargetString = frequency?.let { tuning?.nearestStringIndex(it, configuration.a4Hz) }
    val statusRes = when (state.status) {
        CaptureStatus.PERMISSION -> R.string.permission_explanation
        CaptureStatus.STARTING -> R.string.starting
        CaptureStatus.PAUSED -> R.string.paused
        CaptureStatus.UNSUPPORTED -> R.string.unsupported
        CaptureStatus.UNAVAILABLE -> R.string.unavailable
        CaptureStatus.BUSY -> R.string.microphone_owned_by_recorder
        CaptureStatus.RUNNING -> when (tuner?.status) {
            // A held reading is still the note being tuned: keep its verdict through brief dropouts.
            SignalStatus.STABLE, SignalStatus.HOLDING -> when (direction) {
                TuningDirection.FLAT -> R.string.flat
                TuningDirection.SHARP -> R.string.sharp
                TuningDirection.IN_TUNE -> R.string.in_tune
                null -> R.string.listening
            }
            SignalStatus.WEAK -> R.string.weak
            SignalStatus.CLIPPING -> R.string.clipping
            else -> R.string.listening
        }
    }
    val statusColor = when {
        state.status == CaptureStatus.UNAVAILABLE || state.status == CaptureStatus.UNSUPPORTED -> Warning
        direction == TuningDirection.IN_TUNE -> Neon
        else -> Muted
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().background(Ink)) {
          Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reference_value, configuration.a4Hz), Modifier.weight(1f), color = Muted)
                val running = state.status == CaptureStatus.RUNNING
                FilledIconButton(
                    onClick = toggleMicrophone, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon, contentColor = Ink),
                ) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (running) R.string.pause_microphone else R.string.resume_microphone), modifier = Modifier.size(30.dp))
                }
            }
            if (state.status == CaptureStatus.PERMISSION) {
                Button(
                    onClick = requestPermission,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Ink),
                    shape = AppShape,
                ) { Text(stringResource(R.string.allow_microphone), fontWeight = FontWeight.Bold) }
                TextButton(onClick = openSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.open_settings), color = Muted)
                }
            }
            TuningSelectors(configuration.selection, noteNames, onSelection)
            Row(
                Modifier.fillMaxWidth().height(118.dp).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SideMetricCard(
                    modifier = Modifier.weight(1.1f),
                    label = stringResource(R.string.detected_frequency),
                    value = frequency?.let { stringResource(R.string.hz_value_short, it) }
                        ?: stringResource(R.string.frequency_placeholder),
                    progress = 0f,
                    accent = NeonSoft,
                    valueFontSize = 16.sp,
                )
                Column(
                    Modifier.weight(1.2f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        noteWithSmallSharp(note, noteSize),
                        color = White,
                        fontSize = noteSize,
                        lineHeight = 60.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                    targetFrequency?.let { targetHz ->
                        Text(
                            stringResource(R.string.target_frequency_short, targetHz),
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.height(14.dp),
                        )
                    } ?: Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(statusRes),
                        color = statusColor,
                        fontSize = 11.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(min = 20.dp),
                    )
                }
                SideMetricCard(
                    modifier = Modifier.weight(0.85f),
                    label = stringResource(R.string.cents_short),
                    value = cents?.let { "%+.0f".format(it) } ?: stringResource(R.string.no_value),
                    progress = 1f,
                    accent = statusColor,
                    showBar = false,
                )
            }

            TuningMeter(cents = cents?.toFloat(), modifier = Modifier.fillMaxWidth().height(128.dp))
            SpectrumStrip(levels = state.frame?.spectrumLevels ?: floatArrayOf())

            InputLevelCard(
                dbfs = tuner?.inputDbfs,
                active = state.status == CaptureStatus.RUNNING,
            )

            TuningStrings(
                strings = tuning?.strings.orEmpty(),
                selected = selectedStringIndex.takeIf { it >= 0 },
                detected = autoTargetString,
                noteNames = noteNames,
                onSelect = { index -> selectedStringIndex = if (selectedStringIndex == index) -1 else index },
            )

            if (state.status == CaptureStatus.UNSUPPORTED || state.status == CaptureStatus.UNAVAILABLE) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(statusRes), color = Warning, textAlign = TextAlign.Center)
                    TextButton(onClick = retry) { Text(stringResource(R.string.retry), color = Neon) }
                }
            }
            Text(
                stringResource(R.string.privacy_short),
                color = Quiet,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
            if (BuildConfig.DEBUG && state.status != CaptureStatus.PERMISSION) {
                DetectionHistoryPanel(history, clearHistory, exportRecentAudio, exportedAudio, canSaveDiagnosticCapture, captureRunning, noteNames)
            }
          }
        }
    }
}

@Composable
private fun SideMetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    progress: Float,
    accent: Color,
    showBar: Boolean = true,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
) {
    Column(
        modifier.heightIn(min = 76.dp).clip(AppShape).background(Panel).border(1.dp, Line, AppShape).padding(horizontal = 11.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = accent, fontSize = valueFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
        if (showBar) {
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = accent,
                trackColor = Line,
            )
        }
    }
}

// Fades the content out towards the left and right edges, so the dial reads as a window onto a wider scale.
private fun Modifier.fadeSides(fraction: Float = 0.15f): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, fraction to Color.Black, 1f - fraction to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun TuningMeter(cents: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.fadeSides()) {
        val centerX = size.width / 2f
        // A shallow slice of a large circle whose center sits far below the screen. The scale keeps
        // going past +/-50 cents and is masked out at the sides instead of ending abruptly.
        val radius = size.width * 0.85f
        val centerY = radius + 18.dp.toPx()
        val degreesPerCent = 0.5
        fun angleAt(centsValue: Float) = Math.toRadians(270.0 + centsValue * degreesPerCent)
        for (tick in -40..40) {
            val centsAtTick = tick * 2f
            val angle = angleAt(centsAtTick)
            val major = tick % 5 == 0
            val inner = radius - if (major) 20.dp.toPx() else 11.dp.toPx()
            val outer = radius - 1.dp.toPx()
            val x0 = centerX + (kotlin.math.cos(angle) * inner).toFloat()
            val y0 = centerY + (kotlin.math.sin(angle) * inner).toFloat()
            val x1 = centerX + (kotlin.math.cos(angle) * outer).toFloat()
            val y1 = centerY + (kotlin.math.sin(angle) * outer).toFloat()
            val distance = abs(centsAtTick)
            val color = if (distance <= 22f) {
                val t = distance / 22f
                lerp(NeonSoft, Neon, t).copy(alpha = 1f - 0.6f * t)
            } else (if (major) Muted else Quiet).copy(alpha = 0.8f)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x0, y0),
                end = androidx.compose.ui.geometry.Offset(x1, y1),
                strokeWidth = if (major) 2.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val native = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(176, 170, 164)
            textSize = 13.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        listOf(-50, -20, 20, 50).forEach { label ->
            val angle = angleAt(label.toFloat())
            val labelRadius = radius - 40.dp.toPx()
            native.drawText(
                if (label > 0) "+$label" else label.toString(),
                centerX + (kotlin.math.cos(angle) * labelRadius).toFloat(),
                centerY + (kotlin.math.sin(angle) * labelRadius).toFloat() + 5.dp.toPx(),
                paint,
            )
        }
        if (cents != null) {
            val limited = cents.coerceIn(-50f, 50f)
            val angle = angleAt(limited)
            val unitX = kotlin.math.cos(angle).toFloat()
            val unitY = kotlin.math.sin(angle).toFloat()
            val innerStart = radius - 4.dp.toPx()
            val innerEnd = radius - 96.dp.toPx()
            val start = androidx.compose.ui.geometry.Offset(centerX + unitX * innerStart, centerY + unitY * innerStart)
            val end = androidx.compose.ui.geometry.Offset(centerX + unitX * innerEnd, centerY + unitY * innerEnd)
            drawLine(
                Brush.linearGradient(listOf(Neon.copy(alpha = 0.3f), Color.Transparent), start, end),
                start, end, 9.dp.toPx(), StrokeCap.Round,
            )
            drawLine(
                Brush.linearGradient(listOf(NeonSoft, Neon, Neon.copy(alpha = 0f)), start, end),
                start, end, 3.dp.toPx(), StrokeCap.Round,
            )
            val tip = androidx.compose.ui.geometry.Offset(centerX + unitX * (radius - 1.dp.toPx()), centerY + unitY * (radius - 1.dp.toPx()))
            val baseX = centerX + unitX * (radius + 13.dp.toPx())
            val baseY = centerY + unitY * (radius + 13.dp.toPx())
            val perpendicularX = -unitY * 7.dp.toPx()
            val perpendicularY = unitX * 7.dp.toPx()
            val pointer = androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(baseX + perpendicularX, baseY + perpendicularY)
                lineTo(baseX - perpendicularX, baseY - perpendicularY)
                close()
            }
            drawPath(pointer, NeonSoft)
        }
    }
}

@Composable
private fun SpectrumStrip(levels: FloatArray) {
    Canvas(Modifier.fillMaxWidth().height(44.dp).fadeSides(0.3f)) {
        if (levels.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (levels.size - 1)) / levels.size
        levels.forEachIndexed { index, level ->
            val strength = level.coerceIn(0f, 1f)
            val height = 3.dp.toPx() + strength * (size.height - 3.dp.toPx())
            val alpha = 0.3f + strength * 0.7f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(NeonSoft.copy(alpha = alpha), Neon.copy(alpha = alpha * 0.85f), Neon.copy(alpha = alpha * 0.2f)),
                    startY = size.height - height, endY = size.height,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

@Composable
private fun InputLevelCard(dbfs: Double?, active: Boolean) {
    val shown = dbfs?.takeIf { active }?.let { stringResource(R.string.dbfs_value, it) }
        ?: stringResource(R.string.no_value)
    val progress = dbfs?.let { ((it + 60.0) / 60.0).toFloat().coerceIn(0f, 1f) } ?: 0f
    Row(
        Modifier.fillMaxWidth().clip(AppShape).background(Panel).border(1.dp, Line, AppShape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(0.85f)) {
            Text(stringResource(R.string.input_level_label), color = Muted, fontSize = 12.sp)
            Text(shown, color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1.5f).height(8.dp).clip(CircleShape),
            color = Neon,
            trackColor = Line,
        )
    }
}

@Composable
private fun TuningStrings(strings: List<OpenString>, selected: Int?, detected: Int?, noteNames: Array<String>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        strings.forEachIndexed { index, string ->
            val note = Note(string.midi)
            val isSelected = selected == index
            val isDetected = detected == index
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(index) }
                    .clip(AppShape)
                    .background(if (isSelected) Color(0xFF38220F) else Panel)
                    .border(
                        1.dp,
                        when {
                            isSelected -> Neon
                            isDetected -> NeonSoft.copy(alpha = 0.65f)
                            else -> Line
                        },
                        AppShape,
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    noteWithSmallSharp(
                        stringResource(R.string.note_value, noteNames[note.pitchClass], note.octave),
                        15.sp,
                    ),
                    color = if (isSelected) Neon else White,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.string_number, string.number),
                    color = if (isSelected) NeonSoft else Muted,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

private fun noteWithSmallSharp(value: String, fontSize: androidx.compose.ui.unit.TextUnit): AnnotatedString =
    buildAnnotatedString {
        value.forEach { character ->
            if (character == '♯' || character == '#' || character == '♭') {
                pushStyle(
                    SpanStyle(
                        fontSize = fontSize * 0.68f,
                        baselineShift = BaselineShift(0.2f),
                    ),
                )
                append(character)
                pop()
            } else {
                append(character)
            }
        }
    }
