# Google Play listing

Assets and texts for the Play Console listing. Everything here is safe to publish. Update the screenshots
when the interface changes (see "How the screenshots were taken").

## Links

- Privacy policy (English): https://pablopramparo.github.io/anarkey/privacy/
- Privacy policy (Spanish): https://pablopramparo.github.io/anarkey/privacy/es/

The link inside the app (Settings → Credits) points to the version in the app's language. Both pages are
in `docs/privacy/` and are served by GitHub Pages from the `docs/` folder of the main branch.

## Graphics

| File | Use | Spec |
|---|---|---|
| `graphics/icon-512.png` | App icon | 512×512, 32-bit PNG |
| `graphics/feature-graphic-1024x500.png` | Feature graphic | 1024×500, 24-bit PNG, no alpha |
| `screenshots/es/*.png`, `screenshots/en/*.png` | Phone screenshots | 1080×2160 (2:1), 5 per language |

## Texts (draft)

### English (default)

Short description (73/80):

> Free music tools: tuner, recorder, chords and metronome. No ads, offline.

Full description (1115/4000):

```
Anarkey is a free, open-source set of tools for musicians. No ads, no accounts, no tracking, and it works offline: it does not even ask for Internet permission.

Tuner
• Guitar, bass, ukulele, violin, mandolin and banjo, plus a chromatic mode.
• Shows the note, the cents and the string you are tuning. Reference pitch from 400 to 480 Hz.
• Note names as letters (C D E) or solfège (Do Re Mi), with sharps or flats.

Chords
• Diagrams for guitar, ukulele, mandolin, banjo and bass, in beginner or advanced mode.
• Tap a diagram to hear the chord. Save your favourite shapes. Left-handed view.

Songs
• Keep your songs with chords, notes and recordings in one place, and transpose them.

Recorder
• Record your ideas and rehearsals, even with the screen off. Add markers and group takes in sessions.

Metronome
• Tempo, time signatures (2/4, 3/4, 4/4, 6/8), accents and sounds. It keeps playing with the screen off.

Private by design
Audio is processed on your device and never uploaded. Your recordings and songs stay on your phone.

Anarkey is an early version. The source code is public, and feedback is welcome.
```

### Español

Descripción corta (71/80):

> Herramientas musicales gratis: afinador, grabador, acordes y metrónomo.

Descripción completa (1214/4000):

```
Anarkey es un conjunto gratuito y de código abierto de herramientas para músicos. Sin anuncios, sin cuentas, sin seguimiento y funciona sin conexión: ni siquiera pide el permiso de Internet.

Afinador
• Guitarra, bajo, ukelele, violín, mandolina y banjo, más un modo cromático.
• Muestra la nota, los cents y la cuerda que estás afinando. Frecuencia de referencia de 400 a 480 Hz.
• Nombres de notas en letras (C D E) o solfeo (Do Re Mi), con sostenidos o bemoles.

Acordes
• Diagramas para guitarra, ukelele, mandolina, banjo y bajo, en modo principiante o avanzado.
• Tocá un diagrama para escuchar el acorde. Guardá tus digitaciones favoritas. Vista para zurdos.

Canciones
• Guardá tus canciones con acordes, notas y grabaciones en un solo lugar, y transportalas.

Grabador
• Grabá tus ideas y ensayos, incluso con la pantalla apagada. Agregá marcadores y agrupá tomas en sesiones.

Metrónomo
• Tempo, compases (2/4, 3/4, 4/4, 6/8), acentos y sonidos. Sigue sonando con la pantalla apagada.

Privada por diseño
El audio se procesa en tu dispositivo y nunca se sube. Tus grabaciones y canciones quedan en tu teléfono.

Anarkey es una versión temprana. El código fuente es público y se agradecen los comentarios.
```

## Foreground service declarations (Play Console → App content)

Google asks for a description, the impact if the task is interrupted, and a link to a video for each
foreground service type. The videos are not stored in the repository; they are 1080x2400 screen recordings of
the release build. Upload them (for example as unlisted videos) and paste the links.

### Microphone (`RecordingService`, type `microphone`)

- **What the feature does:** Anarkey's audio recorder. When the user taps Record, the app captures audio from the
  microphone and keeps recording if the user switches to another tool, opens the notification shade or locks the
  screen. A persistent notification ("Anarkey está grabando") with a Stop action is shown for the whole recording,
  and Android shows its microphone indicator.
- **Impact if deferred or interrupted:** The recording stops and the rest of the take is lost. Rehearsals and ideas
  are often several minutes long and are recorded with the phone on a stand or in a pocket, so the user would only
  notice later.
- **Video steps:** Home → Recorder → Record → switch to Chords while recording → open the notification shade →
  expand the notification → Stop → the recording appears saved in the Recorder.

### Media playback (`MetronomeService`, type `mediaPlayback`)

- **What the feature does:** The metronome. It generates click sounds and keeps playing when the user switches app
  or turns the screen off, with a notification ("Metrónomo sonando") that has a Stop action.
- **Impact if deferred or interrupted:** The clicks stop, so a musician practising with the phone on a music stand
  or in a pocket loses the tempo reference in the middle of the exercise.
- **Video steps:** Home → Metronome → Start → open the notification shade → expand the notification → Stop.

## How the screenshots were taken

Google rejects screenshots whose long side is more than twice the short one, and the test phone is 20:9.
The phone display was set to 1080×2160 with a density of 400 so every screen fits, the status bar was
cleaned, and the display settings were restored afterwards. The tuner screenshot shows the listening state:
a detected note needs a real instrument.
