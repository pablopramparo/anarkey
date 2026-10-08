# Milestone 1 research and decisions

Reviewed 2026-10-04. Scope ends at the technical prototype.

## Tunify audit

Repository: https://github.com/thestbar/tunify

Pinned source: `0a8f8b9ac952a394d9d740b2fb363c09be7acd8a`.
The actual repository LICENSE is MIT, copyright 2023 Stavros Barousis.
Android application and Kotlin source were verified directly, including the
YIN implementation. This audit describes that revision, not older Tunify releases.

Paths below are relative to `app/src/main/java/dev/thestbar/tunify/`.

| Component | Findings | Decision |
| --- | --- | --- |
| `util/algorithms/Yin.kt` | Squared difference, cumulative mean normalization, first threshold crossing at 0.15, local minimum, parabolic interpolation. Instance-owned buffer resized only when needed. No Android dependencies. Quadratic cost; no range or clarity output. Constant input divides zero by zero internally. Very short input can index outside the buffer. | Adapt this small implementation with attribution. Guard input/normalization, bound lag search, expose clarity, compare harmonic candidates. |
| `core/RecordingRunnable.kt` | Coroutine loops over blocking reads; return count/errors ignored, so partial/stale input can reach detector. Posts each result to Main, rounds cents, no temporal stabilization or signal-level checks. Global mutable note selector. | Implement our own single-owner capture and engine. |
| `data/viewmodels/TunerViewModel.kt` | 44.1 kHz, mono PCM16, UNPROCESSED. Internal byte buffer is minimum times four, but the same byte count sizes a ShortArray, doubling sample storage. Device-dependent analysis length, no explicit overlap. Unsupported minimum sizes not rejected before multiplication. | Fixed analysis window/hop, independent capture byte buffer, two rate candidates and source fallback. |
| Lifecycle/threading | Cancellation is requested, then stop/release happens without awaiting the reader; possible concurrent access. Activity lacks foreground stop/restart; ViewModel can remain alive in background. No reader `finally` for exceptional exits. | Lifecycle-owned coroutine, mutex to serialize sessions, worker-owned release in `finally`, nonblocking reads for bounded cancellation polling. |
| `util/algorithms/NoteDetection.kt` | Binary search followed by nearest linear Hz distance; note boundary is arithmetic rather than geometric. Cents formula itself is logarithmic. | Central equal-temperament music math using MIDI integers and cents. No copied note catalog. |
| Tests | `YinTest.kt`: sine at 220/440/880 Hz, silence, separate instances. Other tests cover notes/tunings. No guitar low-E, noise, weak-fundamental or lifecycle coverage in the inspected tests. | Independent acceptance matrix and engine tests. |

Only YIN computation is adapted. UI, themes, instrument data, persistence,
navigation and orchestration are not copied. Preserve Tunify's full MIT text in
`licenses/Tunify-MIT.txt`, root notices and APK assets. The source header identifies
the original revision and modifications. No GPL dependency is included.

## Paper and alternatives

YIN: Alain de Cheveigné and Hideki Kawahara (2002), *YIN, a fundamental frequency
estimator for speech and music*, JASA 111, 1917–1930, DOI 10.1121/1.1458024.
Author URL: https://recherche.ircam.fr/equipes/pcm/cheveign/pss/2002_JASA_YIN.pdf

The original PDF could not be retrieved in this environment (web gateway errors
and TLS trust failure). Therefore a full paper audit is **not claimed**. The
implemented difference/normalization/threshold/interpolation stages were compared
with both inspected code implementations. The prototype does not implement every
paper refinement (including best-local-estimate search), or call itself FastYIN.

* https://github.com/sevagh/pitch-detection — MIT repository. Inspected
  `include/pitch_detection.h`, `src/yin.cpp` and README. Provides FFT-based YIN,
  MPM and probabilistic variants with reusable storage and instrument tests.
  Promising performance reference, but C++/FFT integration and dependency audit
  are unnecessary until Android measurements show a problem. No code reused.
* https://github.com/peterkhayes/pitchfinder/blob/master/src/detectors/yin.ts —
  source embeds an aubio GPL notice. Do not assume a repository-level license
  clears individual source provenance. Rejected; no code reused.

FastYIN/FFT can lower computational cost; MPM uses a normalized correlation peak
criterion. Neither removes physical ambiguity from absent fundamentals, and neither
is introduced without a measured need. A future switch must run the same tests.

## Exact prototype architecture

`AudioRecord → PCM window → TunerEngine → YIN → validation → median → PitchMath`

`AudioFrame → StateFlow<DebugState> → Compose`

