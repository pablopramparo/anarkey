#!/usr/bin/env python3
"""Generates core/src/main/kotlin/org/anarkey/core/music/OtherInstrumentVoicingData.kt.

Searches playable fingerings for ukulele, mandolin, banjo (open G) and bass, then writes them as data.
Each shape must: use only chord tones, contain the chord's essential intervals, stay within a 4-fret
hand span, need at most four fingers, keep open strings inside the first five frets, and be a valid
barre. The generated Kotlin is checked again by the core tests against the notes of each tuning.

Run from the repository root:  python tools/chords/generate_voicings.py
"""
from itertools import product
from pathlib import Path

NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
# suffix: (intervals, essential intervals) mirroring ChordTheory.formulas
QUALITIES = {
    "": ([0, 4, 7], {0, 4}), "m": ([0, 3, 7], {0, 3}), "7": ([0, 4, 7, 10], {0, 4, 10}),
    "maj7": ([0, 4, 7, 11], {0, 4, 11}), "m7": ([0, 3, 7, 10], {0, 3, 10}), "dim": ([0, 3, 6], {0, 3, 6}),
    "aug": ([0, 4, 8], {0, 4, 8}), "sus2": ([0, 2, 7], {0, 2, 7}), "sus4": ([0, 5, 7], {0, 5, 7}),
    "dim7": ([0, 3, 6, 9], {0, 3, 6, 9}), "m7b5": ([0, 3, 6, 10], {0, 3, 6, 10}),
    "6": ([0, 4, 7, 9], {0, 4, 9}), "m6": ([0, 3, 7, 9], {0, 3, 9}),
    "add9": ([0, 4, 7, 14], {0, 4, 14}), "madd9": ([0, 3, 7, 14], {0, 3, 14}),
    "9": ([0, 4, 7, 10, 14], {0, 4, 10, 14}), "11": ([0, 4, 7, 10, 14, 17], {0, 4, 10, 17}),
    "13": ([0, 4, 7, 10, 14, 17, 21], {0, 4, 10, 21}), "5": ([0, 7], {0, 7}),
}
MAX_FRET = 12
FRET_CHARS = "0123456789abc"


class Instrument:
    def __init__(self, key, midi, drone_first=False, bass=False, per_chord=2):
        self.key, self.midi, self.drone_first, self.bass, self.per_chord = key, midi, drone_first, bass, per_chord


INSTRUMENTS = [
    Instrument("uke", [67, 60, 64, 69]),            # high G; low G has identical pitch classes
    Instrument("mandolin", [55, 62, 69, 76]),
    Instrument("banjo", [67, 50, 55, 59, 62], drone_first=True),
    Instrument("bass", [28, 33, 38, 43], bass=True),
]


def string_options(inst, index, chord_pcs):
    if inst.drone_first and index == 0:  # fifth string: left open when it is a chord tone, otherwise muted
        return [0] if inst.midi[0] % 12 in chord_pcs else [None]
    if inst.bass:
        return [None] + list(range(0, MAX_FRET + 1))
    return list(range(0, MAX_FRET + 1))


