package org.tidalex.rempart.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.tidalex.rempart.AutoStart
import org.tidalex.rempart.Battery
import org.tidalex.rempart.PermissionStep
import org.tidalex.rempart.Permissions
import org.tidalex.rempart.Protection
import org.tidalex.rempart.R
import org.tidalex.rempart.core.lock.CodeProblem
import org.tidalex.rempart.core.lock.PartnerSecret
import org.tidalex.rempart.filter.dns.RempartVpnService
import org.tidalex.rempart.lock.DeviceOwnerPolicies
import org.tidalex.rempart.lock.LockRepository
import org.tidalex.rempart.lock.RempartAdminReceiver
import org.tidalex.rempart.rememberUiTick

@Composable
fun WelcomeScreen(onAccept: () -> Unit, onPrivacy: () -> Unit) {
    var selfUse by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.welcome_title)) {
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
        InfoCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selfUse, onCheckedChange = { selfUse = it }, modifier = Modifier.testTag("selfUse"))
                Text(stringResource(R.string.welcome_self_use), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.welcome_self_use_hint), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onPrivacy) { Text(stringResource(R.string.welcome_privacy_link)) }
        Button(onClick = onAccept, enabled = selfUse, modifier = Modifier.fillMaxWidth().testTag("welcomeContinue")) {
            Text(stringResource(R.string.continue_))
        }
    }
}

@Composable
fun SetupScreen(onboardingDone: Boolean, onNext: () -> Unit) {
    val context = LocalContext.current
    rememberUiTick()
    val status = Protection.status(context)
    val missing = Permissions.missing(context)
    var showA11yDisclosure by remember { mutableStateOf(false) }

    // Enchaînement « Tout autoriser » : chaque écran système s'ouvre après le précédent.
    var chain by remember { mutableStateOf(false) }
    val attempted = remember { mutableSetOf<PermissionStep>() }
    var current by remember { mutableStateOf<PermissionStep?>(null) }
    val next = remember { mutableStateOf({}) }

    val stepLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (current == PermissionStep.VPN && RempartVpnService.prepare(context) == null) RempartVpnService.start(context)
        next.value()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next.value() }

    fun launch(intent: Intent?): Boolean = intent != null && runCatching { stepLauncher.launch(intent) }.isSuccess

    fun open(step: PermissionStep) {
        current = step
        when (step) {
            PermissionStep.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else next.value()
            PermissionStep.VPN -> {
                val consent = RempartVpnService.prepare(context)
                if (consent == null) { RempartVpnService.start(context); next.value() }
                else if (!launch(consent)) next.value()
            }
            PermissionStep.BATTERY ->
                if (!launch(Battery.requestIntent(context)) && !launch(Battery.fallbackIntent())) next.value()
            PermissionStep.ADMIN ->
                if (!launch(RempartAdminReceiver.activationIntent(context, context.getString(R.string.setup_admin_body)))) next.value()
            PermissionStep.AUTOSTART -> {
                AutoStart.markAsked(context)
                if (!launch(AutoStart.intent(context))) next.value()
            }
            // Divulgation bien visible exigée par Google Play AVANT d'ouvrir les réglages.
            PermissionStep.ACCESSIBILITY -> showA11yDisclosure = true
        }
    }

    next.value = {
        val step = if (chain) Permissions.missing(context).firstOrNull { it !in attempted } else null
        if (step == null) { chain = false; current = null } else { attempted += step; open(step) }
    }

    fun startChain() { attempted.clear(); chain = true; next.value() }

    // Première ouverture, ou retour après une mise à jour : on demande tout de suite ce qui manque.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted && Permissions.autoPrompt && missing.isNotEmpty()) { autoStarted = true; startChain() }
    }

    ScreenScaffold(stringResource(R.string.setup_title)) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
        if (missing.isNotEmpty()) {
            InfoCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.setup_all_hint), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { startChain() }, modifier = Modifier.fillMaxWidth().testTag("allowAll")) { Text(stringResource(R.string.setup_all_button)) }
            }
        }

        SetupStep(R.string.setup_vpn_title, R.string.setup_vpn_body, status.vpnRunning, "stepVpn") { open(PermissionStep.VPN) }
        SetupStep(R.string.setup_battery_title, R.string.setup_battery_body, status.battery, "stepBattery") { open(PermissionStep.BATTERY) }
        if (status.autoStartAvailable) {
            SetupStep(R.string.setup_autostart_title, R.string.setup_autostart_body, status.autoStartAsked, "stepAutoStart",
                button = R.string.setup_autostart_button, allowRepeat = true) { open(PermissionStep.AUTOSTART) }
        }
        SetupStep(R.string.setup_a11y_title, R.string.setup_a11y_body, status.accessibility, "stepA11y") { open(PermissionStep.ACCESSIBILITY) }
        SetupStep(R.string.setup_admin_title, R.string.setup_admin_body, status.admin, "stepAdmin") { open(PermissionStep.ADMIN) }
        if (Build.VERSION.SDK_INT >= 33) {
            SetupStep(R.string.setup_notif_title, R.string.setup_notif_body, status.notifications, "stepNotif") { open(PermissionStep.NOTIFICATIONS) }
        }
        if (!DeviceOwnerPolicies.isDeviceOwner(context)) {
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(R.string.setup_always_on_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.setup_always_on_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { runCatching { context.startActivity(Protection.vpnSettingsIntent()) } }) { Text(stringResource(R.string.setup_always_on_button)) }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("setupNext")) {
            Text(stringResource(if (onboardingDone) R.string.setup_back_home else R.string.setup_next_partner))
        }
    }

    if (showA11yDisclosure) {
        AlertDialog(
            onDismissRequest = { showA11yDisclosure = false; next.value() },
            title = { Text(stringResource(R.string.a11y_disclosure_title)) },
            text = { Text(stringResource(R.string.a11y_disclosure_body)) },
            confirmButton = {
                Button(onClick = {
                    showA11yDisclosure = false
                    current = PermissionStep.ACCESSIBILITY
                    if (!launch(Protection.accessibilitySettingsIntent())) next.value()
                }, modifier = Modifier.testTag("a11yAccept")) { Text(stringResource(R.string.a11y_disclosure_accept)) }
            },
            dismissButton = { TextButton(onClick = { showA11yDisclosure = false; next.value() }) { Text(stringResource(R.string.a11y_disclosure_decline)) } },
        )
    }
}

