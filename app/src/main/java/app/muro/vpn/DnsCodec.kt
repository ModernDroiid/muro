package app.muro.vpn

import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.io.IOException

/** Lo que pregunta una consulta. [isAddress] es la pregunta por la IPv4 del dominio. */
data class DnsQuestion(val domain: String, val isAddress: Boolean)

/** Lectura y armado de mensajes DNS con dnsjava. */
object DnsCodec {

    /** Devuelve la pregunta con el dominio en minúsculas, o null si el mensaje no es válido. */
    fun questionOf(query: ByteArray): DnsQuestion? = try {
        Message(query).question?.let { question ->
            val domain = question.name.toString(true).lowercase()
            DnsQuestion(domain, question.type == Type.A).takeIf { domain.isNotEmpty() }
        }
    } catch (_: IOException) {
        null
    }

    /** Exige mismo ID, bandera de respuesta y la misma pregunta que se hizo. */
    fun isResponseTo(query: ByteArray, response: ByteArray): Boolean = try {
        val asked = Message(query)
        val got = Message(response)
        got.header.id == asked.header.id &&
            got.header.getFlag(Flags.QR.toInt()) &&
            got.question == asked.question
    } catch (_: IOException) {
        false
    }

    fun hasAnswers(response: ByteArray): Boolean = try {
        Message(response).getSection(Section.ANSWER).isNotEmpty()
    } catch (_: IOException) {
        false
    }

    /** Respuesta "no existe": la app no recibe IP y el anuncio no carga. */
    fun nxDomain(query: ByteArray): ByteArray = reply(query, Rcode.NXDOMAIN)

    fun serverFailure(query: ByteArray): ByteArray = reply(query, Rcode.SERVFAIL)

    /**
     * AdGuard no responde "no existe" al bloquear: devuelve `200 OK` con la dirección
     * 0.0.0.0 (o ::). Hay que mirar el contenido o se registraría como permitido.
     */
    fun isSinkholed(response: ByteArray): Boolean = try {
        Message(response).getSection(Section.ANSWER).any { record ->
            when (record) {
                is ARecord -> record.address.isAnyLocalAddress
                is AAAARecord -> record.address.isAnyLocalAddress
                else -> false
            }
        }
    } catch (_: IOException) {
        false
    }

    private fun reply(query: ByteArray, rcode: Int): ByteArray {
        val request = Message(query)
        return Message(request.header.id).apply {
            header.setFlag(Flags.QR.toInt())
            header.setFlag(Flags.RA.toInt())
            if (request.header.getFlag(Flags.RD.toInt())) header.setFlag(Flags.RD.toInt())
            header.rcode = rcode
            request.question?.let { addRecord(it, Section.QUESTION) }
        }.toWire()
    }
}
