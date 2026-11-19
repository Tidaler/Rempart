package org.tidalex.rempart.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.tidalex.rempart.BuildConfig
import org.tidalex.rempart.Protection
import org.tidalex.rempart.R
import org.tidalex.rempart.Stats
import org.tidalex.rempart.StreakRepository
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.core.lock.Phase
import org.tidalex.rempart.core.lock.UnlockResult
import org.tidalex.rempart.filter.dns.BlocklistUpdateWorker
import org.tidalex.rempart.filter.dns.FilterSettings
import org.tidalex.rempart.filter.dns.UpstreamChoice
import org.tidalex.rempart.lock.LockRepository
import org.tidalex.rempart.rememberUiTick
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Date

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val repo = LockRepository.get(context)
    rememberUiTick()
    val lock by repo.state.collectAsStateWithLifecycle()
    LockTicker(repo)
    val status = Protection.status(context)
    val streakRepo = remember { StreakRepository(context) }
    var streak by remember { mutableStateOf(streakRepo.get()) }
    var confirmRelapse by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    ScreenScaffold(stringResource(R.string.app_name)) {
        InfoCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(pluralStringResource(R.plurals.home_streak, streak.currentDays(today).toInt(), streak.currentDays(today).toInt()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("streak"))
            Text(stringResource(R.string.home_streak_best, streak.best(today).toInt(), streak.nextMilestone(today).toInt()), style = MaterialTheme.typography.bodyMedium)
            Text(Stats.total(context).toInt().let { pluralStringResource(R.plurals.home_blocked, it, it) }, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { confirmRelapse = true }) { Text(stringResource(R.string.home_relapse)) }
        }

        Button(onClick = { context.startActivity(Intent(context, org.tidalex.rempart.BlockedActivity::class.java).putExtra("sos", true)) },
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_sos)) }

        InfoCard {
            Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleMedium)
            val fix: @Composable () -> Unit = { TextButton(onClick = { onNavigate("setup") }) { Text(stringResource(R.string.home_fix)) } }
            StatusLine(stringResource(R.string.home_status_vpn), status.vpnRunning, fix)
            StatusLine(stringResource(R.string.home_status_a11y), status.accessibility, fix)
            StatusLine(stringResource(R.string.home_status_admin), status.admin, fix)
            if (status.deviceOwner) StatusLine(stringResource(R.string.home_status_owner), true)
        }

        InfoCard(container = MaterialTheme.colorScheme.surfaceVariant) {
            val label = when (lock.phase) {
                Phase.OPEN -> stringResource(R.string.home_lock_open)
                Phase.LOCKED -> stringResource(R.string.home_lock_locked)
                Phase.RELEASING -> stringResource(R.string.home_lock_releasing, formatDuration(lock.release?.remainingMs ?: 0))
            }
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("lockState"))
            if (lock.phase == Phase.RELEASING) LinearProgressIndicator(progress = { lock.release?.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lock.isLocked) {
                    FilledTonalButton(onClick = { onNavigate("unlock") }, modifier = Modifier.testTag("goUnlock")) { Text(stringResource(R.string.home_unlock)) }
                } else {
                    FilledTonalButton(onClick = { onNavigate("lock") }, modifier = Modifier.testTag("goLock")) { Text(stringResource(R.string.home_lock_now)) }
                }
            }
            if (!lock.isLocked) {
                Text(stringResource(R.string.home_uninstall_ready), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { Protection.turnEverythingOff(context) }) { Text(stringResource(R.string.home_turn_off)) }
            }
        }

        listOf("resources" to R.string.home_resources, "journal" to R.string.home_journal, "settings" to R.string.home_settings, "about" to R.string.home_about)
            .forEach { (route, label) -> OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) } }
    }

    if (confirmRelapse) {
        AlertDialog(
            onDismissRequest = { confirmRelapse = false },
            title = { Text(stringResource(R.string.home_relapse_title)) },
            text = { Text(stringResource(R.string.home_relapse_body)) },
            confirmButton = { Button(onClick = { streak = streakRepo.relapse(); confirmRelapse = false }) { Text(stringResource(R.string.home_relapse_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmRelapse = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun UnlockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = LockRepository.get(context)
    val lock by repo.state.collectAsStateWithLifecycle()
    LockTicker(repo)
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(stringResource(R.string.unlock_title), onBack) {
        if (!lock.isLocked) {
            InfoCard(container = MaterialTheme.colorScheme.primaryContainer) { Text(stringResource(R.string.unlock_success), modifier = Modifier.testTag("unlocked")) }
            return@ScreenScaffold
        }
        InfoCard {
            Text(stringResource(R.string.unlock_partner), style = MaterialTheme.typography.titleMedium)
            if (lock.partner == null) {
                Text(stringResource(R.string.unlock_no_partner))
            } else {
                OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.unlock_code_hint)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("unlockCode"))
                Button(modifier = Modifier.testTag("unlockSubmit"), onClick = {
                    message = when (val r = repo.unlockWithPartnerCode(code)) {
                        UnlockResult.Success -> context.getString(R.string.unlock_success)
                        is UnlockResult.WrongCode -> context.resources.getQuantityString(R.plurals.unlock_wrong, r.attemptsBeforeCooldown, r.attemptsBeforeCooldown)
                        is UnlockResult.CoolingDown -> context.getString(R.string.unlock_cooldown, formatDuration(r.remainingMs))
                        else -> null
                    }
                    code = ""
                }) { Text(stringResource(R.string.unlock_submit)) }
                lock.partnerPhone?.let { phone: String ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())) }) {
                        Text(stringResource(R.string.unlock_call_partner, lock.partnerName ?: phone))
                    }
                }
            }
            message?.let { Text(it, modifier = Modifier.testTag("unlockMessage")) }
        }
        InfoCard(container = MaterialTheme.colorScheme.surfaceVariant) {
            Text(stringResource(R.string.unlock_delay), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.unlock_delay_body, lock.delayHours), style = MaterialTheme.typography.bodyMedium)
            if (lock.phase == Phase.RELEASING) {
                Text(stringResource(R.string.unlock_delay_running, formatDuration(lock.release?.remainingMs ?: 0)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("releaseRunning"))
                LinearProgressIndicator(progress = { lock.release?.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { repo.cancelRelease() }, modifier = Modifier.testTag("releaseCancel")) { Text(stringResource(R.string.unlock_delay_cancel)) }
            } else {
                OutlinedButton(onClick = { repo.requestRelease() }, modifier = Modifier.testTag("releaseStart")) { Text(stringResource(R.string.unlock_delay_start, lock.delayHours)) }
            }
        }
    }
}

