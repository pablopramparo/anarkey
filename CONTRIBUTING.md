# Contributing

Keep Anarkey free, offline and focused on musical practice. No tracking, backend or accounts.
Discuss any dependency with restrictive licensing before adding it.

Use JDK17 and the checked-in Gradle wrapper. Run `:core:test :app:testDebugUnitTest :app:assembleDebug
:app:lintDebug` before submitting changes. Include the physical-device observations
for capture/DSP changes; synthetic success alone does not prove acoustic accuracy.

DSP and musical math belong in the pure Kotlin `core` module. Reuse capture/DSP
buffers. Document numerical tolerances before adapting tests. Do not fix octave
errors by forcing an instrument target or by hiding raw results with smoothing.

All visible text belongs in Android resources. Keep `values/strings.xml` (English
fallback) and `values-es/strings.xml` aligned, including accessibility labels.
Internal notes remain canonical MIDI/scientific pitches, independent of translations.

Record source revision and preserve licenses immediately when adapting code.
See `docs/RESEARCH.md` for the historical prototype scope, `docs/TOOLBOX_PROPOSAL.md`
for the toolbox design and `docs/DESIGN_DECISIONS.md` for what was decided and why.

Physical acceptance of the tuner is partial: M0 remains open for decay variation while
the partial that dominates changes. Keep changes to the YIN and decay DSP separate from
unrelated work, and include device observations when you touch them. When you change
a decision, update `docs/DESIGN_DECISIONS.md` and say why.

The license notices are also bundled in the app: after changing `THIRD_PARTY_NOTICES.md` or `licenses/`,
copy them to `app/src/main/assets/licenses/` so the APK ships the same texts. Implementation notes in
`docs/` are mostly in Spanish and the user-facing documents (README, decisions) in English; either is fine
for contributions.

Release builds are signed with a private key listed in the untracked `signing/keystore.properties`
(see `AGENTS.md`). Without it the release build is unsigned; never commit keys or passwords.

Be kind: see the [code of conduct](CODE_OF_CONDUCT.md). Report security problems privately, as described in [SECURITY.md](SECURITY.md).
