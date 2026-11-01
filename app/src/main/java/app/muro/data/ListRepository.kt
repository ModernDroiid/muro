package app.muro.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Las listas vienen dentro de la app, así funcionan sin red, y se renuevan cada semana. */
class ListRepository(
    private val context: Context,
    private val dao: DomainDao,
    private val settings: Settings,
) {
    private val client by lazy {
        OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    }

    fun custom(): Flow<List<String>> = dao.observeList(ListId.CUSTOM.name)
    fun allowed(): Flow<List<String>> = dao.observeAllowed()

    suspend fun seedIfEmpty() {
        for (source in SOURCES) {
            if (dao.count(source.list.name) > 0) continue
            val domains = withContext(Dispatchers.IO) {
                context.assets.open(source.asset).bufferedReader().useLines(Domains::parseList)
            }
            dao.replaceList(source.list.name, domains)
        }
        if (settings.flow.first().listsUpdatedAt == 0L) {
            settings.setListsUpdatedAt(System.currentTimeMillis())
        }
    }

    suspend fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - settings.flow.first().listsUpdatedAt < REFRESH_INTERVAL_MS) return

        var allUpdated = true
        for (source in SOURCES) {
            val domains = try {
                withContext(Dispatchers.IO) { download(source.url) }
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo actualizar ${source.list}: ${e.message}")
                null
            }
            // Una descarga truncada o una página de error no debe vaciar la lista.
            if (domains != null && domains.size >= MIN_DOMAINS) {
                dao.replaceList(source.list.name, domains)
            } else {
                allUpdated = false
            }
        }
        if (allUpdated) settings.setListsUpdatedAt(now)
    }

    /** Bloquear gana a un permiso anterior, y Mi lista se enciende para que surta efecto. */
    suspend fun block(domain: String) {
        dao.removeAllowed(domain)
        dao.insertAll(listOf(BlockedDomain(ListId.CUSTOM.name, domain)))
        settings.setListEnabled(ListId.CUSTOM, true)
    }

    suspend fun allow(domain: String) {
        dao.removeBlocked(ListId.CUSTOM.name, domain)
        dao.allow(AllowedDomain(domain))
    }

    suspend fun removeCustom(domain: String) = dao.removeBlocked(ListId.CUSTOM.name, domain)

    suspend fun removeAllowed(domain: String) = dao.removeAllowed(domain)

    private fun download(url: String): Set<String> {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Respuesta vacía")
            return body.charStream().buffered().useLines(Domains::parseList)
        }
    }

    private class Source(val list: ListId, val asset: String, val url: String)

    private companion object {
        const val TAG = "MuroLists"
        const val MIN_DOMAINS = 1_000
        val REFRESH_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
        val SOURCES = listOf(
            Source(ListId.ADS, "lists/ads.txt", "https://adaway.org/hosts.txt"),
            Source(ListId.TRACKERS, "lists/trackers.txt", "https://v.firebog.net/hosts/Easyprivacy.txt"),
        )
    }
}
