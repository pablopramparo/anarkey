package org.anarkey.app.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.anarkey.app.BuildConfig
import org.anarkey.app.R
import org.anarkey.app.ui.AppShape
import org.anarkey.app.ui.Muted
import org.anarkey.app.ui.label
import org.anarkey.app.ui.SelectorButton
import org.anarkey.core.music.*

@Composable
fun SettingsScreen(preferences: TunerPreferences, setA4: (Double) -> Unit, setNaming: (NoteNaming) -> Unit,
    appLanguage: String, setAppLanguage: (String) -> Unit) {
    var a4Text by rememberSaveable(preferences.configuration.a4Hz) { mutableStateOf(preferences.configuration.a4Hz.toString()) }
    var languageMenu by remember { mutableStateOf(false) }
    val a4 = a4Text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 400.0..480.0 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.titleLarge)
        Box {
            val languageLabel = when (appLanguage) {
                "es" -> stringResource(R.string.app_language_spanish)
                "en" -> stringResource(R.string.app_language_english)
                else -> stringResource(R.string.app_language_system)
            }
            SelectorButton(languageLabel, onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth(), primary = true)
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.app_language_system)) }, onClick = { languageMenu = false; setAppLanguage("system") })
                DropdownMenuItem(text = { Text(stringResource(R.string.app_language_spanish)) }, onClick = { languageMenu = false; setAppLanguage("es") })
                DropdownMenuItem(text = { Text(stringResource(R.string.app_language_english)) }, onClick = { languageMenu = false; setAppLanguage("en") })
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.reference_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = a4Text, onValueChange = { a4Text = it.take(12) }, singleLine = true,
            label = { Text(stringResource(R.string.reference_input)) },
            supportingText = { Text(stringResource(R.string.reference_range)) },
            isError = a4 == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = a4 != null && a4 != preferences.configuration.a4Hz, shape = AppShape,
                onClick = { a4?.let(setA4) }) { Text(stringResource(R.string.apply)) }
            TextButton(onClick = { a4Text = "440.0"; setA4(440.0) }) { Text(stringResource(R.string.reset_reference)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.note_naming_title), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.selectableGroup()) {
            NoteNaming.entries.forEach { naming ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                    selected = preferences.noteNaming == naming, role = Role.RadioButton, onClick = { setNaming(naming) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = preferences.noteNaming == naming, onClick = null)
                    Text(stringResource(naming.label()), Modifier.padding(start = 12.dp))
                }
            }
        }
        Text(stringResource(R.string.preferences_local), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        // Credits and the privacy policy only: no donation links here, because Google Play rejects in-app links to payments.
        // Google Play requires the privacy policy to be reachable from inside the app.
        Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.credits_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = Muted)
        listOf(R.string.credits_license, R.string.credits_yin, R.string.credits_libraries, R.string.credits_notices).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
        val uriHandler = LocalUriHandler.current
        val privacyUrl = stringResource(R.string.privacy_policy_url)
        TextButton(onClick = { runCatching { uriHandler.openUri(privacyUrl) } }) {
            Text(stringResource(R.string.credits_privacy))
        }
    }
}
