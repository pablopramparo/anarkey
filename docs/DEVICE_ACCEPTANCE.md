# Physical-device acceptance — pending hardware test

Milestone 1 is not accepted until a real guitar passes this checklist.
Do not infer acoustic success from synthetic tests, an emulator or APK installation.

Record device/model, Android version, build, source, sample rate, room, microphone
distance and instrument. Use a trusted independent tuner/reference for comparison.

| Test | Expected | Observed |
| --- | --- | --- |
| E2 82.4069 Hz | Correct note/octave, stable result | Pending |
| A2 110.0000 Hz | Correct note/octave, stable result | Pending |
| D3 146.8324 Hz | Correct note/octave, stable result | Pending |
| G3 195.9977 Hz | Correct note/octave, stable result | Pending |
| B3 246.9417 Hz | Correct note/octave, stable result | Pending |
| E4 329.6276 Hz | Correct note/octave, stable result | Pending |
| Each string ±15 cents | Correct direction, responsive cents | Pending |
| Attack, decay, stronger harmonics | No persistent octave jumps | Pending |
| Quiet and normal room noise | No persistent random notes | Pending |
| Weak/overloaded input | Understandable state; no confident false reading | Pending |
| Permission deny, deny permanently, settings grant | Explanation and recovery | Pending |
| Revoke permission/system microphone privacy switch | No crash/stale stable pitch | Pending |
| Another app using microphone | Error or listening; never stale capture | Pending |
| Home/lock/return, ten repetitions | Mic indicator clears, capture restarts | Pending |
| Rotate during capture, ten repetitions | Single capture session, no crash | Pending |
| Start/stop, twenty repetitions | Release/restart consistently | Pending |
| EN/ES, dark/light, large font, TalkBack | Readable and operable | Pending |
| Airplane mode | Full tuning works | Pending |
| Sustained ten-minute capture | No backlog, excessive heat or stalls | Pending |

Starting goals: useful note within 250ms after a settled pluck, analysis p95 below
the 21–23ms hop, stable clean reading within roughly ±3 cents once string settles.
Measure audio-to-screen latency externally (recording/video/reference) rather than
equating the analysis-time label with latency. Log p50/p95, glitches and battery
observations. Goals may be revised with evidence, not to hide a failing detector.

For each failure record raw frequency, stable frequency, clarity, dBFS and compute
time. Wrong **raw** octave means detector/input issue. Correct raw frequency with
lagging display means stabilization/presentation issue. Avoid smoothing away the
former. Never save microphone audio without explicit user consent.