def analyse(inst, frets, root, pcs, essentials_pcs, tone_pcs):
    sounding = [i for i, f in enumerate(frets) if f is not None]
    if len(sounding) < 2:
        return None
    midi = [inst.midi[i] + frets[i] for i in sounding]
    played = {m % 12 for m in midi}
    if not played <= pcs or not essentials_pcs <= played:
        return None
    if inst.bass:
        if sounding != list(range(sounding[0], sounding[-1] + 1)):
            return None
        if min(midi) % 12 != root:
            return None
    fretted = [(f, i) for i, f in enumerate(frets) if f]
    open_strings = [i for i in sounding if frets[i] == 0 and not (inst.drone_first and i == 0)]
    if fretted:
        lo, hi = min(f for f, _ in fretted), max(f for f, _ in fretted)
        if hi - lo > 3:
            return None
        if open_strings and hi > 4:
            return None
    else:
        lo = hi = 0
    barre_strings = [i for f, i in fretted if f == lo] if fretted else []
    barre = None
    # A flat finger is only used when three or more strings share the lowest fret and it does not mute or
    # block a lower note in between; two notes on one fret are simply played with two fingers.
    if len(barre_strings) >= 3 and all(
        frets[i] is not None and frets[i] >= lo for i in range(min(barre_strings), max(barre_strings) + 1)
    ):
        barre = lo
    singles = [(f, i) for f, i in fretted if not (barre is not None and f == lo)]
    fingers_needed = len(singles) + (1 if barre is not None else 0)
    if fingers_needed > 4:
        return None
    fingers = {}
    order = ([("barre", None)] if barre is not None else []) + [("one", s) for s in sorted(singles)]
    next_finger = 1
    for kind, item in order:
        if kind == "barre":
            for i in barre_strings:
                fingers[i] = 1
        else:
            fingers[item[1]] = next_finger
        next_finger += 1
    missing = len(tone_pcs - played)
    span = hi - lo if fretted else 0
    score = 12 * fingers_needed + 7 * (barre is not None) + 2 * span + hi + 5 * missing
    if inst.bass:
        score += 8 * max(0, min(3, len(tone_pcs)) - len(sounding))
    return {"frets": frets, "fingers": fingers, "barre": barre, "score": score, "pos": lo}


def shapes_for(inst, root, suffix):
    intervals, essential = QUALITIES[suffix]
    pcs = {(root + i) % 12 for i in intervals}
    essentials_pcs = {(root + i) % 12 for i in essential}
    tone_pcs = set(pcs)
    options = [string_options(inst, i, pcs) for i in range(len(inst.midi))]
    found = []
    for frets in product(*options):
        shape = analyse(inst, list(frets), root, pcs, essentials_pcs, tone_pcs)
        if shape:
            found.append(shape)
    found.sort(key=lambda s: (s["score"], s["frets"] != sorted(s["frets"], key=lambda x: -1 if x is None else x), [-1 if f is None else f for f in s["frets"]]))
    chosen = []
    for shape in found:
        if all(abs(shape["pos"] - other["pos"]) >= 3 for other in chosen):
            chosen.append(shape)
        if len(chosen) == inst.per_chord:
            break
    return chosen


def encode(shape, count):
    frets = "".join("x" if f is None else FRET_CHARS[f] for f in shape["frets"])
    fingers = "".join(str(shape["fingers"].get(i, 0)) for i in range(count))
    return frets, fingers


def main():
    out = ["package org.anarkey.core.music", "",
           "/** GENERATED by tools/chords/generate_voicings.py - do not edit by hand. Shapes are searched with fixed",
           " * playability rules (<= 4 fingers, 4-fret span, valid barres) and re-validated against each tuning's notes",
           " * in the core tests. They are not certified by a player. Format: symbol|n|frets|fingers|barre fret or -.",
           " * Frets use 0-9 then a,b,c for 10-12; x is muted. Fingers are per string; 0 means no finger.",
           " */", "internal object OtherInstrumentVoicingData {"]
    summary = []
    for inst in INSTRUMENTS:
        lines, missing = [], []
        for root in range(12):
            for suffix in QUALITIES:
                symbol = NAMES[root] + suffix
                shapes = shapes_for(inst, root, suffix)
                if not shapes:
                    missing.append(symbol)
                for n, shape in enumerate(shapes, 1):
                    frets, fingers = encode(shape, len(inst.midi))
                    barre = "-" if shape["barre"] is None else str(shape["barre"])
                    lines.append(f'        "{symbol}|{n}|{frets}|{fingers}|{barre}",')
        out.append(f"    val {inst.key} = listOf(")
        out += lines
        out.append("    )")
        summary.append((inst.key, len(lines), missing))
    out.append("}")
    path = Path("core/src/main/kotlin/org/anarkey/core/music/OtherInstrumentVoicingData.kt")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    for key, count, missing in summary:
        print(f"{key}: {count} shapes; chords without any shape ({len(missing)}): {missing}")


if __name__ == "__main__":
    main()
