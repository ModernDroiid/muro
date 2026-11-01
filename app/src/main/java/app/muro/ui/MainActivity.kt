package app.muro.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MuroTheme { MuroRoot() }
        }
    }
}

@Composable
private fun MuroRoot(vm: MuroViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDeviceState() }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.startProtection()
    }
    // La notificación fija es opcional: la protección funciona aunque se rechace.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun enable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val consent: Intent? = VpnService.prepare(context)
        if (consent != null) vpnConsent.launch(consent) else vm.startProtection()
    }

    Column(Modifier.fillMaxSize().background(MuroColors.Background).imePadding()) {
        Box(Modifier.weight(1f)) {
            // Hasta leer los ajustes no se dibuja nada, para no mostrar "sin apps" un instante.
            settings?.let { current ->
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        vm = vm,
                        settings = current,
                        status = status,
                        onToggle = { on ->
                            when {
                                !on -> vm.stopProtection()
                                current.selectedApps.isEmpty() -> tab = Tab.APPS
                                else -> enable()
                            }
                        },
                        onEditApps = { tab = Tab.APPS },
                        onOpenSettings = { tab = Tab.LISTS },
                    )
                    Tab.APPS -> AppsScreen(vm, current)
                    Tab.LOG -> LogScreen(vm)
                    Tab.LISTS -> ListsScreen(vm, current)
                }
            }
            // Las listas se dibujan de borde a borde: sin esto pasarían por debajo de la hora.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MuroColors.Background),
            )
        }
        BottomBar(current = tab, onSelect = { tab = it })
    }
}
