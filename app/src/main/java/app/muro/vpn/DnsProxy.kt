package app.muro.vpn

import android.util.Log
import app.muro.data.LogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Quién responde lo que no se decide localmente. Null si no hubo respuesta. */
fun interface Resolver {
    fun resolve(query: ByteArray, filtered: Boolean): ByteArray?
}

data class AppRef(val packageName: String, val label: String)

/** Decide qué responder a cada consulta DNS y la deja en el registro. */
class DnsProxy(
    private val scope: CoroutineScope,
    private val resolver: Resolver,
    private val appOf: (UdpDatagram) -> AppRef?,
    private val log: (LogEntry) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var blocklist: Blocklist = Blocklist.EMPTY

    fun submit(query: UdpDatagram, reply: (ByteArray) -> Unit) {
        scope.launch {
            // Los bytes vienen de otras apps y de la red: un mensaje raro se descarta y el
            // sistema reintenta, pero nunca debe llegar a detener la protección.
            try {
                answer(query)?.let { reply(Ipv4Udp.buildReply(query, it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Consulta descartada: $e")
            }
        }
    }

    /** Null cuando el paquete no es DNS válido: se descarta sin responder. */
    internal fun answer(query: UdpDatagram): ByteArray? {
        val question = DnsCodec.questionOf(query.payload) ?: return null
        // Antes de responder: al recibir la respuesta el sistema cierra el socket
        // y Android ya no podría decir de qué app era.
        val app = appOf(query)

        val verdict = blocklist.verdict(question.domain)
        var blocked = verdict == Verdict.BLOCK
        val response = if (blocked) {
            DnsCodec.nxDomain(query.payload)
        } else {
            val upstream = resolver.resolve(query.payload, filtered = verdict != Verdict.ALLOW)
            when {
                upstream == null || upstream.size > Tunnel.MAX_DNS_PAYLOAD ->
                    DnsCodec.serverFailure(query.payload)
                else -> {
                    blocked = verdict != Verdict.ALLOW && DnsCodec.isSinkholed(upstream)
                    // El 0.0.0.0 de AdGuard trae un TTL de una hora: las apps lo guardarían
                    // y "Permitir" no surtiría efecto hasta que venciera.
                    if (blocked) DnsCodec.nxDomain(query.payload) else upstream
                }
            }
        }

        // AdGuard bloquea A y AAAA con 0.0.0.0, pero a otros tipos (HTTPS) responde vacío.
        // Esa respuesta no dice nada del dominio y saldría como "Permitido" junto a la
        // fila "Bloqueado" de la misma visita.
        // Los nombres .arpa son sondas del propio Android, no de una app.
        val isSystemProbe = question.domain.endsWith(".arpa")
        if (!isSystemProbe && (blocked || question.isAddress || DnsCodec.hasAnswers(response))) {
            log(LogEntry(0, clock(), question.domain, app?.packageName, app?.label, blocked))
        }
        return response
    }

    private companion object {
        const val TAG = "MuroProxy"
    }
}
