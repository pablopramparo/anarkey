package org.anarkey.core.music

/** Hand-specified shapes, not a combinatorial fret search. Only closed shapes are transposed.
 * Frets and fingers run from low E to high E; x/0 in the finger map means no finger.
 * Musical correctness and geometric finger constraints are checked in catalogue tests.
 */
internal object StandardGuitarVoicings {
    private data class Shape(
        val id: String, val symbol: String, val frets: String, val fingers: String, val barre: Int? = null,
    ) {
        fun build(shift: Int = 0): ChordVoicing {
            val positions = frets.map { if (it == 'x') null else it.digitToInt() + shift }
            val fingerPositions = fingers.mapIndexedNotNull { index, char ->
                char.digitToInt().takeIf { it > 0 }?.let { FingerPosition(index, requireNotNull(positions[index]), it) }
            }
            val min = positions.filterNotNull().filter { it > 0 }.minOrNull() ?: 1
            return ChordVoicing("gtr.$id" + if (id.endsWith(".open")) "" else ".f$min",
                "guitar", "guitar.standard", ChordTransposition.transpose(symbol, shift), positions,
                fingerPositions, if ((positions.filterNotNull().maxOrNull() ?: 0) > 5) min else 1,
                barre?.plus(shift), "hand-specified shape; transposed in standard tuning; pitch and finger geometry tested")
        }

        fun inAllKeys(): List<ChordVoicing> {
            require('0' !in frets) { "Open strings cannot be moved with a closed shape" }
            val min = frets.filter { it != 'x' }.minOf { it.digitToInt() }
            return (0..11).map { shift -> build(if (min + shift > 12) shift - 12 else shift) }
        }
    }

    private val closed = listOf(
        Shape("e.major", "F", "133211", "134211", 1),
        Shape("a.major", "Bb", "x13331", "012341", 1),
        Shape("e.minor", "Fm", "133111", "134111", 1),
        Shape("a.minor", "Bbm", "x13321", "013421", 1),
        Shape("e.seventh", "F7", "131211", "131211", 1),
        Shape("a.seventh", "Bb7", "x13131", "013141", 1),
        Shape("d.seventh", "F7", "xx3545", "001324"),
        Shape("a.major7", "Bbmaj7", "x13231", "013241", 1),
        Shape("e.minor7", "Fm7", "131111", "131111", 1),
        Shape("a.minor7", "Bbm7", "x13121", "013121", 1),
        Shape("a.dim", "Cdim", "x3454x", "012430"),
        Shape("a.aug", "Caug", "x3211x", "032110", 1),
        Shape("a.sus2", "Bbsus2", "x13311", "013411", 1),
        Shape("e.sus4", "Fsus4", "133311", "123411", 1),
        Shape("a.dim7", "Bdim7", "x2313x", "023140"),
        Shape("a.halfdim7", "Bm7b5", "x2323x", "013240"),
        Shape("a.sixth", "Bb6", "x13333", "013333", 3),
        Shape("a.minor6", "Cm6", "x3121x", "031210", 1),
        Shape("d.add9", "Gadd9", "xx5435", "003214"),
        Shape("d.minoradd9", "Gmadd9", "xx5335", "003114", 3),
        Shape("a.ninth", "C9", "x3233x", "021340"),
        // 11: root, third, b7, ninth, eleventh (fifth omitted).
        Shape("e.eleventh", "C11", "87876x", "324210", 7),
        // 13: root, b7, third, thirteenth; optional fifth/ninth/eleventh omitted.
        Shape("e.thirteenth", "F13", "1x123x", "102340"),
        Shape("e.power", "F5", "133xxx", "134000"),
    )

    private val open = listOf(
        Shape("c7.open", "C7", "x32310", "032410"),
        Shape("d7.open", "D7", "xx0212", "000213"),
        Shape("e7.open", "E7", "020100", "020100"),
        Shape("a7.open", "A7", "x02020", "001020"),
        Shape("b7.open", "B7", "x21202", "021304"),
        Shape("g7.open", "G7", "320001", "320001"),
        Shape("am7.open", "Am7", "x02010", "002010"),
        Shape("em7.open", "Em7", "020000", "020000"),
        Shape("dm7.open", "Dm7", "xx0211", "000211", 1),
        Shape("cmaj7.open", "Cmaj7", "x32000", "032000"),
        Shape("dmaj7.open", "Dmaj7", "xx0222", "000111", 2),
        Shape("emaj7.open", "Emaj7", "021100", "031200"),
        Shape("amaj7.open", "Amaj7", "x02120", "002130"),
        Shape("fmaj7.open", "Fmaj7", "xx3210", "003210"),
        Shape("asus2.open", "Asus2", "x02200", "002300"),
        Shape("dsus2.open", "Dsus2", "xx0230", "000130"),
        Shape("asus4.open", "Asus4", "x02230", "001230"),
        Shape("dsus4.open", "Dsus4", "xx0233", "000134"),
        Shape("esus4.open", "Esus4", "022200", "012300"),
        Shape("cadd9.open", "Cadd9", "x32030", "032040"),
        Shape("am6.open", "Am6", "x02212", "002314"),
    )

    val entries: List<ChordVoicing> = open.map { it.build() } + closed.flatMap { it.inAllKeys() }
}
