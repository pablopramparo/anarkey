# Third-party notices

## Tunify — MIT

`core/src/main/kotlin/org/anarkey/core/pitch/YinPitchDetector.kt` adapts
`app/src/main/java/dev/thestbar/tunify/util/algorithms/Yin.kt` from
https://github.com/thestbar/tunify at revision
`0a8f8b9ac952a394d9d740b2fb363c09be7acd8a`.

Copyright (c) 2023 Stavros Barousis.

Full license: [licenses/Tunify-MIT.txt](licenses/Tunify-MIT.txt). Included in
the APK under `assets/licenses/Tunify-MIT.txt`. Modifications and audit are in
[docs/RESEARCH.md](docs/RESEARCH.md).

## Gradle wrapper — Apache-2.0

Wrapper scripts and JAR originate from Gradle (https://github.com/gradle/gradle).
Bootstrap copies were obtained through the inspected Tunify repository, then
regenerated using the official Gradle 8.13 wrapper task.
Copyright notices remain in scripts. Apache license is in
[licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

## Build/runtime dependencies

Kotlin, kotlinx.coroutines and AndroidX/Jetpack components are Apache-2.0 licensed.
JUnit4 is EPL-1.0 and test-only (not shipped in the application). Its transitive
Hamcrest matcher dependency is BSD-3-Clause and test-only. No GPL DSP package or
code from pitchfinder/aubio is used. Keep notices when redistributing dependencies;
perform the full release artifact license audit before public distribution.

## Toolbox M1 dependencies (2026-10-06)

New direct dependencies: AndroidX Navigation Compose **2.9.0** and Preferences
DataStore **1.1.7**, Apache-2.0. Versions remain compatible with the existing
Kotlin 2.1.20 / Compose BOM 2025.04.01 toolchain; no blanket upgrade was performed.
Verified the actual [AndroidX LICENSE](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)
and resolved Maven POM licenses.

New transitive families include Okio **3.4.0** ([Apache-2.0 LICENSE](https://github.com/square/okio/blob/parent-3.4.0/LICENSE.txt)),
kotlinx.serialization core **1.7.3** ([Apache-2.0 LICENSE](https://github.com/Kotlin/kotlinx.serialization/blob/v1.7.3/LICENSE.txt)),
and DataStore's repackaged protobuf **1.1.7** (artifact version), **BSD-3-Clause**.
The latter is explicitly declared BSD in its published POM and the AndroidX
[repackaging project](https://github.com/androidx/androidx/blob/androidx-main/datastore/datastore-preferences-external-protobuf/build.gradle).
Its upstream [license text](https://github.com/protocolbuffers/protobuf/blob/v3.25.5/LICENSE)
is preserved in [licenses/Protobuf-BSD-3-Clause.txt](licenses/Protobuf-BSD-3-Clause.txt)
and APK assets. Apache-2.0 is already included in both locations. These packages
do not introduce networking or analytics to Anarkey. No Room, Media3, KSP or NDK
dependency was added in M1.

## Toolbox M2 dependencies (2026-10-06)

Room **2.8.5** (runtime, KTX and compiler) and Media3 ExoPlayer/Common **1.11.1**
are Apache-2.0. AndroidX Test runner **1.6.2** and ext-junit **1.2.1** are
Apache-2.0 and test-only. Audio capture and waveform decoding use Android's
platform MediaRecorder/MediaCodec APIs. The Apache-2.0 text is included above
and in the APK assets. No network permission or network feature was added.
