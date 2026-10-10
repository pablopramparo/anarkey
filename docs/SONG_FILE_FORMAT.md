# Song files: ChordPro and the Anarkey format

Songs can be imported from, and shared as, two kinds of file. Both work offline; no catalogue or
download service is involved.

| | ChordPro (`.cho`, `.chordpro`, `.chopro`, `.pro`) | Anarkey (`*.anarkeysong.json`) |
|---|---|---|
| Purpose | Interoperability with other programs | Backups and moving songs between devices, without loss |
| Fidelity | Lyrics, chords and structure; no notes, rests or note values | Everything Anarkey stores about a song |
| Reading | Tolerant: unknown directives are reported, never fatal | Strict and validated, forward compatible |

The reader chooses the format from the content, not the extension, so a renamed file still opens.
Nothing is ever overwritten: importing always creates new songs, and a song that already exists
(same title and artist) starts unchecked in the preview.

Code: `core/src/main/kotlin/org/anarkey/core/exchange/` (`ChordProFormat`, `AnarkeyFormat`,
`ExchangeRules`, `Json`). It is plain Kotlin, with tests in `core/src/test/.../exchange/`.

## What is never exported

Linked **recordings and their audio** are not part of either format. Audio lives in the device's
private storage; promising to move it would need the files themselves. The share dialog says so.
A song also carries no file paths, database ids or device-specific references.

## ChordPro

### Read

| Directive | Result |
|---|---|
| `title` / `t` | Title (file name when absent) |
| `artist`; otherwise the first `subtitle` / `st` | Artist |
| `composer`, `lyricist`, `arranger`, `album`, `year`, `copyright`, other `subtitle`, `comment_italic` | Kept as lines in the song notes |
| `key` (`Am`, `F#`, `Bb`, `A minor`) | Key and mode |
| `tempo`, `time` (`3/4`), `capo` | Tempo, meter, capo |
| `start_of_verse`/`sov`, `chorus`/`soc`, `bridge`/`sob`, `intro`, `outro`, `solo`, `prechorus`, `part`, any `start_of_*` | A section, with the label as its title |
| `comment` / `c` outside an environment | Opens a section named after the comment |
| `chorus` | Repeats the last chorus |
| `start_of_tab`, `start_of_grid` | Kept as plain text lines, with a notice |
| `new_song` / `ns` | Several songs in one file |
| `x_anarkey_instrument`, `x_anarkey_tag`, `x_anarkey_transpose` | Anarkey extensions (see below) |
| fonts, sizes, colours, pages, columns, `define`, `meta` (except `tag`) | Ignored; counted in one notice |
| anything else | Reported as an unknown directive and skipped |

Inline `[Am]` chords are anchored to the character they precede, counted in Unicode code points.
`[*…]` annotations are dropped unless they are an Anarkey note or rest (below). Lines starting
with `#` are comments; an unclosed `[` is plain text. CRLF, a BOM and Latin-1 files are accepted.

### Written

Standard directives for title, artist, key, tempo, time and capo; sections as
`{start_of_verse: Title}` … `{end_of_verse}` (`part` for custom sections); lyrics with `[chord]` at
the character positions. Anarkey-only data uses `x_` directives, which other programs ignore:
`x_anarkey_instrument`, `x_anarkey_tag`, `x_anarkey_transpose`.

ChordPro has no notes, rests or note values, so Anarkey writes them as annotations that other
programs show as text and Anarkey reads back:

```
[*♪La4:1/4]   a note: ♪ + name + octave, then ":" and its value
[*_:1/2.]     a rest, with a dotted half value
```

Chord durations cannot be written at all. The export dialog counts what was written as an
annotation and what was lost before the file is shared.

## Anarkey format, version 1

```json
{
  "format": "anarkey-song",
  "version": 1,
  "exportedAt": "2026-10-10T12:00:00Z",
  "positionUnit": "unicode-code-point",
  "songs": [{
    "title": "Amazing Grace",
    "artist": "Traditional",
    "key": { "root": "G", "mode": "major" },
    "tempo": 84,
    "meter": { "numerator": 3, "denominator": 4 },
    "capo": 0,
    "instrument": { "id": "guitar", "tuning": "guitar.standard" },
    "notes": "",
    "favorite": false,
    "transposeOffset": 0,
    "tags": ["hymn"],
    "lyrics": [ { "text": "…", "marks": [ { "at": 0, "symbol": "G", "figure": null } ] } ],
    "sections": [
      { "kind": "verse", "title": "Verso 1", "notes": "",
        "lines": [ { "text": "Amazing grace!", "marks": [ { "at": 0, "symbol": "G" }, { "at": 8, "symbol": "♪B4", "figure": "1/4" } ] } ] }
    ]
  }]
}
```

* `symbol` is a chord (`Am7`), a note (`♪A4`; octave defaults to 4) or a rest (`_`).
* `figure` is `1/1`, `1/2`, `1/4`, `1/8` or `1/16`, with a trailing `.` when dotted; `null` on a
  chord means it lasts one bar.
* `kind` is one of `intro`, `verse`, `prechorus`, `chorus`, `bridge`, `solo`, `outro`, `custom`.
* `transposeOffset` is the semitones (0–11) the song was moved up from its original key, so the
  original can be restored.
* A song without `instrument` follows the last instrument used for chords.

### Versioning

`version` is an integer. A reader accepts versions up to its own and refuses higher ones with a
clear message; **unknown fields are ignored**, so a later version can add data without breaking
this one. Changes that would alter the meaning of an existing field must raise `version`.

### Validation

Importing is defensive: a file is never trusted. Strings and counts are capped (`ExchangeRules`),
positions are clamped to the line, unknown note values are dropped, invalid tempo, meter, key,
capo, instrument or tuning are cleared, and every repair is reported in the import preview.
A file above 5 MB, more than 1,000 songs, or a wrong `format` is refused as a whole.
