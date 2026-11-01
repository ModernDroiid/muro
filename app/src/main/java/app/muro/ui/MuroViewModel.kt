package app.muro.ui

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.muro.data.ListId
import app.muro.data.LogEntry
import app.muro.data.UserSettings
import app.muro.muro
import app.muro.vpn.MuroVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class InstalledApp(val packageName: String, val label: String)

enum class LogFilter(val label: String, val blocked: Boolean?) {
    ALL("Todos", null),
    BLOCKED("Bloqueados", true),
    ALLOWED("Permitidos", false),
}

@OptIn(ExperimentalCoroutinesApi::class)
class MuroViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.muro
    private val log = app.db.log()

    val status = MuroVpnService.status
    val settings: StateFlow<UserSettings?> = app.settings.flow.state(null)

    /** Se revisa cada minuto para que "hoy" pase solo a medianoche con la app abierta. */
    private val startOfToday = flow {
        while (true) {
            emit(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val blockedToday: StateFlow<Int> = startOfToday.flatMapLatest(log::blockedSince).state(0)

    val blockedByApp: StateFlow<Map<String, Int>> = startOfToday
        .flatMapLatest(log::blockedByAppSince)
        .map { rows -> rows.associate { it.packageName to it.blocked } }
        .state(emptyMap())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    val logFilter = MutableStateFlow(LogFilter.ALL)
    val logEntries: StateFlow<List<LogEntry>> = logFilter
        .flatMapLatest { log.recent(it.blocked, LOG_LIMIT) }
        .state(emptyList())

    val customDomains: StateFlow<List<String>> = app.lists.custom().state(emptyList())
    val allowedDomains: StateFlow<List<String>> = app.lists.allowed().state(emptyList())

    /** Servidor del DNS privado en modo estricto, que se salta a Muro. */
    private val _strictPrivateDns = MutableStateFlow<String?>(null)
    val strictPrivateDns: StateFlow<String?> = _strictPrivateDns

    init {
        refreshDeviceState()
    }

    fun refreshDeviceState() {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        _strictPrivateDns.value = connectivity.activeNetwork
            ?.let(connectivity::getLinkProperties)
            ?.privateDnsServerName

        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadApps() }
        }
    }

    private fun loadApps(): List<InstalledApp> {
        val packages = app.packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launchable, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != app.packageName }
            .map { InstalledApp(it.packageName, packages.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    fun startProtection() = update {
        app.settings.setProtectionEnabled(true)
        MuroVpnService.start(app)
    }

    fun stopProtection() = MuroVpnService.stop(app)

    fun setAppSelected(packageName: String, selected: Boolean) = update { app.settings.setAppSelected(packageName, selected) }
    fun setListEnabled(list: ListId, enabled: Boolean) = update { app.settings.setListEnabled(list, enabled) }
    fun setStartOnBoot(enabled: Boolean) = update { app.settings.setStartOnBoot(enabled) }
    fun setBackupDns(server: String) = update { app.settings.setBackupDns(server) }
    fun block(domain: String) = update { app.lists.block(domain) }
    fun allow(domain: String) = update { app.lists.allow(domain) }
    fun removeCustom(domain: String) = update { app.lists.removeCustom(domain) }
    fun removeAllowed(domain: String) = update { app.lists.removeAllowed(domain) }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private companion object {
        const val LOG_LIMIT = 200
    }
}
