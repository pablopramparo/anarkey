package org.anarkey.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import org.anarkey.app.R
import org.anarkey.app.settings.SettingsScreen
import org.anarkey.app.songs.SongLibraryScreen
import org.anarkey.app.songs.SongDetailScreen
import org.anarkey.app.songs.SongViewModel
import org.anarkey.app.chords.ChordFavoriteViewModel
import org.anarkey.app.chords.ChordDictionaryScreenV42
import org.anarkey.app.metronome.MetronomeScreen
import org.anarkey.app.metronome.MetronomeViewModel
import org.anarkey.app.ui.*
import org.anarkey.core.music.*

// Material Icons has no metronome glyph, so the tab uses this one.
private val MetronomeIcon: ImageVector = ImageVector.Builder("Metronome", 24.dp, 24.dp, 24f, 24f).apply {
    val ink = SolidColor(Color.Black)
    path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.5f, 3f); lineTo(14.5f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        moveTo(6.3f, 16f); lineTo(17.7f, 16f)
        moveTo(12f, 16f); lineTo(18.5f, 5f)
    }
    path(fill = ink) {
        moveTo(17.4f, 9.3f); arcToRelative(1.9f, 1.9f, 0f, true, true, -3.8f, 0f); arcToRelative(1.9f, 1.9f, 0f, true, true, 3.8f, 0f); close()
    }
}.build()

