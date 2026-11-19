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
import kotlin.math.roundToInt
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
fun SetupScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    rememberUiTick()
    val status = Protection.status(context)
    var showA11yDisclosure by remember { mutableStateOf(false) }

    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (RempartVpnService.prepare(context) == null) RempartVpnService.start(context)
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    ScreenScaffold(stringResource(R.string.setup_title)) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)

        SetupStep(R.string.setup_vpn_title, R.string.setup_vpn_body, status.vpnRunning, "stepVpn") {
            val consent = RempartVpnService.prepare(context)
            if (consent != null) vpnConsent.launch(consent) else RempartVpnService.start(context)
        }
        SetupStep(R.string.setup_a11y_title, R.string.setup_a11y_body, status.accessibility, "stepA11y") { showA11yDisclosure = true }
        SetupStep(R.string.setup_admin_title, R.string.setup_admin_body, status.admin, "stepAdmin") {
            adminLauncher.launch(RempartAdminReceiver.activationIntent(context, context.getString(R.string.setup_admin_body)))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            SetupStep(R.string.setup_notif_title, R.string.setup_notif_body, status.notifications, "stepNotif") {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!DeviceOwnerPolicies.isDeviceOwner(context)) {
            InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(R.string.setup_always_on_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.setup_always_on_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { context.startActivity(Protection.vpnSettingsIntent()) }) { Text(stringResource(R.string.setup_always_on_button)) }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("setupNext")) { Text(stringResource(R.string.setup_next_partner)) }
    }

    if (showA11yDisclosure) {
        // Divulgation bien visible exigée par Google Play AVANT l'activation.
        AlertDialog(
            onDismissRequest = { showA11yDisclosure = false },
            title = { Text(stringResource(R.string.a11y_disclosure_title)) },
            text = { Text(stringResource(R.string.a11y_disclosure_body)) },
            confirmButton = {
                Button(onClick = {
                    showA11yDisclosure = false
                    context.startActivity(Protection.accessibilitySettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.testTag("a11yAccept")) { Text(stringResource(R.string.a11y_disclosure_accept)) }
            },
            dismissButton = { TextButton(onClick = { showA11yDisclosure = false }) { Text(stringResource(R.string.a11y_disclosure_decline)) } },
        )
    }
}

@Composable
private fun SetupStep(title: Int, body: Int, done: Boolean, tag: String, onActivate: () -> Unit) {
    InfoCard {
        StatusLine(stringResource(title), done)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!done) FilledTonalButton(onClick = onActivate, modifier = Modifier.testTag(tag)) { Text(stringResource(R.string.activate)) }
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

    ScreenScaffold(stringResource(R.string.partner_title), onBack) {
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
        Text(stringResource(R.string.lock_body, repo.state.value.delayHours), style = MaterialTheme.typography.bodyLarge)
        InfoCard {
            StatusLine(stringResource(R.string.home_status_vpn), status.vpnRunning)
            StatusLine(stringResource(R.string.home_status_a11y), status.accessibility)
            StatusLine(stringResource(R.string.home_status_admin), status.admin)
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
