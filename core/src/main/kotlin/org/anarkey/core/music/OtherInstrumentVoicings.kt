package org.anarkey.core.music

/** Turns the generated shape table into voicings. Ukulele high-G and low-G share shapes: only the octave of one string differs. */
internal object OtherInstrumentVoicings {
    private const val FRET_CHARS = "0123456789abc"

    private fun parse(prefix: String, instrumentId: String, tuningId: String, lines: List<String>): List<ChordVoicing> = lines.map { line ->
        val (symbol, n, fretText, fingerText, barreText) = line.split('|')
        val frets = fretText.map { if (it == 'x') null else FRET_CHARS.indexOf(it) }
        val fingers = fingerText.mapIndexedNotNull { index, char ->
            char.digitToInt().takeIf { it > 0 }?.let { FingerPosition(index, requireNotNull(frets[index]), it) }
        }
        val highest = frets.filterNotNull().maxOrNull() ?: 0
        val baseFret = if (highest > 5) frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 1 else 1
        ChordVoicing("$prefix.$symbol.$n", instrumentId, tuningId, symbol, frets, fingers, baseFret,
            barreText.toIntOrNull(), "generated shape; pitch and finger geometry tested; playability not independently certified")
    }

    val entries: List<ChordVoicing> =
        parse("uke.high", "ukulele", "ukulele.high_g", OtherInstrumentVoicingData.uke) +
            parse("uke.low", "ukulele", "ukulele.low_g", OtherInstrumentVoicingData.uke) +
            parse("mandolin", "mandolin", "mandolin.standard", OtherInstrumentVoicingData.mandolin) +
            parse("banjo", "banjo", "banjo.open_g", OtherInstrumentVoicingData.banjo) +
            parse("bass", "bass", "bass.standard", OtherInstrumentVoicingData.bass)
}
