# Test recordings

Real guitar recordings used by the replay tests in `core/src/test/kotlin` (`DecayTrackingTest` and
related). They exist because synthetic signals do not reproduce how a real string decays and which
partial dominates, so the detector's behaviour on them has to be checked against real audio.

All files are mono, 48 kHz, 16-bit PCM, recorded with a phone microphone. They contain only guitar
sound: the maintainer checked that none has speech. Files prefixed `s21-` were recorded on a Samsung
Galaxy S21.

| File | Length | Content |
|---|---|---|
| `guitar-open-strings-capture.wav` | 30.0 s | Open strings of a guitar in standard tuning |
| `guitar-three-strings-capture.wav` | 4.5 s | Three strings |
| `s21-a2-short-pluck-capture.wav` | 1.7 s | A short, hard pluck of the open A string, slightly flat |
| `s21-e2-damped-capture.wav` | 1.8 s | Open E string stopped with the hand |
| `s21-e2-decay-capture.wav` | 3.7 s | Open E string left to decay to the noise floor |

They are released with the project under its licence (MIT). Add new recordings only if they contain
no speech or other identifiable sound, and describe them in this table.
