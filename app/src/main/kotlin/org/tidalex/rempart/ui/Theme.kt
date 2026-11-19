package org.tidalex.rempart.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tidalex.rempart.R

/** Palette « pierre et pétrole » : calme, sérieuse, jamais alarmante. */
private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F4E5F), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE6EC), onPrimaryContainer = Color(0xFF0B2A33),
    secondary = Color(0xFFB0703C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DFC9), onSecondaryContainer = Color(0xFF3A1F08),
    background = Color(0xFFF6F3EE), onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFFFCF8), onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE9E3DA), onSurfaceVariant = Color(0xFF4A4740),
    error = Color(0xFFB3261E),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FCBDB), onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF1F4E5F), onPrimaryContainer = Color(0xFFCFE6EC),
    secondary = Color(0xFFE8B98F), onSecondary = Color(0xFF4A2808),
    secondaryContainer = Color(0xFF5E3C1E), onSecondaryContainer = Color(0xFFF6DFC9),
    background = Color(0xFF111A1E), onBackground = Color(0xFFE3E2DE),
    surface = Color(0xFF172227), onSurface = Color(0xFFE3E2DE),
    surfaceVariant = Color(0xFF26333A), onSurfaceVariant = Color(0xFFC2C8CA),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun RempartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppTypography, content = content)
}

/** Gabarit d'écran : titre, retour optionnel, contenu défilant. */
@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        content()
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
fun StatusLine(label: String, ok: Boolean, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (ok) R.string.enabled else R.string.disabled),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!ok && action != null) action()
    }
}

/** Durée lisible, sans dépendre de la langue : « 71 h 59 min » / « 4 min 30 s ». */
fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        m > 0 -> "%d min %02d s".format(m, sec)
        else -> "%d s".format(sec)
    }
}
