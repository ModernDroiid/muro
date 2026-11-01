package app.muro.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un anuncio móvil pesa en torno a 30 KB; es la cifra de la que sale "38 MB" del mockup. */
private const val SAVED_KB_PER_BLOCK = 30L

/** "1.284": es-ES no agrupa los números de cuatro cifras, Alemania usa el mismo formato. */
fun formatCount(value: Int): String = String.format(Locale.GERMANY, "%,d", value)

fun formatSaved(blocked: Int): String {
    val kb = blocked * SAVED_KB_PER_BLOCK
    return when {
        kb < 1_024 -> "$kb KB"
        kb < 1_024 * 1_024 -> "${(kb + 512) / 1_024} MB"
        else -> String.format(Locale.GERMANY, "%.1f GB", kb / (1_024.0 * 1_024.0))
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** El registro guarda 7 días: las filas de otro día necesitan la fecha. */
fun formatTime(timestamp: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val moment = Instant.ofEpochMilli(timestamp).atZone(zone)
    return moment.format(if (moment.toLocalDate() == today) TIME else DATE_TIME)
}
