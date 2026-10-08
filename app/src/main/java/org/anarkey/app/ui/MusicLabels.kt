package org.anarkey.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import org.anarkey.app.R
import org.anarkey.core.music.NoteNaming

internal object MusicLabels {
    val instruments = mapOf("guitar" to R.string.guitar, "ukulele" to R.string.ukulele, "violin" to R.string.violin,
        "bass" to R.string.bass, "mandolin" to R.string.mandolin, "banjo" to R.string.banjo)
    val tunings = mapOf(
        "guitar.standard" to R.string.tuning_standard, "guitar.drop_d" to R.string.tuning_drop_d,
        "guitar.dadgad" to R.string.tuning_dadgad, "ukulele.high_g" to R.string.tuning_high_g,
        "ukulele.low_g" to R.string.tuning_low_g, "violin.standard" to R.string.tuning_standard,
        "bass.standard" to R.string.tuning_standard, "mandolin.standard" to R.string.tuning_standard,
        "banjo.open_g" to R.string.tuning_open_g,
    )
}

@StringRes
internal fun NoteNaming.label(): Int = when (this) {
    NoteNaming.LETTERS_SHARPS -> R.string.naming_letters_sharps
    NoteNaming.LETTERS_FLATS -> R.string.naming_letters_flats
    NoteNaming.SOLFEGE_SHARPS -> R.string.naming_solfege_sharps
    NoteNaming.SOLFEGE_FLATS -> R.string.naming_solfege_flats
}

@Composable
internal fun noteNames(naming: NoteNaming): Array<String> = stringArrayResource(when (naming) {
    NoteNaming.LETTERS_SHARPS -> R.array.note_names
    NoteNaming.LETTERS_FLATS -> R.array.note_names_flats
    NoteNaming.SOLFEGE_SHARPS -> R.array.note_names_solfege_sharps
    NoteNaming.SOLFEGE_FLATS -> R.array.note_names_solfege_flats
})
