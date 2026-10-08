package org.anarkey.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.anarkey.app.R
import org.anarkey.core.music.*

@Composable
internal fun TuningSelectors(selection: TunerSelection, names: Array<String>, onSelection: (TunerSelection) -> Unit) {
    var choosingInstrument by rememberSaveable { mutableStateOf(false) }
    var choosingTuning by rememberSaveable { mutableStateOf(false) }
    val tuning = selection.tuning
    val instrumentId = selection.instrumentId
    val instrumentName = stringResource(selection.instrumentId?.let { MusicLabels.instruments.getValue(it) } ?: R.string.chromatic)
    val tuningName = stringResource(tuning?.id?.let { MusicLabels.tunings.getValue(it) } ?: R.string.automatic_notes)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorButton(instrumentName, onClick = { choosingInstrument = true }, primary = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), supportingText = stringResource(R.string.instrument_label))
            SelectorButton(tuningName, onClick = { choosingTuning = true }, enabled = tuning != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), supportingText = stringResource(R.string.tuning_label))
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorButton(instrumentName, onClick = { choosingInstrument = true }, primary = true,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp), supportingText = stringResource(R.string.instrument_label))
            SelectorButton(tuningName, onClick = { choosingTuning = true }, enabled = tuning != null,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp), supportingText = stringResource(R.string.tuning_label))
        }
    }
    if (choosingInstrument) AlertDialog(
        onDismissRequest = { choosingInstrument = false },
        title = { Text(stringResource(R.string.instrument_label)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                SelectionRow(stringResource(R.string.chromatic), selection.instrumentId == null) {
                    onSelection(TunerSelection.Chromatic); choosingInstrument = false
                }
                TuningCatalog.instruments.forEach { instrument ->
                    SelectionRow(stringResource(MusicLabels.instruments.getValue(instrument.id)), selection.instrumentId == instrument.id) {
                        if (selection.instrumentId != instrument.id) onSelection(TunerSelection(instrument.id, TuningCatalog.forInstrument(instrument.id).first().id))
                        choosingInstrument = false
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingInstrument = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    if (choosingTuning && instrumentId != null) AlertDialog(
        onDismissRequest = { choosingTuning = false },
        title = { Text(stringResource(R.string.tuning_label)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                TuningCatalog.forInstrument(instrumentId).forEach { candidate ->
                    val pitches = candidate.strings.joinToString(" · ") { names[Note(it.midi).pitchClass] + Note(it.midi).octave }
                    SelectionRow(stringResource(MusicLabels.tunings.getValue(candidate.id)) + "\n" + pitches, candidate.id == selection.tuningId) {
                        onSelection(TunerSelection(candidate.instrumentId, candidate.id)); choosingTuning = false
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingTuning = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
