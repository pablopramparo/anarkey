package org.anarkey.app.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.anarkey.app.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.anarkey.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Debug-only diagnostic history. It is deliberately omitted from release builds. */
@Composable
internal fun DetectionHistoryPanel(
    entries: List<DetectionEntry>, clear: () -> Unit, exportAudio: (String) -> Unit,
    exportedAudio: String?, canSaveCapture: Boolean, captureRunning: Boolean, names: Array<String>,
) {
    val pattern = stringResource(R.string.history_time_pattern)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    val zone = ZoneId.systemDefault()
    val border = Line
    val accent = Neon
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var captureName by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(AppShape).background(Panel)
            .border(1.dp, border, AppShape).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◷", color = NeonSoft, fontSize = 21.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.history_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = clear, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.history_clear), color = if (entries.isNotEmpty()) NeonSoft else Quiet)
            }
        }
        HorizontalDivider(color = border)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.audio_diagnostic_title), color = Muted, fontSize = 11.sp)
            TextButton(onClick = { captureName = ""; showSaveDialog = true }, enabled = canSaveCapture) {
                Text(stringResource(R.string.audio_diagnostic_save), color = accent)
            }
        }
        if (captureRunning) {
            Text(stringResource(R.string.audio_diagnostic_pause_to_save), color = Muted, fontSize = 10.sp)
        }
        exportedAudio?.let {
            Text(stringResource(R.string.audio_diagnostic_saved, it), color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 5.dp))
        }
        entries.take(6).forEachIndexed { index, entry ->
            val timestamp = formatter.format(Instant.ofEpochMilli(entry.timestampMs).atZone(zone))
            val note = stringResource(R.string.note_value, names[entry.note.pitchClass], entry.note.octave)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(timestamp, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1.05f), maxLines = 1)
                Text(note, color = NeonSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.58f), maxLines = 1)
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(stringResource(R.string.history_stable_short, entry.frequencyHz), color = Color(0xFFBEC5D0), fontSize = 10.sp, maxLines = 1)
                    Text(stringResource(R.string.history_raw_short, entry.rawFrequencyHz), color = Quiet, fontSize = 8.sp, maxLines = 1)
                }
                Text("%+.0f".format(entry.cents), color = Color(0xFFBEC5D0), fontSize = 10.sp,
                    modifier = Modifier.weight(0.55f), maxLines = 1)
                LinearProgressIndicator(
                    progress = { entry.clarity.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(0.75f).height(5.dp).clip(CircleShape),
                    color = accent,
                    trackColor = border,
                )
                Text("%.2f".format(entry.clarity), color = Muted, fontSize = 10.sp,
                    modifier = Modifier.weight(0.48f), maxLines = 1)
            }
            if (index < entries.take(6).lastIndex) HorizontalDivider(color = border.copy(alpha = 0.65f))
        }
    }
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.audio_diagnostic_name_title)) },
            text = {
                OutlinedTextField(
                    value = captureName,
                    onValueChange = { captureName = it.take(64) },
                    label = { Text(stringResource(R.string.audio_diagnostic_name_label)) },
                    placeholder = { Text(stringResource(R.string.audio_diagnostic_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportAudio(captureName.trim())
                        showSaveDialog = false
                    },
                    enabled = captureName.isNotBlank() && canSaveCapture,
                ) { Text(stringResource(R.string.audio_diagnostic_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