/** Landing screen: the app opens here and every tool is one tap away. */
private const val StartRoute = "home"

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    val descriptions = mapOf(
        TunerRoute.Home to R.string.home_tuner, "recorder" to R.string.home_recorder, "songs" to R.string.home_songs,
        "chords" to R.string.home_chords, "metronome" to R.string.home_metronome,
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(tools) { tool ->
                Card(
                    onClick = { onOpen(tool.route) }, shape = AppShape,
                    colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(tool.icon, null, Modifier.size(32.dp), tint = NeonSoft)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(descriptions.getValue(tool.route)), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
        // The slogan is part of the brand, so it is not translated.
        Text("FREE THE MUSIC", color = Neon, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 3.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
    }
}

// A miniature chord diagram: four strings, a thick nut and three fingered notes.
private val ChordsIcon: ImageVector = ImageVector.Builder("Chords", 24.dp, 24.dp, 24f, 24f).apply {
    val ink = SolidColor(Color.Black)
    path(stroke = ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt) {
        for (x in listOf(6f, 12f, 18f)) { moveTo(x, 4f); lineTo(x, 21f) }
        for (y in listOf(9.7f, 15.3f, 21f)) { moveTo(6f, y); lineTo(18f, y) }
    }
    path(stroke = ink, strokeLineWidth = 3f) { moveTo(5.2f, 3.5f); lineTo(18.8f, 3.5f) }
    path(fill = ink) {
        for ((cx, cy) in listOf(12f to 6.9f, 18f to 12.5f, 6f to 18.2f)) {
            moveTo(cx + 2.7f, cy); arcToRelative(2.7f, 2.7f, 0f, true, true, -5.4f, 0f); arcToRelative(2.7f, 2.7f, 0f, true, true, 5.4f, 0f); close()
        }
    }
}.build()

private data class Tool(val route: String, val title: Int, val icon: ImageVector, val description: Int)
private val tools = listOf(
    Tool(TunerRoute.Home, R.string.tuner_title, Icons.Default.Tune, R.string.tuner_title),
    Tool("recorder", R.string.recorder_title, Icons.Default.Mic, R.string.recorder_description),
    Tool("songs", R.string.songs_title, Icons.Default.MusicNote, R.string.songs_description),
    Tool("chords", R.string.chords_title, ChordsIcon, R.string.chords_description),
    Tool("metronome", R.string.metronome_title, MetronomeIcon, R.string.metronome_description),
)

/** Future Song actions push this route; popBackStack returns to the exact caller. */
fun NavHostController.openTuner(configuration: TunerConfiguration) {
    navigate(TunerRoute.contextual(configuration))
}

@Composable
fun AppShell(
    preferences: TunerPreferences,
    setA4: (Double) -> Unit,
    setNaming: (NoteNaming) -> Unit,
    setChordPresentationMode: (ChordPresentationMode) -> Unit,
    appLanguage: String,
    setAppLanguage: (String) -> Unit,
    recorder: @Composable (recordingId: String?, onDismissInitial: () -> Unit) -> Unit,
    songModel: SongViewModel,
    chordModel: ChordFavoriteViewModel,
    metronomeModel: MetronomeViewModel,
    startSongRecording: (String) -> Unit,
    tuner: @Composable (TunerConfiguration?) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val secondary = route == "settings" || route == TunerRoute.Context || route?.startsWith("song/") == true || route?.startsWith("recording/") == true || route?.startsWith("metronome/context") == true
    val selected = if (route == TunerRoute.Context) TunerRoute.Home else route
    fun selectTool(destination: String) {
        if (selected == destination && !secondary) return
        nav.navigate(destination) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondary) IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.back))
                }
                Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (route == "settings") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.brand_iso), contentDescription = null, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.settings_title), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    } else Image(painterResource(R.drawable.brand_logo), contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(36.dp).clickable { selectTool(StartRoute) },
                        contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                }
                if (route != "settings") IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings_title), tint = NeonSoft)
                }
            }
        },
        bottomBar = {
            if (!secondary && route != StartRoute) NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                tools.forEach { tool ->
                    NavigationBarItem(
                        selected = selected == tool.route,
                        onClick = { selectTool(tool.route) },
                        icon = { Icon(tool.icon, contentDescription = null) },
                        label = { Text(stringResource(tool.title), fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonSoft, selectedTextColor = NeonSoft,
                            indicatorColor = Line, unselectedIconColor = Muted, unselectedTextColor = Muted),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = StartRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
            composable(StartRoute) { HomeScreen(onOpen = ::selectTool) }
            composable(TunerRoute.Home) { tuner(null) }
            composable(TunerRoute.Context) { contextEntry ->
                val args = contextEntry.arguments
                val context = TunerRoute.decode(args?.getString("instrument"), args?.getString("tuning"), args?.getString("a4"))
                context.getOrNull()?.let { tuner(it) } ?: Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.invalid_tuner_context))
                    TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.back)) }
                }
            }
            composable("settings") { SettingsScreen(preferences, setA4, setNaming, appLanguage, setAppLanguage) }
            composable("song/{songId}") { backStackEntry ->
                val songId = backStackEntry.arguments?.getString("songId")
                if (songId != null) SongDetailScreen(
                    model = songModel, chordModel = chordModel, songId = songId,
                    openTuner = { nav.openTuner(it) }, openRecording = { nav.navigate("recording/$it") },
                    openMetronome = { bpm, numerator, denominator -> nav.navigate(metronomeContextRoute(bpm, numerator, denominator)) },
                    onDeleted = { nav.popBackStack() }, onStartRecording = { nav.navigate("recorder"); startSongRecording(songId) },
                    a4Hz = preferences.configuration.a4Hz, naming = preferences.noteNaming,
                    chordMode = preferences.chordPresentationMode, setChordMode = setChordPresentationMode)
            }
            composable("recording/{recordingId}") { backStackEntry ->
                recorder(backStackEntry.arguments?.getString("recordingId")) { nav.popBackStack() }
            }
            composable("metronome/context?bpm={bpm}&numerator={numerator}&denominator={denominator}", arguments = listOf(
                navArgument("bpm") { type = androidx.navigation.NavType.IntType; defaultValue = -1 },
                navArgument("numerator") { type = androidx.navigation.NavType.IntType; defaultValue = -1 },
                navArgument("denominator") { type = androidx.navigation.NavType.IntType; defaultValue = -1 },
            )) { backStackEntry ->
                val args = backStackEntry.arguments
                MetronomeScreen(metronomeModel, args?.getInt("bpm")?.takeIf { it >= 0 },
                    args?.getInt("numerator")?.takeIf { it >= 0 }, args?.getInt("denominator")?.takeIf { it >= 0 }, contextual = true)
            }
            tools.filter { it.route != TunerRoute.Home }.forEach { tool ->
                composable(tool.route) {
                    if (tool.route == "recorder") {
                        recorder(null) { }
                    } else if (tool.route == "songs") {
                        SongLibraryScreen(songModel) { nav.navigate("song/$it") }
                    } else if (tool.route == "chords") {
                        ChordDictionaryScreenV42(chordModel, preferences.noteNaming, preferences.chordPresentationMode, setChordPresentationMode)
                    } else if (tool.route == "metronome") {
                        MetronomeScreen(metronomeModel)
                    } else {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tool.icon, null, Modifier.size(48.dp), tint = NeonSoft)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(tool.title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(tool.description), modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(stringResource(R.string.tool_coming_soon), color = Muted)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { selectTool(TunerRoute.Home) }, shape = AppShape) { Text(stringResource(R.string.return_to_tuner)) }
                    }
                    }
                }
            }
        }
    }
}

private fun metronomeContextRoute(bpm: Int?, numerator: Int?, denominator: Int?) =
    "metronome/context?bpm=${bpm ?: -1}&numerator=${numerator ?: -1}&denominator=${denominator ?: -1}"
