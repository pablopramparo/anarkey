package org.anarkey.core.music

/** Shared by the chord picker and catalogue coverage checks. */
enum class ChordQuality(val id: String, val suffix: String) {
    MAJOR("major", ""), MINOR("minor", "m"), DOMINANT7("dominant7", "7"),
    MAJOR7("major7", "maj7"), MINOR7("minor7", "m7"), DIMINISHED("diminished", "dim"),
    AUGMENTED("augmented", "aug"), SUS2("sus2", "sus2"), SUS4("sus4", "sus4"),
    DIM7("dim7", "dim7"), HALF_DIM7("halfDim7", "m7b5"), SIXTH("sixth", "6"),
    MINOR6("minor6", "m6"), ADD9("add9", "add9"), MINOR_ADD9("minorAdd9", "madd9"),
    NINTH("ninth", "9"), ELEVENTH("eleventh", "11"), THIRTEENTH("thirteenth", "13"),
    POWER("power", "5"),
}
