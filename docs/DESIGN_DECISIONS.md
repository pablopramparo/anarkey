# Design decisions

Each entry states what was decided and why. Status: **in place**, **planned** or
**superseded** (replaced by a later decision, which is linked). Implementation notes per
milestone live in the `M*_IMPLEMENTATION.md` files; provenance and DSP research in
[RESEARCH.md](RESEARCH.md); test results in [VALIDATION.md](VALIDATION.md).

## 1. Product

| Decision | Why | Status |
|---|---|---|
| Free, open source (MIT), no ads, subscriptions, purchases, accounts, analytics or tracking. | A tuner is a small utility; none of those add value to tuning, and each one adds a permission, a dependency or a reason to distrust the app. | in place |
| No backend, cloud sync, social features, lessons, games, streaks or notifications. | They do not help someone tune or practise, and they would require the data collection the app avoids. A feature is questioned if it does not help someone use their instrument. | in place |
| Microphone audio is processed on the device, never uploaded, and never stored unless a recording is made on purpose. Only `RECORD_AUDIO` (plus the foreground-service and notification permissions needed for recording and the metronome) is requested. No Internet permission. | Audio from a microphone is sensitive, and the app works offline, so there is no reason to send it anywhere. | in place |
| Diagnostic audio export is manual, local and limited to debug builds. | Without it, acoustic problems cannot be reproduced, but nothing should leave the device without a deliberate action. | in place |
| Support links (GitHub Sponsors) appear only in the README, never in the app. The Settings screen has credits and licences but no external links. | Google Play rejects apps that lead users to a payment method other than Play billing, and has done so for open-source apps whose links went to GitHub Sponsors, Liberapay or Open Collective, even indirectly. A donation link in the app could cost the Play listing; one in the README cannot. | in place |
| The privacy policy is a static bilingual page in `docs/privacy/`, served by GitHub Pages, and linked from Settings → Credits. It is the only link that leaves the app. | Google Play requires a privacy policy link in the store listing and inside the app for apps that use the microphone. A separate static page avoids pointing the app at the repository page, which shows the Sponsor button that Play rejects in apps. Pages needs no extra service or account. | in place |
| Store listing graphics, screenshots and texts live in `store/`. | They are published with the code so contributors can update them when the interface changes. | in place |
| Scope grew from a tuner into a small practice toolbox: recorder, songs with chords, chord dictionary, metronome. | These tools share the same musical vocabulary (notes, tunings, chords) and the same audio rules, and a musician uses them together. Each tool still has to justify itself against the first rule above. See [TOOLBOX_PROPOSAL.md](TOOLBOX_PROPOSAL.md). | in place |

## 2. Architecture

| Decision | Why | Status |
|---|---|---|
| Kotlin, Jetpack Compose, Android-native APIs, few dependencies. | Fewer dependencies means less licensing risk and less to maintain. | in place |
| `core/` is a pure Kotlin module (pitch detection, music math, validation, stabilization); `app/` owns `AudioRecord`, lifecycle and UI. | The DSP and music logic must be testable without a device or a microphone, and must not depend on Compose. | in place |
| Small, direct state flow: capture → detector → engine → `StateFlow` → `ViewModel` → Compose. No repositories, use-cases or managers without a reason. | The app is intentionally small; extra layers cost readability and add no behaviour. | in place |
| Audio work never runs on the UI thread; realtime loops reuse buffers; capture is released when the tuner is not shown. | Avoids dropped audio, battery drain and a microphone that stays on unexpectedly. | in place |
| One microphone owner at a time (tuner or recorder). | The tuner and the recorder cannot both hold the input; an explicit owner avoids ambiguous states. | in place |
| Room for structured data, files for audio, DataStore for preferences. Schemas are exported and migrations are tested; destructive migrations are never used. | Songs and recordings are the person's data; losing them to a schema change is not acceptable. | in place |

## 3. Reuse of existing code

| Decision | Why | Status |
|---|---|---|
| The YIN pitch detector is adapted from Tunify (MIT), with its notices preserved in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). | YIN is a solved problem; reimplementing it for its own sake adds risk. The licence was verified and compatible with MIT. | in place |
| Only the algorithmic foundation was reused, not Tunify's UI, product design or structure. Orchestration, stabilization, instrument model, UX and localisation are Anarkey's own. | The goal is a different product; copying another app's design is neither original nor necessary. | in place |
| YIN stays the detector; McLeod is kept only as a debug-build comparison. More algorithms are not added unless tests show a practical gain. | Complexity has to be paid for by measured benefit. | in place |
| Copyleft (GPL-style) dependencies are not added without discussing them first. | They would change the licence of the whole project. | in place |
| Provenance and licences are recorded when code is adapted, not at release time. | Retrofitting attribution is error-prone. | in place |

