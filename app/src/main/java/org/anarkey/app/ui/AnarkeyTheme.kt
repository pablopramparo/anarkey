package org.anarkey.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF0C0C0E)
internal val Panel = Color(0xFF171719)
internal val Line = Color(0xFF39322C)
internal val Muted = Color(0xFFB0AAA4)
internal val Quiet = Color(0xFF807870)
internal val Neon = Color(0xFFFF7900)
internal val NeonSoft = Color(0xFFFFA34D)
internal val White = Color(0xFFF3F0EB)
internal val Warning = Color(0xFFFFB95E)
/** The single corner radius of the app. Circular buttons are the only exception. */
internal val AppShape = RoundedCornerShape(12.dp)
internal val FormFieldShape = AppShape

@Composable
internal fun AnarkeyFormFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Muted.copy(alpha = 0.72f),
    focusedBorderColor = NeonSoft,
    errorBorderColor = Warning,
)

@Composable
fun AnarkeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon, onPrimary = Ink, primaryContainer = Color(0xFF48250D), onPrimaryContainer = Color(0xFFFFDCC0),
            secondary = NeonSoft, onSecondary = Ink, secondaryContainer = Color(0xFF382719), onSecondaryContainer = Color(0xFFFFDFC5),
            tertiary = Color(0xFFFFCA70), onTertiary = Ink, tertiaryContainer = Color(0xFF403018), onTertiaryContainer = Color(0xFFFFE3AD),
            background = Ink, surface = Panel, onBackground = White, onSurface = White,
            surfaceVariant = Color(0xFF29241F), onSurfaceVariant = Muted, outline = Quiet, outlineVariant = Line,
            surfaceTint = Neon, inverseSurface = White, inverseOnSurface = Ink, inversePrimary = Color(0xFF944700),
            surfaceDim = Ink, surfaceBright = Color(0xFF393432),
            surfaceContainerLowest = Color(0xFF080809), surfaceContainerLow = Color(0xFF131315),
            surfaceContainer = Panel, surfaceContainerHigh = Color(0xFF222123), surfaceContainerHighest = Color(0xFF2C2928),
        ),
        shapes = Shapes(extraSmall = AppShape, small = AppShape, medium = AppShape, large = AppShape, extraLarge = AppShape),
        content = content,
    )
}
