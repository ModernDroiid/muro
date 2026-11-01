package app.muro.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.system.OsConstants
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/** Averigua qué app hizo una consulta, para el registro y los contadores por app. */
class AppResolver(context: Context, private val selected: () -> Set<String>) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val packages = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()

    fun resolve(query: UdpDatagram): AppRef? {
        val uid = ownerUid(query)
        val candidates = selected()
        val packageName = if (uid >= 0) {
            packages.getPackagesForUid(uid)?.firstOrNull { it in candidates }
        } else {
            // Sin dueño conocido, solo se puede asegurar si hay una única app filtrada.
            candidates.singleOrNull()
        } ?: return null
        return AppRef(packageName, labelOf(packageName))
    }

    private fun ownerUid(query: UdpDatagram): Int = try {
        connectivity.getConnectionOwnerUid(
            OsConstants.IPPROTO_UDP,
            InetSocketAddress(InetAddress.getByAddress(query.srcIp), query.srcPort),
            InetSocketAddress(InetAddress.getByAddress(query.dstIp), query.dstPort),
        )
    } catch (_: SecurityException) {
        // Solo la VPN activa puede preguntar; ocurre durante un reinicio del túnel.
        -1
    }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