## 4. Pitch detection and stabilization

| Decision | Why | Status |
|---|---|---|
| Octave and harmonic errors (2f, 3f) are tested explicitly, including a weak fundamental with a strong second harmonic. | On string instruments the second harmonic can be stronger than the fundamental, so the detector would otherwise report E3 for E2. | in place |
| An instrument's expected note is used as context, never forced when the audio says otherwise. Octave errors are not hidden by forcing a target or by heavy smoothing. | Forcing a target would show a plausible note for a wrong reading and hide real detector problems. | in place |
| The target string is chosen by distance in cents, not in Hz. | Pitch is logarithmic; a linear Hz distance picks the wrong string when neighbouring notes are far apart in Hz or when a string is badly detuned. | in place |
| Low-confidence estimates are not shown as fact; the last stable reading is held briefly, then the display goes back to listening. | Random jumps between notes make a tuner unusable, but holding forever would show a reading for a string that is no longer sounding. | in place |
| Stabilization uses the smallest combination of techniques that behaves well (stable but responsive). | A tuner that reacts slowly is as bad as one that jitters. | in place |
| Tuning-state thresholds (flat, near, in tune, sharp) live in one place and in-tune is a tolerance, not exactly 0 cents. | Magic numbers scattered through the UI drift apart, and exact zero is unreachable on a real string. | in place |
| Input level is reported in dBFS and never presented as acoustic dB SPL. | Phone microphones are not calibrated sound-pressure meters. | in place |
| Synthetic audio tests (sines, harmonics, phases, sample rates, noise) come first; physical-device acceptance is a separate, required step. Observed problems are classified as detector problems or presentation problems. | Synthetic success does not prove acoustic accuracy, and smoothing must not be used to disguise a detector fault. See [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md). | in place |
| Tuner capture high-pass: 60 Hz for guitar-range instruments, 25 Hz when bass is selected. | Bass E1 is about 41 Hz; the guitar cutoff would remove its fundamental. Verified with synthetic bass strings only, not with a physical bass. | in place |

## 5. Music model

| Decision | Why | Status |
|---|---|---|
| Notes are stored as canonical scientific pitch (MIDI); sharps/flats and letter/solfège names are presentation only. | Internal logic must not depend on translations or on enharmonic spelling. | in place |
| Equal temperament, A4 = 440 Hz by default and configurable (400–480 Hz). | Standard practice; some ensembles and instruments use other references. | in place |
| Instruments and tunings are data, with stable ids and localizable names; no code assumes six strings or ascending pitch. | Ukulele (re-entrant tuning), banjo (short fifth string) and violin need different shapes, and new tunings should be data changes. | in place |
| Tuning and chord data are not invented: every chord shape is validated against the notes its tuning produces. | A wrong note in a reference tool is worse than a missing one. | in place |
| The picker accepts both Anglo and Latin chord symbols and respects the notation preference. | The app targets Spanish- and English-speaking players, who write the same chord differently. | in place |

## 6. Information levels and wording

| Decision | Why | Status |
|---|---|---|
| Three levels of information (Guided, Normal, Pro) over one detection engine. | They change how much is shown, not how well the pitch is detected, so they are not difficulty levels. | planned |
| Guided wording says "tighten" or "loosen", never clockwise/anticlockwise. | Which way to turn a peg depends on the instrument; tighten and loosen describe the physical goal. | planned |
| Colour never carries a tuning state alone. | Colour-blind users and bright rooms; state also needs text or shape. | in place |
| Information on the tuner is readable at 0.5–1.5 m. | The phone usually lies on a desk, stand or amplifier while the instrument is played. | in place |

## 7. Interface