* `:core`: pure Kotlin/JVM, detector, level calculation, music math, stabilization.
* `:app`: AudioRecord ownership, Android lifecycle, ViewModel/StateFlow and UI.
* No backend, audio files, Internet permission, analytics or additional framework.
* Android 8/API 26 minimum, compile/target API 36, JDK17, Kotlin2.1.20,
  AGP8.12.2/Gradle8.13. These are pinned stable compatible versions, **not a claim
  to be the newest available**. Existing SDK36/JDK17 tooling favors a conservative
  prototype build over an unrelated AGP9 migration.
  Compatibility: https://developer.android.com/build/releases/agp-8-12-0-release-notes

### Capture and timing

Mono PCM16; probe 48,000 then 44,100 Hz. Do not infer native input rate from
Android's output-rate property. Report the configured recorder rate. Prefer
UNPROCESSED only when advertised; otherwise VOICE_RECOGNITION avoids choosing a
communications source intentionally optimized for calls. Actual processing varies
by manufacturer and needs hardware verification.

Internal capacity: max(2 × minimum byte count, 8192 bytes). Negative/error minimum
results are rejected. Capacity is not the number of samples read or a promise of
latency. Hop 1024 samples, window 4096 samples, overlap 75%. Accumulate partial
reads; process only complete windows. Negative reads/no data for 1.5s fail cleanly.
Reusable PCM, float window and YIN arrays; small immutable result objects per frame.

Window duration: 85.33ms at 48k, 92.88ms at 44.1k. Hop: 21.33/23.22ms.
Three coherent readings add two hops to initial acquisition: theoretical minimum
128/139ms, **before** capture/OS/compute/display overhead. Median tracking adds
approximately one hop for smooth changes. Window includes about 3.5 cycles of E1
and seven cycles of E2 at 48k. Lag search covers 35–1000 Hz; B0 (30.87Hz) excluded.
No downsampling or FFT until a profiling result justifies their extra machinery.

### Validation and stabilization

YIN threshold 0.15; clarity = 1 − normalized difference at selected lag. Accept
clarity >=0.85. These express periodicity, not probability of correct note/octave.
For a first candidate with residual >0.01, inspect local minima near 2× and 3× lag;
prefer one only if residual improves by at least 5×. This is an explicit prototype
heuristic for strong harmonics, not a proof of the fundamental. Never fold into
an expected guitar note. Synthetic tests constrain subharmonic mistakes.

Input gate turns on at −60 dBFS and releases at −62 dBFS (2 dB hysteresis).
Reject clipping (any absolute sample >=0.999). RMS uses
10log10(mean square), equivalent to 20log10(RMS), floor −120 dBFS. No acoustic
SPL claim. No preprocessing filter/window function: YIN compares waveform periods
directly; DC cancels in differences. Nonfinite input is rejected.

Median of three consecutive estimates. A >80-cent jump resets acquisition; a
missing/uncertain estimate immediately clears the displayed pitch. No stale
holding disguised as a current reading. This may flicker during decay and attack;
hardware tests decide whether a short explicitly marked hold is warranted.
Five-cent note-label hysteresis past the normal 50-cent boundary; ±3 cents is
in tune. No claim that these constants are optimal across phones/instruments.

Capture runs on Dispatchers.IO, one coroutine owner, no audio work on Main.
Nonblocking reads poll at 4ms when empty. Cancellation checks after analysis;
stop/release run on the worker. A mutex prevents old/new sessions overlapping
during rotation. Activity STARTED lifecycle scopes capture; stop on background.
Permission is checked on each start; revocation and unavailable input are surfaced.

### Acceptance criteria set before test execution

Clean synthetic signals: absolute frequency error <=2 cents (~0.116% relative),
correct MIDI note/octave, cents error <=2. Noise tests: <=5 cents (~0.289%).
These are substantially smaller than a semitone and keep clean error below the
±3-cent in-tune band; noise tolerance acknowledges random sample perturbations.
They are engineering acceptance targets, not claims about human hearing thresholds.

Matrix: six specified strings × two rates × three amplitudes × five harmonic
profiles × two phases × three offsets (−15/0/+15 cents) = 1080 cases. Additional
seeded noise, E1, silence, DC, clipping, weak input, independent instances,
streaming windows, note changes, outliers, hysteresis and musical math tests.
Desktop timing is diagnostic only. Device CPU/latency/battery remain unmeasured
until running on actual audio input.

## Risks and challenged assumptions

* No monophonic algorithm guarantees a missing fundamental or identifies chords.
* Minimum capture buffer is not an appropriate analysis-window specification.
* An advertised unprocessed source does not establish microphone accuracy.
* White-noise tests do not replace rooms, attacks, inharmonic strings or interference.
* A fan/mains tone is periodic and may legitimately pass YIN's clarity check.
* A severely detuned string cannot always be assigned automatically. Deferred
  instrument mode will need ambiguity handling rather than forced targets.
* Final guided actions, instruments and settings would distract from this milestone.

No additional product decision blocks the prototype. Public application ID,
minimum Android version, final license ownership/name and measured acceptance
thresholds should be confirmed before publishing, not expanded into UI now.
