# M2 — Captura y organización

M2 adds local idea recording and optional sessions. Audio is never stored in Room.

## Data and files

Room v2 stores `Session`, `Recording`, and `RecordingMarker`. A recording may have no
session. Deleting a session applies `SET NULL`; deleting a recording cascades to its
markers. `Recording.songId` is a nullable future reference only: M2 has no `Song`
entity/table or song UI. `waveformPeaks` stores only 256 decoded peak bytes.

The database v1→v2 migration preserves sessions and recordings, adds `songId` and
the waveform cache, and creates the marker table/index. The exported Room schemas are
under `app/schemas/`.

Audio is stored under the app-private `files/recordings/` directory with UUID names:
`<uuid>.m4a.partial` during capture and `<uuid>.m4a` after finalization. Display names
never determine file paths.

## Capture and microphone ownership

The visible recorder screen starts a non-exported microphone foreground service. It
immediately posts an ongoing notification with Stop, holds the process-wide exclusive
microphone lease, and uses Android `MediaRecorder` to encode mono AAC in an M4A
container at 44.1 kHz / 96 kbit/s. Pause/resume use `MediaRecorder.pause/resume`.
Markers use monotonic active time, so paused time is excluded.

The tuner takes a non-blocking lease; while the recorder owns the mic it presents a
clear busy state. Navigating away from the recorder does not stop its service. Start
is requested from the visible Activity to satisfy microphone FGS start restrictions.

## Finalization and recovery

Stop closes the encoder, verifies an audio track with `MediaExtractor`, records final
duration/size, decodes AAC off the main thread with `MediaCodec` into 256 peaks,
atomically promotes the private file and only then marks the row `READY`. A crash or
failed stop leaves a `.partial` file and recoverable metadata. Startup reconciliation
keeps partials, promotes verified final files, marks missing audio explicitly and
only adopts old UUID-named files within Anarkey's own directory. It never deletes
unknown files.

Deletion persists `DELETING`, removes owned files, then removes metadata. A failed
file delete leaves that state for retry. Sharing uses `FileProvider`; export uses the
Storage Access Framework.

## Scope and validation

Playback is local Media3 ExoPlayer with play/pause/stop/seek. Variable speed, A–B
looping, trim, songs, chords, metronome, and tuner DSP changes are outside M2.

The device matrix and exact results are reported in the M2 delivery in this thread;
the known M0 decay behavior remains open.
