package app.muro.data

import java.net.IDN

object Domains {
    private val HOSTNAME = Regex("^(?=.{4,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\\.)+[a-z0-9-]{2,63}$")
    private val IP_V4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val HOSTS_SINK = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?)$")

    /** Limpia lo que escribe el usuario, incluso una URL pegada. Null si no es un dominio. */
    fun normalize(input: String): String? {
        val host = input.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .removeSuffix(".")
        val ascii = try {
            IDN.toASCII(host)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return ascii.takeIf { HOSTNAME.matches(it) && !IP_V4.matches(it) }
    }

    /** Entiende archivos hosts ("0.0.0.0 ads.x.com") y listas de un dominio por línea. */
    fun parseList(lines: Sequence<String>): Set<String> {
        val domains = HashSet<String>()
        for (line in lines) {
            val content = line.substringBefore('#').trim()
            if (content.isEmpty()) continue
            val tokens = content.split(WHITESPACE)
            val candidate = if (tokens.size > 1 && HOSTS_SINK.matches(tokens[0])) tokens[1] else tokens[0]
            normalize(candidate)?.let(domains::add)
        }
        return domains
    }

    private val WHITESPACE = Regex("\\s+")
}
