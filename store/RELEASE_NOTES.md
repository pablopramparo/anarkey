# Release notes for Google Play

"What's new" is limited to 500 characters per language. Paste the text of the version being uploaded.

## 0.2.4-prototype (versionCode 19)

Replaces version 18 in the closed test. Fixes a tap on a tool that could be ignored when reopening the app. Uploaded to Google Play (2026-10-22), with these notes.

### Español (es-US / es-419)

```
Novedades:
• Corregido: a veces, al volver a abrir la app, el botón del afinador no respondía hasta entrar en otra herramienta.
• Exportá tus grabaciones: todas o por sesión, en un .zip que guardás donde quieras (por ejemplo Descargas).
• Biblioteca de canciones más ordenada: búsqueda, un solo botón para crear y botón para importar.
• Ahora Anarkey es software libre bajo licencia GPLv3.
```

### English (en-US)

```
What's new:
• Fixed: sometimes, when reopening the app, the tuner button did not respond until you opened another tool.
• Export your recordings: all of them or by session, as a .zip you save anywhere (for example Downloads).
• Tidier song library: search, a single create button and an import button.
• Anarkey is now free software under the GPLv3 license.
```

## 0.2.3-prototype (versionCode 18)

Replaces version 17 in the closed test. Adds zip export of recordings and a tidier song library.

### Español (es-US / es-419)

```
Novedades:
• Exportá tus grabaciones: todas o por sesión, en un .zip que guardás donde quieras (por ejemplo Descargas).
• Biblioteca de canciones más ordenada: búsqueda, un solo botón para crear y botón para importar.
• Grabar desde una canción: al terminar volvés a la canción.
• Importá y compartí canciones en ChordPro o como copia de Anarkey.
• Ahora Anarkey es software libre bajo licencia GPLv3.
```

### English (en-US)

```
What's new:
• Export your recordings: all of them or by session, as a .zip you save anywhere (for example Downloads).
• Tidier song library: search, a single create button and an import button.
• Recording from a song: when you finish you return to the song.
• Import and share songs as ChordPro or as an Anarkey copy.
• Anarkey is now free software under the GPLv3 license.
```

## 0.2.2-prototype (versionCode 17)

Replaces version 16 in the closed test. Fixes a navigation trap when recording from a song.

### Español (es-US / es-419)

```
Novedades:
• Grabar desde una canción: al terminar volvés a la canción, sin quedar atrapado en el grabador.
• Canciones: reproducí los acordes en orden con la letra resaltada.
• Notas y silencios con figuras, y piano en Acordes.
• Importá y compartí canciones en ChordPro o como copia de Anarkey.
• Cada canción recuerda su instrumento. Transponé y volvé al original.
• Metrónomo: los sonidos madera, digital y campana se distinguen.
```

### English (en-US)

```
What's new:
• Recording from a song: when you finish you return to the song instead of getting stuck in the recorder.
• Songs: play the chords in order with the lyric highlighted.
• Notes and rests with note values, and piano in Chords.
• Import and share songs as ChordPro or as an Anarkey copy.
• Each song remembers its instrument. Transpose and return to the original.
• Metronome: the wood, digital and bell sounds are clearly different.
```

## 0.2.1-prototype (versionCode 16)

Replaces version 15 in the closed test. Same notes as 0.2.0 plus the metronome sounds.

### Español (es-US / es-419)

```
Novedades:
• Canciones: reproducí los acordes en orden con la letra resaltada.
• Notas y silencios con figuras, y piano en Acordes.
• Importá y compartí canciones en ChordPro o como copia de Anarkey.
• Vista de canción más clara: lectura limpia y edición aparte.
• Cada canción recuerda su instrumento. Transponé y volvé al original.
• Metrónomo: los sonidos madera, digital y campana ahora se distinguen.
```

### English (en-US)

```
What's new:
• Songs: play the chords in order with the lyric highlighted.
• Notes and rests with note values, and piano in Chords.
• Import and share songs as ChordPro or as an Anarkey copy.
• Clearer song view: clean reading and separate editing.
• Each song remembers its instrument. Transpose and return to the original.
• Metronome: the wood, digital and bell sounds are now clearly different.
```

Metronome fix: every click was cut at the end of the 5 ms audio block, so no sound lasted longer than
that and the three sounds were nearly identical. Clicks now ring to their end; the bell marks only the
accented beats, as on a mechanical metronome.

## 0.2.0-prototype (versionCode 15)

### Español (es-419 / es-ES)

```
Novedades:
• Canciones: reproducí los acordes en orden con la letra resaltada.
• Notas y silencios con figuras, y piano en Acordes.
• Importá y compartí canciones en ChordPro o como copia de Anarkey.
• Vista de canción más clara: lectura limpia y edición aparte.
• Cada canción recuerda su instrumento. Transponé y volvé al original.
• Canciones de ejemplo para empezar.
```

### English (en-US)

```
What's new:
• Songs: play the chords in order with the lyric highlighted.
• Notes and rests with note values, and piano in Chords.
• Import and share songs as ChordPro or as an Anarkey copy.
• Clearer song view: clean reading and separate editing.
• Each song remembers its instrument. Transpose and return to the original.
• Example songs to get started.
```

## What testers should check

* **Updating keeps everything.** The database moves from version 4 to 6 (transposition offset and note
  values). Songs, favourites and recordings must still be there after the update.
* **Language switch** (Settings): both languages must appear, whichever language the phone uses.
* **Songs:** open one, play the chords, edit a line, add a note and a rest, transpose and restore the
  original key.
* **Import:** a `.cho` file from another program, and a `.anarkeysong.json` shared from another device.
* **Share:** a song as ChordPro and as an Anarkey copy, from the song's ⋮ menu.

## Changes that matter for the Play Console

* **Permissions:** unchanged. Importing uses the system file picker and sharing uses the system share
  sheet, so no storage permission is needed.
* **Data safety:** no change. Nothing leaves the device unless the person shares a file, and no network is
  used. Linked recordings are never part of exported song files.
* **Bundle:** language splits are disabled (`bundle.language.enableSplit = false`), because the app has its
  own language switch. The download is slightly larger and both languages are always present.
* **Signing:** same key as the previous build (SHA-256 `9edffa10…a84d`).
