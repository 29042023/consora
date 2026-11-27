package com.example.consora_mobile.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatos de fecha y moneda usados en la app (Argentina). */
object Formatos {
    private val locale = Locale("es", "AR")
    private val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val diaLargo = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)

    fun fecha(date: LocalDate): String = date.format(fecha)

    /** "yyyy-MM-dd" -> "dd/MM/yyyy" */
    fun fecha(iso: String): String = runCatching { fecha(LocalDate.parse(iso)) }.getOrDefault(iso)

    /** "yyyy-MM-dd" -> "Hoy", "Mañana" o "jueves 23 de octubre" */
    fun dia(iso: String): String {
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val hoy = LocalDate.now()
        return when (date) {
            hoy -> "Hoy"
            hoy.plusDays(1) -> "Mañana"
            else -> date.format(diaLargo).replaceFirstChar { it.uppercase() }
        }
    }

    fun moneda(importe: Double): String = NumberFormat.getCurrencyInstance(locale).format(importe)

    /** "Carlos Pérez" -> "CP" */
    fun iniciales(nombre: String): String =
        nombre.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    /** "yyyy-MM-dd HH:mm:ss" -> "dd/MM HH:mm" */
    fun fechaHora(fechaHora: String): String = "${fecha(fechaHora.take(10)).take(5)} ${hora(fechaHora)}"

    /** "yyyy-MM-dd HH:mm:ss" -> "HH:mm" */
    fun hora(fechaHora: String): String = fechaHora.substring(11, 16)

    /** Para la lista de chats: hora si es de hoy, "Ayer", o la fecha corta. */
    fun horaChat(fechaHora: String): String {
        val dia = fechaHora.take(10)
        val hoy = LocalDate.now()
        return when (dia) {
            hoy.toString() -> hora(fechaHora)
            hoy.minusDays(1).toString() -> "Ayer"
            else -> fecha(dia).take(5)
        }
    }

    /** Separador de días dentro de una conversación: "Hoy", "Ayer" o "jueves 23 de octubre". */
    fun diaChat(fechaHora: String): String {
        val fechaIso = fechaHora.take(10)
        return if (fechaIso == LocalDate.now().minusDays(1).toString()) "Ayer" else dia(fechaIso)
    }
}
