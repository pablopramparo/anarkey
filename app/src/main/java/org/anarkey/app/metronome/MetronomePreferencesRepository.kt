package org.anarkey.app.metronome

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.anarkey.core.metronome.*

private val Context.metronomeDataStore by preferencesDataStore(name = "metronome_preferences")

data class MetronomePreferences(
    val settings: MetronomeSettings = MetronomeSettings(),
    val keepScreenOn: Boolean = false,
)

class MetronomePreferencesRepository(context: Context) {
    private val store = context.applicationContext.metronomeDataStore

    val preferences = store.data.map { values ->
        val meter = runCatching { Meter.valueOf(values[METER] ?: Meter.FOUR_FOUR.name) }.getOrDefault(Meter.FOUR_FOUR)
        val sound = runCatching { ClickSound.valueOf(values[SOUND] ?: ClickSound.WOOD.name) }.getOrDefault(ClickSound.WOOD)
        val bpm = (values[BPM] ?: 100).coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM)
        val volume = (values[VOLUME] ?: 0.75f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.75f
        val accentPatterns = Meter.entries.mapNotNull { patternMeter ->
            val encoded = values[patternKey(patternMeter)] ?: return@mapNotNull null
            val pattern = encoded.split(',').mapNotNull { runCatching { Accent.valueOf(it) }.getOrNull() }
            if (pattern.size == patternMeter.clicksPerBar) patternMeter to pattern else null
        }.toMap()
        MetronomePreferences(
            MetronomeSettings(bpm, meter, values[PRIMARY] ?: true, values[SECONDARY] ?: true, sound, volume, accentPatterns),
            values[KEEP_SCREEN_ON] ?: false,
        )
    }

    suspend fun save(value: MetronomePreferences) {
        store.edit { values ->
            values[BPM] = value.settings.bpm.coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM)
            values[METER] = value.settings.meter.name
            values[PRIMARY] = value.settings.primaryAccent
            values[SECONDARY] = value.settings.secondaryAccent
            values[SOUND] = value.settings.sound.name
            values[VOLUME] = value.settings.volume.coerceIn(0f, 1f)
            values[KEEP_SCREEN_ON] = value.keepScreenOn
            value.settings.accentPatterns.forEach { (meter, pattern) ->
                if (pattern.size == meter.clicksPerBar) values[patternKey(meter)] = pattern.joinToString(",") { it.name }
            }
        }
    }

    private companion object {
        val BPM = intPreferencesKey("bpm")
        val METER = stringPreferencesKey("meter")
        val PRIMARY = booleanPreferencesKey("primary_accent")
        val SECONDARY = booleanPreferencesKey("secondary_accent")
        val SOUND = stringPreferencesKey("click_sound")
        val VOLUME = floatPreferencesKey("volume")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PATTERN_TWO_FOUR = stringPreferencesKey("accent_pattern_2_4")
        val PATTERN_THREE_FOUR = stringPreferencesKey("accent_pattern_3_4")
        val PATTERN_FOUR_FOUR = stringPreferencesKey("accent_pattern_4_4")
        val PATTERN_SIX_EIGHT = stringPreferencesKey("accent_pattern_6_8")

        fun patternKey(meter: Meter) = when (meter) {
            Meter.TWO_FOUR -> PATTERN_TWO_FOUR
            Meter.THREE_FOUR -> PATTERN_THREE_FOUR
            Meter.FOUR_FOUR -> PATTERN_FOUR_FOUR
            Meter.SIX_EIGHT -> PATTERN_SIX_EIGHT
        }
    }
}
