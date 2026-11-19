package org.tidalex.rempart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.tidalex.rempart.lock.LockRepository
import org.tidalex.rempart.ui.AboutScreen
import org.tidalex.rempart.ui.HomeScreen
import org.tidalex.rempart.ui.JournalScreen
import org.tidalex.rempart.ui.LockScreen
import org.tidalex.rempart.ui.PartnerScreen
import org.tidalex.rempart.ui.RempartTheme
import org.tidalex.rempart.ui.ResourcesScreen
import org.tidalex.rempart.ui.SettingsScreen
import org.tidalex.rempart.ui.SetupScreen
import org.tidalex.rempart.ui.UnlockScreen
import org.tidalex.rempart.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        RempartApp.wire(this)
        setContent {
            RempartTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { RempartNav() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LockRepository.get(this).tick()
        Protection.ensureVpnRunning(this)
    }
}

/** Battement d'horloge de l'interface : rafraîchit statuts et comptes à rebours. */
@Composable
fun rememberUiTick(periodMs: Long = 1000): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); tick++ } }
    return tick
}

@Composable
fun RempartNav() {
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = when {
        !Onboarding.consented(context) -> "welcome"
        !Onboarding.partnerStepDone(context) -> "setup"
        else -> "home"
    }
    NavHost(nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(onAccept = { Onboarding.setConsented(context); nav.navigate("setup") { popUpTo("welcome") { inclusive = true } } }, onPrivacy = { nav.navigate("about") }) }
        composable("setup") { SetupScreen(onNext = { nav.navigate("partner") }) }
        composable("partner") { PartnerScreen(onDone = { Onboarding.setPartnerStepDone(context); nav.navigate("lock") }, onBack = { nav.popBackStack() }) }
        composable("lock") { LockScreen(onDone = { nav.navigate("home") { popUpTo(0) } }, onBack = { nav.popBackStack() }, onFix = { nav.navigate("setup") }) }
        composable("home") { HomeScreen(onNavigate = { nav.navigate(it) }) }
        composable("unlock") { UnlockScreen(onBack = { nav.popBackStack() }) }
        composable("resources") { ResourcesScreen(onBack = { nav.popBackStack() }) }
        composable("journal") { JournalScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
