package org.tidalex.rempart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tidalex.rempart.core.domain.BlockReason
import org.tidalex.rempart.filter.accessibility.RempartAccessibilityService
import org.tidalex.rempart.lock.LockRepository
import org.tidalex.rempart.ui.RempartTheme
import java.time.LocalDate

/**
 * Écran affiché à la place d'un contenu bloqué : encouragement, respiration
 * guidée, compteur de jours, appel au partenaire. Jamais culpabilisant.
 */
class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val reason = intent.getStringExtra(RempartAccessibilityService.EXTRA_REASON)?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
        setContent { RempartTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { BlockedScreen(reason, onHome = ::goHome) } } }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
fun BlockedScreen(reason: BlockReason?, onHome: () -> Unit) {
    val context = LocalContext.current
    val streak = StreakRepository(context).get()
    val days = streak.currentDays(LocalDate.now())
    val messages = stringArrayResource(R.array.encouragements)
    val message = messages[((days + System.currentTimeMillis() / 60_000) % messages.size).toInt()]
    val partner = LockRepository.get(context).state.value
    val settings = reason == BlockReason.PROTECTED_SETTINGS

    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(0.6f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(if (settings) R.string.blocked_settings_title else R.string.blocked_title),
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("blockedTitle"),
        )
        Text(stringResource(R.string.home_streak, days.toInt()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(if (settings) stringResource(R.string.blocked_settings_body) else message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).scale(scale).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            Text(stringResource(if (scale > 0.8f) R.string.blocked_exhale else R.string.blocked_inhale), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.blocked_breathe), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        partner.partnerPhone?.let { phone ->
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.blocked_call))
            }
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().testTag("blockedHome")) { Text(stringResource(R.string.blocked_home)) }
    }
}