@Composable
private fun SetupStep(
    title: Int, body: Int, done: Boolean, tag: String,
    button: Int = R.string.activate, allowRepeat: Boolean = false, onActivate: () -> Unit,
) {
    InfoCard {
        StatusLine(stringResource(title), done)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!done) FilledTonalButton(onClick = onActivate, modifier = Modifier.testTag(tag)) { Text(stringResource(button)) }
        else if (allowRepeat) TextButton(onClick = onActivate) { Text(stringResource(button)) }
    }
}

fun codeProblemText(p: CodeProblem): Int = when (p) {
    CodeProblem.TOO_SHORT -> R.string.code_too_short
    CodeProblem.TOO_SIMPLE -> R.string.code_too_simple
    CodeProblem.TOO_COMMON -> R.string.code_too_common
}

@Composable
fun PartnerScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = LockRepository.get(context)
    var name by rememberSaveable { mutableStateOf(repo.state.value.partnerName ?: "") }
    var phone by rememberSaveable { mutableStateOf(repo.state.value.partnerPhone ?: "") }
    var generated by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val generatedCode = rememberSaveable { PartnerSecret.generate() }
    var delay by remember { mutableFloatStateOf(repo.state.value.delayHours.toFloat()) }
    var error by remember { mutableStateOf<Int?>(null) }

    val lockState by repo.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.partner_title), onBack) {
        // Changer de partenaire pendant le verrouillage est interdit (et faisait planter l'appli).
        if (lockState.isLocked) {
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(R.string.partner_locked_note), modifier = Modifier.testTag("partnerLocked"))
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_)) }
            return@ScreenScaffold
        }
        Text(stringResource(R.string.partner_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.partner_name)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("partnerName"))
        OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.partner_phone)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())

        listOf(false to R.string.partner_mode_typed, true to R.string.partner_mode_generated).forEach { (gen, label) ->
            Row(Modifier.fillMaxWidth().selectable(generated == gen) { generated = gen }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = generated == gen, onClick = { generated = gen })
                Text(stringResource(label))
            }
        }
        if (!generated) {
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(stringResource(R.string.partner_hand_phone)) }
            val opts = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)
            OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.partner_code)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = opts, modifier = Modifier.fillMaxWidth().testTag("partnerCode"))
            OutlinedTextField(confirm, { confirm = it }, label = { Text(stringResource(R.string.partner_code_confirm)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = opts, modifier = Modifier.fillMaxWidth().testTag("partnerConfirm"))
        } else {
            InfoCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text(generatedCode, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.partner_generated_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.partner_share_text, generatedCode))
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.partner_share)) }
            }
        }
        Text(stringResource(R.string.delay_label, delay.roundToInt()), style = MaterialTheme.typography.titleMedium)
        Slider(value = delay, onValueChange = { delay = it }, valueRange = 24f..168f, steps = 5)
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(modifier = Modifier.fillMaxWidth().testTag("partnerSave"), onClick = {
            val chosen = if (generated) generatedCode else code
            val problem = PartnerSecret.validate(chosen)
            error = when {
                !generated && code != confirm -> R.string.partner_code_mismatch
                problem != null -> codeProblemText(problem)
                else -> null
            }
            if (error == null) {
                repo.setPartner(chosen, name, phone)
                repo.setDelayHours(delay.roundToInt().coerceIn(24, 168))
                onDone()
            }
        }) { Text(stringResource(R.string.save)) }
        TextButton(onClick = {
            repo.setPartner(null, null, null)
            repo.setDelayHours(delay.roundToInt().coerceIn(24, 168))
            onDone()
        }) { Text(stringResource(R.string.partner_skip)) }
    }
}

@Composable
fun LockScreen(onDone: () -> Unit, onBack: () -> Unit, onFix: () -> Unit) {
    val context = LocalContext.current
    rememberUiTick()
    val status = Protection.status(context)
    val repo = LockRepository.get(context)
    ScreenScaffold(stringResource(R.string.lock_title), onBack) {
        val lockState by repo.state.collectAsStateWithLifecycle()
        Text(stringResource(R.string.lock_body, lockState.delayHours), style = MaterialTheme.typography.bodyLarge)
        InfoCard {
            StatusLine(stringResource(R.string.home_status_vpn), status.vpnRunning)
            StatusLine(stringResource(R.string.home_status_a11y), status.accessibility)
            StatusLine(stringResource(R.string.home_status_admin), status.admin)
            StatusLine(stringResource(R.string.home_status_battery), status.battery)
        }
        if (!status.allEssential) {
            Text(stringResource(R.string.lock_missing), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.home_fix)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = status.allEssential, modifier = Modifier.testTag("lockConfirm"), onClick = {
                if (!repo.isLocked) repo.engage()
                DeviceOwnerPolicies.apply(context)
                onDone()
            }) { Text(stringResource(R.string.lock_confirm)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
        }
    }
}