/** Fait avancer le délai et les pauses chaque seconde tant que l'écran est visible. */
@Composable
fun LockTicker(repo: LockRepository) {
    LaunchedEffect(repo) { while (true) { repo.tick(); delay(1000) } }
}

@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    ScreenScaffold(stringResource(R.string.resources_title), onBack) {
        Text(stringResource(R.string.resources_intro), style = MaterialTheme.typography.bodyLarge)
        InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(stringResource(R.string.resources_emergency)) }
        InfoCard { Text(stringResource(R.string.resources_list)) }
        InfoCard {
            Text(stringResource(R.string.resources_tips_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.resources_tips))
        }
    }
}

@Composable
fun eventLabel(e: IntegrityEvent): String = stringResource(
    when (e) {
        IntegrityEvent.LOCK_ENGAGED -> R.string.ev_LOCK_ENGAGED
        IntegrityEvent.RELEASE_REQUESTED -> R.string.ev_RELEASE_REQUESTED
        IntegrityEvent.RELEASE_CANCELLED -> R.string.ev_RELEASE_CANCELLED
        IntegrityEvent.RELEASE_COMPLETED -> R.string.ev_RELEASE_COMPLETED
        IntegrityEvent.PARTNER_UNLOCK -> R.string.ev_PARTNER_UNLOCK
        IntegrityEvent.PARTNER_CODE_FAILED -> R.string.ev_PARTNER_CODE_FAILED
        IntegrityEvent.VPN_REVOKED -> R.string.ev_VPN_REVOKED
        IntegrityEvent.VPN_STARTED -> R.string.ev_VPN_STARTED
        IntegrityEvent.ACCESSIBILITY_LOST -> R.string.ev_ACCESSIBILITY_LOST
        IntegrityEvent.ADMIN_DISABLE_REQUESTED -> R.string.ev_ADMIN_DISABLE_REQUESTED
        IntegrityEvent.PROTECTED_SCREEN_BLOCKED -> R.string.ev_PROTECTED_SCREEN_BLOCKED
        IntegrityEvent.CLOCK_CHANGED -> R.string.ev_CLOCK_CHANGED
        IntegrityEvent.PROTECTION_GAP -> R.string.ev_PROTECTION_GAP
    },
)

