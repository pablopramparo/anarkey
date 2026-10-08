# Notes for contributors and coding agents

Decisions and their reasons are in [docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md). Read it
before changing behaviour, and update it when a decision changes.

## Installing on a phone without losing data

Recordings live in the app's private storage, and Android erases it when the app is uninstalled.
Android forces an uninstall whenever a new APK is signed with a different key. So:

- **Installable build: `release`, package `org.anarkey.app`.** It is always signed with the private
  key listed in `signing/keystore.properties` (the whole `signing/` folder is gitignored), so it
  updates in place and keeps the recordings. Build and install with:

  ```powershell
  .\gradlew.bat :app:assembleRelease
  adb install -r app\build\outputs\apk\release\app-release.apk
  ```

  Never use `adb uninstall`, `pm clear` or `install` without `-r` on `org.anarkey.app`.
- **Testing changes: `debug` build, package `org.anarkey.app.dev`**, labelled "Anarkey Dev". It has
  separate data and installs next to the stable app, so it cannot touch real recordings.
- Do not change `applicationId`, remove the `release` signing config in `app/build.gradle.kts`, or
  regenerate or replace the keystore. A different key means the app must be uninstalled and its
  recordings are lost. If `signing/keystore.properties` is missing the release build is unsigned:
  stop and ask the maintainer rather than creating a new key.
- Bump `versionCode` in `app/build.gradle.kts` for each build meant to replace an older one
  (Android refuses to install a lower or equal `versionCode` with `-r`).
- **Keys:** `signing/anarkey-release.jks` is private. Back it up together with
  `signing/keystore.properties` somewhere outside the repository; without them no update can be
  installed over an existing install. Forks create their own key and `keystore.properties`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); never commit either file.
- A copy of the last stable APK is kept in `dist/` (gitignored).

## Devices

`adb` is at `%ANDROID_HOME%\platform-tools\adb.exe` (it may not be on PATH). Screenshots:
`adb shell screencap -p`. The development device was a Samsung phone; behaviour on other
devices has not been checked.

## Branding

Source images are in `design-assets/` (`anarkey_ico`, `anarkey_iso`, `anarkey_logo`,
`anarkey_logo_slogan`); the app uses resized copies in `app/src/main/res/drawable-nodpi/brand_*.png`.
Launcher and splash drawables use `<inset>` with percentages, not fixed dp, because fixed sizes are
cropped by the system masks. The palette is dark with orange accents (`ui/AnarkeyTheme.kt`) and
the single corner radius is `AppShape`.

## Chords and instruments

- Ukulele, mandolin, banjo and bass chord shapes are generated: run
  `python tools/chords/generate_voicings.py` and never edit `OtherInstrumentVoicingData.kt` by hand.
  Guitar shapes are hand specified.
- The tuner high-pass filter is 60 Hz for guitar and 25 Hz for bass (`AudioCapture`), because bass E1
  is about 41 Hz.
- Earlier decisions (such as not offering bass, or not generating chord shapes) can be revisited when
  there is a better approach. Record the new decision and its cause in
  [docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md) and mark the old one as superseded.
