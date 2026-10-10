package org.anarkey.app.songs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.anarkey.app.R
import org.anarkey.app.recording.data.SongDocument
import org.anarkey.app.ui.Muted
import org.anarkey.core.music.*

@Composable
internal fun SongTransposeDialog(document: SongDocument, naming: NoteNaming, onDismiss: () -> Unit,
    onApply: (Int, Boolean, () -> Unit) -> Unit, onRestore: (Boolean, () -> Unit) -> Unit) {
    var interval by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val flats = naming == NoteNaming.LETTERS_FLATS || naming == NoteNaming.SOLFEGE_FLATS
    // Rests have nothing to transpose; notes move with the chords.
    val symbols = document.chords.values.flatten().map { it.originalSymbol }.distinct().filter { SongMarks.parse(it) != SongMark.Rest }
    fun movable(symbol: String) = SongMarks.parse(symbol) is SongMark.Note || ChordSymbolParser.parse(symbol).interpretable
    val unknown = symbols.filter { !movable(it) }
    val canTranspose = document.song.keyRoot != null || symbols.any(::movable)
    fun display(symbol: String) = SongMarks.display(symbol, naming)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.song_transpose)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.song_transpose_hint))
            val offset = document.song.transposeOffset
            if (offset != 0) {
                Text(stringResource(R.string.song_transpose_from_original, offset), color = Muted)
                OutlinedButton(enabled = !saving, onClick = { saving = true; onRestore(flats) { saving = false } }) {
                    Text(stringResource(R.string.song_transpose_restore))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(enabled = !saving && interval > -11, onClick = { interval-- }) { Text("−1") }
                Text(stringResource(R.string.song_transpose_interval, interval), Modifier.weight(1f))
                TextButton(enabled = !saving && interval < 11, onClick = { interval++ }) { Text("+1") }
            }
            document.song.keyRoot?.let { key ->
                Text(stringResource(R.string.song_transpose_key, display(key),
                    display(ChordTransposition.transpose(key, interval, flats))))
            }
            symbols.filter { it !in unknown }.forEach { symbol ->
                Text(display(symbol) + " → " + display(ChordTransposition.transpose(symbol, interval, flats)))
            }
            if (unknown.isNotEmpty()) Text(stringResource(R.string.song_transpose_unknown, unknown.joinToString(", ")))
            if (!canTranspose) Text(stringResource(R.string.song_transpose_empty))
        } },
        confirmButton = { TextButton(enabled = !saving && interval != 0 && canTranspose,
            onClick = { saving = true; onApply(interval, flats) { saving = false } }) {
            Text(stringResource(R.string.song_transpose_save))
        } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