@Composable
fun JournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log by LockRepository.get(context).log.collectAsStateWithLifecycle()
    val fmt = DateFormat.getMediumDateFormat(context)
    val tfmt = DateFormat.getTimeFormat(context)
    ScreenScaffold(stringResource(R.string.journal_title), onBack) {
        Text(stringResource(R.string.journal_intro), style = MaterialTheme.typography.bodyMedium)
        if (log.isEmpty()) Text(stringResource(R.string.journal_empty))
        log.asReversed().forEach { entry ->
            InfoCard {
                Text(eventLabel(entry.event), style = MaterialTheme.typography.bodyLarge)
                Text("${fmt.format(Date(entry.wallTimeMs))} ${tfmt.format(Date(entry.wallTimeMs))}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { FilterSettings(context) }
    val locked = LockRepository.get(context).isLocked
    var safe by remember { mutableStateOf(settings.safeSearch) }
    var yt by remember { mutableStateOf(settings.youtubeRestricted) }
    var upstream by remember { mutableStateOf(settings.upstream) }

    ScreenScaffold(stringResource(R.string.settings_title), onBack) {
        if (locked) InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(stringResource(R.string.settings_locked_note)) }
        InfoCard {
            ToggleRow(stringResource(R.string.settings_safesearch), safe, enabled = !locked) { safe = it; settings.safeSearch = it }
            ToggleRow(stringResource(R.string.settings_youtube), yt, enabled = !locked) { yt = it; settings.youtubeRestricted = it }
        }
        InfoCard {
            Text(stringResource(R.string.settings_upstream), style = MaterialTheme.typography.titleMedium)
            UpstreamChoice.entries.forEach { c ->
                Row(Modifier.fillMaxWidth().selectable(upstream == c, enabled = !locked) { upstream = c; settings.upstream = c },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = upstream == c, onClick = null, enabled = !locked)
                    Text(c.label)
                }
            }
        }
        InfoCard {
            Text((settings.listSize.takeIf { it > 0 } ?: 148_000).let { pluralStringResource(R.plurals.settings_lists, it, it) }, style = MaterialTheme.typography.titleMedium)
            Text(if (settings.listUpdatedAt == 0L) stringResource(R.string.settings_lists_never)
                else stringResource(R.string.settings_lists_updated, DateFormat.getMediumDateFormat(context).format(Date(settings.listUpdatedAt))))
            OutlinedButton(onClick = { WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BlocklistUpdateWorker>().build()) }) {
                Text(stringResource(R.string.settings_update_now))
            }
        }
        InfoCard {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_language_body), style = MaterialTheme.typography.bodyMedium)
            Protection.appLocaleIntent(context)?.let { intent ->
                OutlinedButton(onClick = { context.startActivity(intent) }) { Text(stringResource(R.string.settings_language_button)) }
            }
        }
        InfoCard {
            Text(stringResource(R.string.settings_device_owner), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_device_owner_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold(stringResource(R.string.about_title), onBack) {
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
        Column {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) }) { Text(stringResource(R.string.about_source)) }
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

const val SOURCE_URL = "https://github.com/Tidaler/Rempart"
