package app.muro.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.muro.muro
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Recupera la protección tras reiniciar, solo si estaba encendida al apagar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.muro
        app.scope.launch {
            try {
                val settings = app.settings.flow.first()
                // prepare() != null significa que hace falta pedir permiso, y sin
                // pantalla no se puede.
                if (settings.startOnBoot && settings.protectionEnabled && VpnService.prepare(app) == null) {
                    MuroVpnService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
