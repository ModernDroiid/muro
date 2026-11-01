package app.muro.vpn

import app.muro.data.LogDao
import app.muro.data.LogEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * Escribe el registro sin frenar las consultas. El sistema pide cada dominio varias veces
 * (A, AAAA, reintentos): se guarda una sola fila para que los contadores cuenten dominios
 * cargados y no paquetes.
 */
class QueryLogger(private val dao: LogDao) {
    private val pending = Channel<LogEntry>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val deduplicator = Deduplicator()

    fun record(entry: LogEntry) {
        pending.trySend(entry)
    }

    suspend fun run() {
        for (first in pending) {
            val batch = mutableListOf(first)
            while (batch.size < 64) batch += pending.tryReceive().getOrNull() ?: break
            val fresh = batch.filter(deduplicator::isFresh)
            if (fresh.isNotEmpty()) dao.insertAll(fresh)
        }
    }

    /** "No existe" no se guarda en caché y las apps reintentan a los pocos segundos. */
    internal class Deduplicator(private val windowMs: Long = 10_000) {
        private val lastSeen = HashMap<String, Long>()

        fun isFresh(entry: LogEntry): Boolean {
            if (lastSeen.size > 2_000) lastSeen.clear()
            val key = "${entry.packageName}|${entry.domain}|${entry.blocked}"
            val previous = lastSeen.put(key, entry.timestamp)
            return previous == null || entry.timestamp - previous > windowMs
        }
    }
}
