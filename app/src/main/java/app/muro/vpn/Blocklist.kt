package app.muro.vpn

enum class Verdict {
    /** Está en una lista de bloqueo: se responde "no existe" sin salir del celular. */
    BLOCK,

    /** El usuario lo permitió: se resuelve sin filtros, ni siquiera los de AdGuard. */
    ALLOW,

    /** Sin decisión local: lo decide el filtro de AdGuard. */
    FORWARD,
}

/**
 * Foto inmutable de las listas activas. El servicio la reemplaza entera cuando cambian,
 * así las consultas en curso nunca ven una lista a medio actualizar.
 */
class Blocklist(private val blocked: Set<String>, private val allowed: Set<String>) {

    /** Una regla cubre sus subdominios. Permitir siempre gana sobre bloquear. */
    fun verdict(domain: String): Verdict {
        var isBlocked = false
        var suffix = domain
        while (true) {
            if (suffix in allowed) return Verdict.ALLOW
            if (suffix in blocked) isBlocked = true
            val dot = suffix.indexOf('.')
            // Se detiene antes del TLD solo: nunca se compara "com" o "net" a secas.
            if (dot < 0 || suffix.indexOf('.', dot + 1) < 0) break
            suffix = suffix.substring(dot + 1)
        }
        return if (isBlocked) Verdict.BLOCK else Verdict.FORWARD
    }

    companion object {
        val EMPTY = Blocklist(emptySet(), emptySet())
    }
}
