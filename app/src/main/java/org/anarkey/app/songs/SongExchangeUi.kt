package org.anarkey.app.songs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.anarkey.app.R
import org.anarkey.app.ui.*
import org.anarkey.core.exchange.ExchangeRules
import org.anarkey.core.exchange.ImportedSong
import org.anarkey.core.exchange.Notice
import java.io.File

/** One readable sentence for each thing an import or export has to report. */
@Composable
internal fun noticeText(notice: Notice): String {
    val detail = notice.detail.orEmpty()
    return when (notice.code) {
        "untitled" -> stringResource(R.string.notice_untitled)
        "key_invalid" -> stringResource(R.string.notice_key_invalid, detail)
        "key_mode_invalid" -> stringResource(R.string.notice_key_mode_invalid, detail)
        "bpm_invalid" -> stringResource(R.string.notice_bpm_invalid, detail)
        "meter_invalid" -> stringResource(R.string.notice_meter_invalid, detail)
        "instrument_invalid" -> stringResource(R.string.notice_instrument_invalid, detail)
        "capo_invalid" -> stringResource(R.string.notice_capo_invalid, detail)
        "figure_invalid" -> stringResource(R.string.notice_figure_invalid, detail)
        "lines_truncated" -> stringResource(R.string.notice_lines_truncated, detail)
        "sections_truncated" -> stringResource(R.string.notice_sections_truncated, detail)
        "unknown_directive" -> stringResource(R.string.notice_unknown_directive, detail)
        "formatting_ignored" -> stringResource(R.string.notice_formatting_ignored, detail)
        "annotations_dropped" -> stringResource(R.string.notice_annotations_dropped, detail)
        "metadata_in_notes" -> stringResource(R.string.notice_metadata_in_notes)
        "chorus_recall_without_chorus" -> stringResource(R.string.notice_chorus_recall)
        "tab_as_text" -> stringResource(R.string.notice_tab_as_text)
        "song_untitled_index" -> stringResource(R.string.notice_song_untitled, detail)
        "notes_as_annotations" -> stringResource(R.string.notice_notes_as_annotations, detail)
        "rests_as_annotations" -> stringResource(R.string.notice_rests_as_annotations, detail)
        "chord_durations_lost" -> stringResource(R.string.notice_chord_durations_lost, detail)
        "anarkey_extensions" -> stringResource(R.string.notice_anarkey_extensions)
        else -> notice.code
    }
}

@Composable
internal fun exchangeErrorText(code: String): String = when (code) {
    "file_too_large" -> stringResource(R.string.exchange_error_too_large)
    "invalid_json" -> stringResource(R.string.exchange_error_invalid_json)
    "not_anarkey_file" -> stringResource(R.string.exchange_error_not_anarkey)
    "missing_version" -> stringResource(R.string.exchange_error_missing_version)
    "unsupported_version" -> stringResource(R.string.exchange_error_unsupported_version)
    "missing_songs", "no_songs" -> stringResource(R.string.exchange_error_no_songs)
    "too_many_songs" -> stringResource(R.string.exchange_error_too_many, ExchangeRules.MAX_SONGS)
    else -> stringResource(R.string.exchange_error_unreadable)
}

/** Said before the system picker opens, which lists every file and says nothing about what Anarkey can read. */
@Composable
internal fun ImportInfoDialog(onChoose: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.exchange_info_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.exchange_info_chordpro_name), style = MaterialTheme.typography.titleSmall)
                Text(".cho  .chordpro  .chopro  .pro", color = NeonSoft, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.exchange_info_chordpro_detail), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.exchange_info_anarkey_name), style = MaterialTheme.typography.titleSmall)
                Text(".anarkeysong.json  (.json)", color = NeonSoft, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.exchange_info_anarkey_detail), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.exchange_info_note), color = Muted, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = onChoose) { Text(stringResource(R.string.exchange_info_choose)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

/** What was found in a file, before anything is stored. Songs that already exist start unchecked. */
@Composable
internal fun ImportPreviewDialog(candidates: List<SongViewModel.ImportCandidate>, onConfirm: (List<ImportedSong>) -> Unit, onDismiss: () -> Unit) {
    val selected = remember(candidates) { mutableStateListOf<Boolean>().apply { candidates.forEach { add(!it.duplicate) } } }
    val count = selected.count { it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.exchange_preview_title)) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.exchange_preview_hint), color = Muted, style = MaterialTheme.typography.bodySmall)
            candidates.forEachIndexed { index, candidate ->
                val song = candidate.imported.song
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(selected[index], onCheckedChange = { selected[index] = it })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(song.title.ifBlank { stringResource(R.string.exchange_untitled) }, style = MaterialTheme.typography.titleMedium)
                        song.artist?.let { Text(it, color = Muted) }
                        Text(stringResource(R.string.exchange_summary, song.sections.size, song.lineCount, song.chordCount), style = MaterialTheme.typography.bodySmall)
                        if (song.noteCount + song.restCount > 0) Text(stringResource(R.string.exchange_summary_extra, song.noteCount, song.restCount),
                            style = MaterialTheme.typography.bodySmall)
                        if (candidate.duplicate) Text(stringResource(R.string.exchange_duplicate), color = NeonSoft, style = MaterialTheme.typography.bodySmall)
                        candidate.imported.notices.forEach { Text("• " + noticeText(it), color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } },
        confirmButton = { TextButton(enabled = count > 0, onClick = { onConfirm(candidates.filterIndexed { i, _ -> selected[i] }.map { it.imported }) }) {
            Text(stringResource(R.string.exchange_import_count, count))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

/** Shown before sharing, so what a format cannot carry (or leaves out) is stated rather than discovered later. */
@Composable
internal fun ExportDialog(file: SongViewModel.ExportFile, native: Boolean, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.exchange_share_title)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.fileName, style = MaterialTheme.typography.titleSmall, color = NeonSoft)
            Text(stringResource(if (native) R.string.exchange_native_includes else R.string.exchange_chordpro_includes))
            Text(stringResource(R.string.exchange_recordings_not_included), color = Muted)
            if (file.notices.isNotEmpty()) {
                Text(stringResource(R.string.exchange_limits_title), style = MaterialTheme.typography.titleSmall)
                file.notices.forEach { Text("• " + noticeText(it), color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        } },
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.exchange_share)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

/** Text of a chosen document and a name to fall back on for its title; null text when it cannot be read. */
internal fun readDocument(context: Context, uri: Uri): Pair<String?, String> {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    val title = name.substringBeforeLast('.', name).replace('_', ' ').trim()
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                // Stop early: a file this large is refused anyway, and reading it all would only waste memory.
                if (buffer.size() > ExchangeRules.MAX_FILE_CHARS * 2) break
            }
            buffer.toByteArray()
        }
    }.getOrNull() ?: return null to title
    var text = String(bytes, Charsets.UTF_8)
    // Older ChordPro files are often Latin-1; only fall back when UTF-8 clearly does not fit.
    if (text.contains('�')) text = String(bytes, Charsets.ISO_8859_1)
    return text to title
}

internal fun shareExport(context: Context, file: SongViewModel.ExportFile, subject: String) {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    // Only the latest few exports are kept; the system copies what it needs when sharing.
    directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
    val target = File(directory, file.fileName)
    target.writeText(file.text, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    val send = Intent(Intent.ACTION_SEND).setType(file.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, subject).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
