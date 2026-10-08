package org.anarkey.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val SelectorShape = AppShape
internal val SelectorPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)

@Composable
internal fun SelectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    contentPadding: PaddingValues = SelectorPadding,
    /** One selector per screen carries the accent; the rest stay neutral so they do not compete for attention. */
    primary: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = SelectorShape,
        border = BorderStroke(1.dp, (if (primary) NeonSoft else Quiet).copy(alpha = if (enabled) (if (primary) 0.8f else 0.75f) else 0.35f)),
        colors = if (primary) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            if (supportingText != null) Text(supportingText, style = MaterialTheme.typography.labelSmall, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