| Decision | Why | Status |
|---|---|---|
| Dark theme with orange accents taken from the logo; the logo (without slogan) is the header, the logo with slogan is the splash screen. | Brand consistency. The slogan is too small to read at header size (its letters would be under 4 dp), so it appears where there is room. | in place |
| One corner radius for the whole app (12 dp); only circular buttons are exempt. | Mixed radii made cards, fields and selectors look unrelated. | in place |
| On each screen only one selector has the orange border; the others are neutral. | Three equally loud selectors competed for attention on the chords screen. Selectors are also less tall and wider than before. | in place |
| The app opens on a home screen with a card per tool; the tuner starts listening only when opened. Tapping the logo returns home. | Opening straight into the tuner switched the microphone on at launch and hid the other tools. Supersedes "opening the app immediately prepares for tuning". | in place |
| The play/pause control of the tuner is a large filled circle, and the "allow microphone" button sits at the top of the screen. | Pausing the microphone is the main action of the screen, and a permission request at the bottom was easy to miss. | in place |
| Recording status is never shown as an internal enum; finished recordings show only their duration. Empty states say what to do and offer the first action. | Implementation details are noise for the person using the app. | in place |
| Expandable help links carry a chevron. | Plain text links did not look expandable. | in place |
| Chord diagrams are compact, centred and drawn like printed charts, with a thick line on the first fret shown and the starting fret number beside it. Tapping a diagram plays the chord. | Wide, flat diagrams made strings look too far apart; listening confirms a shape without leaving the screen. | in place |
| User-visible text lives in Android resources, English as the fallback, Spanish included. The Spanish strings currently use voseo. | Translations can be contributed without touching code. | in place |
| Accessibility: content descriptions, 44 dp targets where possible, no colour-only state. | Basic usability. | in place |

## 8. Decisions taken during development

| Decision | Why |
|---|---|
| The installable build keeps the package `org.anarkey.app` and is always signed with the same key; debug builds install separately as `org.anarkey.app.dev` ("Anarkey Dev"). | Android refuses to update an app signed with a different key, so installing a differently signed build forced an uninstall, which erases the app's private storage including recordings. See [AGENTS.md](../AGENTS.md). |
| Release builds are signed with a private key kept in the untracked `signing/` folder and referenced from `signing/keystore.properties`; the repository holds no key or password. | The first local builds used the standard Android debug key (public password), which let anyone sign an update that Android would accept as legitimate. A private key prevents that. Moving an installed app to the new key needs a one-time uninstall, which erases its data, so recordings must be exported first. |
| The metronome keeps playing with the screen off through a foreground service (`mediaPlayback`) with a Stop action and a wake lock. | Stopping when the screen turned off made it useless while playing. It stops when the tool is left, from the button or from the notification. |
| The metronome asks for the notification permission the first time it starts, but plays whether or not it is granted. The recorder asks too and, unlike the metronome, does not start without it. | On Android 13+ the foreground-service notification (with its Stop button) is hidden until the permission is granted, and the person should be able to see and stop what is running in the background. A metronome that refused to play without a notification would be an obstacle for something that works without one; a recording that cannot be seen or stopped from outside the app is a bigger problem. | in place |
| Chords can be played: a plucked-string sound is synthesised from the tuning, the fret and the capo, strummed from the lowest string. | It needs no audio assets and works for every instrument and tuning. The sound is a model of a string, not a recording, and has not been tuned by ear against real instruments. |
| Ukulele, mandolin, banjo and bass chord shapes are generated by `tools/chords/generate_voicings.py` and validated by tests. Guitar shapes stay hand-written. | With four or five strings there are few combinations, and fixed playability rules (up to 4 fingers, 4-fret span, valid barres) are more reliable than writing about 1,600 shapes by hand. This supersedes the earlier rule of not generating diagrams. |
| Violin has a tuner but no chord diagrams. | Violin is not played with fretted chords; diagrams would not be useful. |
| Bass was added to the tuner and the chord tool. | Supersedes the earlier decision not to offer bass, which existed only because of the 60 Hz filter described above. Bass chord shapes require the root as the lowest note and contiguous strings, so a few extended chords (such as 13ths) have none. |
| Toolbox M1 was implemented independently of the open decay issue (M0). | M1 does not touch the YIN or decay DSP, so the two do not block each other; M0 stays open until the physical acceptance for decay passes. |
| Recording continues with the screen locked through a foreground service that is started from the visible activity. | Android restricts starting microphone foreground services from the background. |
| Time signatures: 6/8 counts the BPM in dotted quarters, with the unit shown next to the value. Chords written in a song are relative to the capo, and the key is the sounding key. | Without a unit "60 BPM" is ambiguous in 6/8; separating shape from sound lets a song be written the way it is played and still be transposed. |
| Recorder scope: capture, management, markers, waveform and export are in; playback speed, A–B looping and trimming come later, one at a time. | Smaller deliveries are easier to validate than one large milestone. |

## 9. How decisions are recorded

Prefer small classes, explicit behaviour, tests, few dependencies and data-driven configuration.
Avoid frameworks for their own sake, abstractions without a second user, and clever code that is
hard to debug. When a decision changes, mark the old one as superseded and say why, rather than
deleting it.
