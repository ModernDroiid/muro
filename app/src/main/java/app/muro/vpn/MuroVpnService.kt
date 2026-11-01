package app.muro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.muro.R
import app.muro.muro
import app.muro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Parámetros de la interfaz virtual. */
object Tunnel {
    // Rango poco común: 10.0.0.x choca con muchos routers domésticos.
    const val ADDRESS = "10.111.222.1"
    const val DNS = "10.111.222.2"
    val DNS_BYTES = byteArrayOf(10, 111, 222.toByte(), 2)

    // Por aquí solo pasa DNS. Un MTU grande evita fragmentar respuestas largas, que de
    // otro modo el sistema reintentaría por TCP y este túnel no maneja TCP.
    const val MTU = 9_000
    const val MAX_DNS_PAYLOAD = MTU - Ipv4Udp.REPLY_OVERHEAD
}

sealed interface VpnStatus {
    data object Off : VpnStatus
    data object Starting : VpnStatus
    data class Active(val apps: Int) : VpnStatus

    /** Encendida, pero sin apps válidas: el túnel queda abajo a propósito. */
    data object NoApps : VpnStatus
}

class MuroVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var main: Job? = null

    @Volatile
    private var selected: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!goForeground()) return START_NOT_STICKY
        if (main == null) {
            _status.value = VpnStatus.Starting
            main = scope.launch { run() }
        }
        return START_STICKY
    }

    /** Otra VPN tomó el lugar o el usuario revocó el permiso desde Android. */
    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        _status.value = VpnStatus.Off
        super.onDestroy()
    }

    private fun shutdown() {
        val app = muro
        app.scope.launch { app.settings.setProtectionEnabled(false) }
        main?.cancel()
        main = null
        _status.value = VpnStatus.Off
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @OptIn(FlowPreview::class)
    private suspend fun run() = coroutineScope {
        val app = muro
        val upstream = Upstream()
        val logger = QueryLogger(app.db.log())
        val owners = AppResolver(this@MuroVpnService) { selected }
        val proxy = DnsProxy(
            // Una consulta lenta espera a la red, así que cada una necesita su hilo; el
            // límite evita que una ráfaga dispare cientos. El supervisor aísla cada consulta
            // para que un fallo en una no cancele el servicio entero.
            scope = CoroutineScope(
                coroutineContext + SupervisorJob(coroutineContext[Job]) + Dispatchers.IO.limitedParallelism(32),
            ),
            resolver = upstream,
            appOf = owners::resolve,
            log = logger::record,
        )

        launch { logger.run() }
        launch { app.lists.refreshIfStale() }
        launch {
            app.settings.flow.map { it.backupDns }.distinctUntilChanged()
                .collect { upstream.backupDns = it }
        }
        launch { blocklists().collect { proxy.blocklist = it } }

        // Android no deja cambiar las apps en caliente. El debounce junta varios toques
        // seguidos en un solo reinicio del túnel.
        app.settings.flow.map { it.selectedApps }.distinctUntilChanged().debounce(400)
            .collectLatest { apps -> tunnel(apps - packageName, proxy) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun blocklists(): Flow<Blocklist> {
        val app = muro
        val domains = app.db.domains()
        return app.settings.flow.map { it.enabledLists }.distinctUntilChanged()
            .flatMapLatest { lists ->
                combine(domains.observeBlocked(lists.map { it.name }), domains.observeAllowed()) { blocked, allowed ->
                    Blocklist(blocked.toHashSet(), allowed.toHashSet())
                }
            }
            .flowOn(Dispatchers.Default)
    }

    /** Mantiene un túnel para [apps] hasta que la corrutina se cancele. */
    private suspend fun tunnel(apps: Set<String>, proxy: DnsProxy) {
        selected = apps
        val builder = Builder()
            .setSession("Muro")
            .setMtu(Tunnel.MTU)
            .addAddress(Tunnel.ADDRESS, 32)
            .addDnsServer(Tunnel.DNS)
            // Solo se enruta la IP falsa: el resto del tráfico no toca la app.
            .addRoute(Tunnel.DNS, 32)
            // Sin esto Android bloquea la familia que la VPN no configura e IPv6 dejaría
            // de funcionar en las apps filtradas.
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .setBlocking(true)
            .setMetered(false)

        val added = apps.count { packageName ->
            try {
                builder.addAllowedApplication(packageName)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        // Sin ninguna app, addAllowedApplication no se llamó y la VPN cubriría TODO el
        // celular en lugar de nada.
        if (added == 0) {
            _status.value = VpnStatus.NoApps
            updateNotification("Elige al menos una app para filtrar")
            awaitCancellation()
        }

        val descriptor = try {
            builder.establish()
        } catch (e: RuntimeException) {
            Log.e(TAG, "No se pudo crear la VPN", e)
            null
        }
        if (descriptor == null) {
            shutdown() // Sin permiso de VPN, o lo tomó otra app
            return
        }

        lateinit var pump: TunPump
        pump = TunPump(descriptor) { packet, length ->
            Ipv4Udp.parseDnsQuery(packet, length, Tunnel.DNS_BYTES)?.let { query ->
                proxy.submit(query, pump::write)
            }
        }
        _status.value = VpnStatus.Active(added)
        updateNotification(filteringText(added))
        try {
            awaitCancellation()
        } finally {
            pump.close()
        }
    }

    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Iniciando…"), type)
        true
    } catch (e: RuntimeException) {
        // Android rechaza el tipo si la app perdió el permiso de VPN.
        Log.e(TAG, "No se pudo iniciar en primer plano", e)
        _status.value = VpnStatus.Off
        stopSelf()
        false
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Estado de la protección", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MuroVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle("Muro")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Detener", stop)
            .build()
    }

    companion object {
        private const val TAG = "MuroVpn"
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.muro.STOP"

        private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Off)
        val status: StateFlow<VpnStatus> = _status

        fun filteringText(apps: Int) = if (apps == 1) "Filtrando 1 app" else "Filtrando $apps apps"

        /** Quien llama debe haber confirmado el permiso con [VpnService.prepare]. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MuroVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MuroVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
