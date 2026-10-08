# Milestone 1 validation report

> Historical prototype report. For toolbox M1 see [M1_IMPLEMENTATION.md](M1_IMPLEMENTATION.md).
> A later test with a real guitar gave a partial acceptance: decay/partial dominance is
> still unresolved, so M0 remains open. Toolbox M1 does not touch that DSP and went ahead.

## Diagnostic history addition

Added a temporary panel with the newest 30 valid stabilized detections, sampled
no more frequently than every 250ms using monotonic elapsed time. Entries contain
local wall-clock timestamp with milliseconds, stabilized note/octave and frequency,
raw frequency, cents and clarity. History is memory-only and separate from capture
state; Clear also resets throttling so the next valid frame is immediately eligible.

Verified with `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: build
successful, five new history tests passed, Lint 0 errors and the same five dependency
update warnings. Tests cover the 250ms boundary, invalid-frame rejection, retaining
the newest 30 snapshots, clear/reacquisition and wall-clock changes. Core DSP is
unchanged. Live populated-panel and acoustic testing remain manual acceptance items.

## Brief signal dropouts

When confidence or input briefly drops, the engine keeps the last stable note
visible for up to 125ms and labels it `HOLDING`. Clarity, raw frequency and input
level continue to reflect the current frame, keeping suspect readings diagnosable.
Held frames are not added as new stable history entries. Longer dropouts clear the
note; reacquisition still requires three coherent estimates. This improves display
continuity without changing YIN or its confidence threshold.

Debug builds emit `AnarkeyTuner` Logcat pitch samples at most every 250ms, matching
the history cadence. Hold start/end and signal state changes are logged immediately.
Each event includes current raw frequency, clarity and input level. No audio samples
are logged. Inspect with `adb logcat -s AnarkeyTuner:I`; individual analysis frames
are not logged.

## Input-level hysteresis

The device trace showed `WEAK`/`UNCERTAIN` toggling around −60 dBFS. Input gating
now turns on at −60 dBFS and stays on until the level falls to −62 dBFS. This
2dB release gap prevents chatter around the threshold without reducing the
activation sensitivity. Below −62 dBFS, existing brief-hold behavior still applies.

Date: 2026-10-04. Implementation complete for prototype testing; **physical-guitar
acceptance is pending**. No later milestone implemented.

## Automated results

Command: `gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain`

* Build: successful, debug APK generated with SDK36/JDK17.
* JVM tests: **1094 passed, 0 failures, 0 errors**.
  * SyntheticPitchTest: 1080 parameterized cases, 4.864s aggregate.
  * DetectorRobustnessTest: 5 tests including 42 noisy scenarios, 1.827s aggregate.
  * EngineTest: 9 tests, 0.156s aggregate.
* Clean six-string signals: <=2-cent error, correct note/octave, ±15-cent offsets.
* Noisy signals (including E1): <=5-cent error. Seeded uniform noise amplitude
  is ±5% of the configured signal amplitude, not a universal acoustic SNR claim.
* Strong second/third harmonic profiles include fundamental/harmonic ratio 0.15:1.
* Silence, DC, seeded noise-only input, invalid samples, clipping and weak input
  handled as specified; a pure second harmonic is not invented as a lower note.
* Engine checks cover coherent acquisition, median, confidence loss, changing
  strings, silence reset, note hysteresis, levels and tuning thresholds.
* Lint: 0 errors; 5 dependency-update warnings. Older pinned stable dependencies
  are intentional for this SDK36/JDK17 prototype. No lint baseline/suppression hides
  correctness or localization warnings.

Reports: `core/build/reports/tests/test/index.html`,
`app/build/reports/lint-results-debug.html`. Build outputs are ignored by Git.

The first wrapper-regeneration invocation completed Gradle tasks successfully but
the running Windows batch wrapper exited with an error after replacing itself.
A fresh invocation of the regenerated wrapper then completed with exit code 0.

## Performance observations

Desktop JVM detector benchmark: 30 warmups, 100 timed 4096-sample windows at 48kHz;
median **5.3106ms**, p95 **15.6692ms**. Taken during other build work, without CPU
isolation. This is a development diagnostic, not an Android performance guarantee.
No CPU/battery, GC profile, end-to-end latency or microphone quality measurements
have been completed on Android. UI exposes per-window processing time for testing.

The direct difference implementation is O(window × maximum lag), about 2.8 million
sample comparisons/window at 48kHz. If device p95 exceeds the 21.33ms hop, capture
may accumulate latency. Measure before changing hop, decimating with an anti-alias
filter, or adopting an FFT implementation. Array storage is reused; immutable state
objects and Compose updates still allocate.

## Physical device smoke test

Connected phone: Samsung SM-G9650, Android10. Debug APK installation succeeded;
`am start -W` reported successful cold Activity launch (2117ms in the first run).
This startup measurement includes debug/device overhead and is not pitch latency.
Application UI hierarchy and a screenshot were inspected (Spanish, dark theme;
permission explanation and controls readable). RECORD_AUDIO remained **not granted**;
no microphone/audio test is claimed. No Internet permission requested.

## Known limitations / remaining acceptance

* All six real guitar strings, normal environmental noise, attack/decay, detuned
  strings and octave stability require user testing.
* Permission grant/deny/permanent deny, microphone privacy controls, background/
  foreground, rotation, repeated start/stop, unavailable microphone and airplane
  mode need full device validation. Installation/launch does not validate these.
* Harmonic refinement is heuristic; missing fundamentals, chords, inharmonicity,
  mains hum and extreme harmonic dominance remain difficult. No forced guitar notes.
* Instant clearing on low confidence can flicker during decay. Raw diagnostics
  remain visible for diagnosis; do not hide detector errors with smoothing.
* Range excludes B0/five-string bass. No instrument-specific claims yet.
* Prototype UI needs TalkBack, enlarged font and EN/ES light/dark hardware review.
* The original YIN PDF was unavailable from attempted academic hosts. The bibliographic
  reference and code-to-code comparison are recorded in RESEARCH.md; full paper
  review remains outstanding, rather than falsely claimed complete.

Next step: follow [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) using a real
guitar. Report wrong raw octaves separately from presentation lag. Only then decide
whether to accept Milestone1 and authorize Milestone2. No product decision is
required to run this prototype; confirm final app ID, Android support floor and
copyright attribution before public release.
