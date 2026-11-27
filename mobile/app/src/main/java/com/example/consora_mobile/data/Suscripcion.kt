package com.example.consora_mobile.data

import com.example.consora_mobile.ui.Formatos
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lo que el usuario le paga a CONSORA por mes (plan + asistentes adicionales).
 * Planes según el documento de la tesis (2.8): Bronce y Oro, para administraciones y proveedores.
 */
data class Suscripcion(
    val plan: String,
    val lineas: List<Linea>,
    val proximoVencimiento: LocalDate,
    val pagada: Boolean,
    /** Mes que se está facturando, ej. "octubre 2026". */
    val periodo: String,
) {
    /** Un concepto de la factura: "Plan Oro", "Asistentes adicionales (2 × $ 8.000)", "IVA"... */
    data class Linea(val concepto: String, val detalle: String?, val importe: Double)

    val total: Double get() = lineas.sumOf { it.importe }
}

/**
 * TODO: por ahora son datos de ejemplo armados en código.
 * Más adelante saldrán de tablas en la base (plan, precio, asistentes, facturas emitidas).
 */
object SuscripcionRepository {

    private const val PRECIO_ASISTENTE = 8_000.0
    private const val IVA = 0.21

    private val preciosAdministracion = mapOf("Bronce" to 25_000.0, "Oro" to 45_000.0)
    private val preciosProveedor = mapOf("Bronce" to 15_000.0, "Oro" to 30_000.0)

    /** Suscripción del usuario, o null si su rol no paga suscripción (propietarios, asistentes). */
    fun para(usuario: Usuario): Suscripcion? {
        val (plan, precio, asistentes) = when {
            usuario.administrador != null -> Triple("Oro", preciosAdministracion.getValue("Oro"), 2)
            usuario.rol == "Proveedor" -> Triple("Bronce", preciosProveedor.getValue("Bronce"), 0)
            else -> return null
        }

        val lineas = mutableListOf(Suscripcion.Linea("Plan $plan", "Mensual", precio))
        if (asistentes > 0) {
            lineas += Suscripcion.Linea(
                "Asistentes adicionales",
                "$asistentes × ${Formatos.moneda(PRECIO_ASISTENTE)}",
                asistentes * PRECIO_ASISTENTE,
            )
        }
        val subtotal = lineas.sumOf { it.importe }
        lineas += Suscripcion.Linea("IVA", "21 %", subtotal * IVA)

        // Vence el día 10 del mes siguiente
        val hoy = LocalDate.now()
        val vencimiento = hoy.plusMonths(1).withDayOfMonth(10)
        val periodo = hoy.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "AR")))
        return Suscripcion(plan, lineas, vencimiento, pagada = usuario.id in pagados, periodo = periodo)
    }

    /**
     * Usuarios que ya "pagaron" en esta ejecución. Se guarda solo en memoria a propósito:
     * al cerrar y volver a abrir la app, la factura vuelve a figurar pendiente (para la demo).
     */
    private val pagados = mutableSetOf<Long>()

    /** Pago simulado: marca la factura del usuario como pagada hasta que se reinicie la app. */
    fun registrarPago(usuario: Usuario) {
        pagados += usuario.id
    }
}
