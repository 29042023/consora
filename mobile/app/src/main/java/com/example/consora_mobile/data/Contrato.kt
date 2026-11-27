package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.floor

/** Forma de pago de un contrato: al contado (1 pago) o en N cuotas. */
data class FormaPago(val id: Long, val cuotas: Int) {
    val esContado get() = cuotas == 1
}

/** Trabajo contratado a un proveedor por un consorcio (misma estructura que en la web). */
data class Contrato(
    val id: Long,
    val consorcioId: Long,
    val consorcio: String,
    val representanteId: Long,
    val proveedorId: Long,
    val proveedor: String,
    val descripcion: String,
    val precio: Double,
    val cuotas: Int,
    /** "vigente" | "finalizado" | "cancelado" */
    val estado: String,
    /** Vencimiento de la primera cuota (yyyy-MM-dd). */
    val fecha: String,
    val votacionId: Long?,
    val cantCuotas: Int,
    val cantPagas: Int,
    val pagado: Double,
    /** Próxima cuota sin pagar: vencimiento (yyyy-MM-dd) e importe. */
    val proximaVence: String?,
    val proximaImporte: Double?,
    /** Fecha en que se terminó el trabajo (yyyy-MM-dd), o null si sigue en proceso. */
    val trabajoTerminadoEn: String? = null,
    /** Contrato formal firmado (PDF guardado en la app). */
    val contratoPdf: String? = null,
    val proveedorImg: String? = null,
    val consorcioImg: String? = null,
) {
    val esContado get() = cuotas == 1
    val trabajoTerminado get() = trabajoTerminadoEn != null
    val saldo get() = if (estado == CANCELADO) 0.0 else precio - pagado
    val porcentaje get() = if (cantCuotas == 0) 0 else cantPagas * 100 / cantCuotas

    companion object {
        const val VIGENTE = "vigente"
        const val FINALIZADO = "finalizado"
        const val CANCELADO = "cancelado"
    }
}

/** Cuota de un contrato: es una deuda del consorcio, paga cuando se le carga su factura. */
data class Cuota(
    val deudaId: Long,
    val nro: Int,
    val importe: Double,
    val vencimiento: String,
    /** Fecha y comprobante de la factura con que se pagó, o null si está pendiente. */
    val pagadaEl: String?,
    val comprobante: String?,
) {
    val pagada get() = pagadaEl != null
}

/** Proveedor con su foto, a qué se dedica y su puntaje (para elegirlo en un contrato). */
data class ProveedorInfo(val id: Long, val nombre: String, val img: String?, val info: String, val promedio: Double?, val cantResenas: Int)

/** Lo que pasó en un edificio. */
data class EventoHistorial(
    val id: Long,
    val consorcioId: Long,
    val consorcio: String,
    /** "contrato" | "pago" | "votacion" | "reunion" | "obra" | "otro" */
    val categoria: String,
    val descripcion: String,
    val usuario: String?,
    /** yyyy-MM-dd */
    val fecha: String,
    /** HH:mm:ss */
    val hora: String,
    val manual: Boolean,
    val representanteId: Long,
) {
    companion object {
        val CATEGORIAS = listOf("contrato", "pago", "votacion", "reunion", "obra", "otro")
        /** Las que el administrador carga a mano; las demás se registran solas. */
        val MANUALES = listOf("obra", "otro")
    }
}

/** Registra un evento en el historial del edificio (dentro de la transacción que lo produjo). */
fun SQLiteDatabase.registrarHistorial(consorcioId: Long, categoria: String, descripcion: String, usuarioId: Long?) {
    val ahora = LocalDateTime.now()
    insertOrThrow("historial_consorcio", null, ContentValues().apply {
        put("id_consorcio", consorcioId)
        put("categoria", categoria)
        put("descripcion", descripcion.take(255))
        put("id_user", usuarioId)
        put("fecha", ahora.toLocalDate().toString())
        put("time", ahora.toLocalTime().withNano(0).toString().let { if (it.length == 5) "$it:00" else it })
        put("manual", 0)
    })
}

/** "$ 185.000" (sin centavos si son cero), igual que en la web. */
fun monedaCorta(importe: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("es", "AR")).apply {
        minimumFractionDigits = if (importe % 1.0 == 0.0) 0 else 2
        maximumFractionDigits = 2
    }
    return "$ " + nf.format(importe)
}

class ContratoRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    private val select = """
        SELECT k.id_contrato_proveedor, k.id_consorcio, c.name_consorcio, c.representante, k.id_proveedor, u.name_surname,
               k.descripcion, k.precio, fp.cuotas, k.estado, k.fecha, v.id_votacion,
               (SELECT COUNT(*) FROM deuda d WHERE d.id_contrato_proveedor = k.id_contrato_proveedor),
               (SELECT COUNT(*) FROM deuda d JOIN factura f ON f.id_deuda = d.id_deuda WHERE d.id_contrato_proveedor = k.id_contrato_proveedor),
               (SELECT COALESCE(SUM(d.importe), 0) FROM deuda d JOIN factura f ON f.id_deuda = d.id_deuda WHERE d.id_contrato_proveedor = k.id_contrato_proveedor),
               (SELECT d.vencimiento FROM deuda d WHERE d.id_contrato_proveedor = k.id_contrato_proveedor
                    AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.id_deuda = d.id_deuda) ORDER BY d.vencimiento LIMIT 1),
               (SELECT d.importe FROM deuda d WHERE d.id_contrato_proveedor = k.id_contrato_proveedor
                    AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.id_deuda = d.id_deuda) ORDER BY d.vencimiento LIMIT 1),
               k.trabajo_terminado_en, k.contrato_pdf, u.img, c.img_consorcio
        FROM contratos_proveedores k
        JOIN consorcio c ON c.id_consorcio = k.id_consorcio
        JOIN user u ON u.id_user = k.id_proveedor
        JOIN forma_pago_contrato fp ON fp.id_forma_pago_contrato = k.id_forma_pago
        LEFT JOIN votacion_contrato v ON v.id_votacion_contrato = k.id_votacion_contrato
    """

    private fun leer(sql: String, args: Array<String>): List<Contrato> =
        db.readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Contrato(
                        id = c.getLong(0), consorcioId = c.getLong(1), consorcio = c.getString(2), representanteId = c.getLong(3),
                        proveedorId = c.getLong(4), proveedor = c.getString(5), descripcion = c.getString(6), precio = c.getDouble(7),
                        cuotas = c.getInt(8), estado = c.getString(9), fecha = c.getString(10),
                        votacionId = if (c.isNull(11)) null else c.getLong(11),
                        cantCuotas = c.getInt(12), cantPagas = c.getInt(13), pagado = c.getDouble(14),
                        proximaVence = if (c.isNull(15)) null else c.getString(15),
                        proximaImporte = if (c.isNull(16)) null else c.getDouble(16),
                        trabajoTerminadoEn = if (c.isNull(17)) null else c.getString(17),
                        contratoPdf = if (c.isNull(18)) null else c.getString(18),
                        proveedorImg = if (c.isNull(19)) null else c.getString(19),
                        consorcioImg = if (c.isNull(20)) null else c.getString(20),
                    )
                )
            }
        }

    /** El proveedor ve sus contratos; el resto, los de los consorcios que ve (o los de uno). */
    suspend fun listar(usuario: Usuario, consorcioId: Long? = null): List<Contrato> = withContext(Dispatchers.IO) {
        val (filtro, args) = if (usuario.esProveedor) "k.id_proveedor = ?" to mutableListOf(usuario.id.toString())
            else "k.id_consorcio IN (${usuario.sqlConsorciosVisibles})" to mutableListOf(usuario.id.toString())
        val porConsorcio = if (consorcioId != null) { args.add(consorcioId.toString()); " AND k.id_consorcio = ?" } else ""
        leer("$select WHERE $filtro$porConsorcio ORDER BY k.fecha DESC, k.id_contrato_proveedor DESC", args.toTypedArray())
    }

    suspend fun detalle(id: Long): Contrato? = withContext(Dispatchers.IO) {
        leer("$select WHERE k.id_contrato_proveedor = ?", arrayOf(id.toString())).firstOrNull()
    }

    suspend fun cuotas(contratoId: Long): List<Cuota> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT d.id_deuda, d.nro_cuota, d.importe, d.vencimiento, f.fecha, f.nro_comprobante
               FROM deuda d LEFT JOIN factura f ON f.id_deuda = d.id_deuda
               WHERE d.id_contrato_proveedor = ? ORDER BY d.nro_cuota""",
            arrayOf(contratoId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Cuota(c.getLong(0), c.getInt(1), c.getDouble(2), c.getString(3),
                        if (c.isNull(4)) null else c.getString(4), if (c.isNull(5)) null else c.getString(5))
                )
            }
        }
    }

    suspend fun formasPago(): List<FormaPago> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT id_forma_pago_contrato, cuotas FROM forma_pago_contrato ORDER BY cuotas", null)
            .use { c -> buildList { while (c.moveToNext()) add(FormaPago(c.getLong(0), c.getInt(1))) } }
    }

    /** Propuesta ganadora de una votación cerrada que todavía no tiene contrato cargado. */
    suspend fun propuestaSinContrato(votacionId: Long): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT vc.id_votacion_contrato, vc.id_proveedor FROM votacion_contrato vc
               WHERE vc.id_votacion = ? AND vc.contratado = 1
                 AND NOT EXISTS (SELECT 1 FROM contratos_proveedores k WHERE k.id_votacion_contrato = vc.id_votacion_contrato)""",
            arrayOf(votacionId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }
    }

    /** Contrato cargado desde una votación, si existe. */
    suspend fun deVotacion(votacionId: Long): Long? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT k.id_contrato_proveedor FROM contratos_proveedores k
               JOIN votacion_contrato vc ON vc.id_votacion_contrato = k.id_votacion_contrato WHERE vc.id_votacion = ?""",
            arrayOf(votacionId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /** Crea el contrato y una deuda por cuota (una sola al contado), que aparecen en Facturación. */
    suspend fun crear(
        adminId: Long, consorcioId: Long, proveedorId: Long, proveedor: String, descripcion: String,
        precio: Double, forma: FormaPago, primerPago: LocalDate, propuestaId: Long? = null,
        pdf: ArchivoGuardado? = null,
    ): Long = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val id = w.insertOrThrow("contratos_proveedores", null, ContentValues().apply {
                put("id_proveedor", proveedorId)
                put("id_consorcio", consorcioId)
                put("id_votacion_contrato", propuestaId)
                put("descripcion", descripcion)
                put("precio", precio)
                put("id_forma_pago", forma.id)
                put("fecha", primerPago.toString())
                put("id_user", adminId)
                put("contrato_pdf", pdf?.ruta)
            })
            val tipo = w.rawQuery("SELECT id_tipo_deuda FROM tipo_deuda WHERE nombre = ?", arrayOf(TIPO_DEUDA))
                .use { if (it.moveToFirst()) it.getLong(0) else null }
                ?: w.insertOrThrow("tipo_deuda", null, ContentValues().apply { put("nombre", TIPO_DEUDA) })
            val n = forma.cuotas
            // Cuotas iguales; la última absorbe los centavos que sobran
            val base = floor(precio / n * 100) / 100
            for (i in 1..n) {
                w.insertOrThrow("deuda", null, ContentValues().apply {
                    put("id_consorcio", consorcioId)
                    put("id_tipo_deuda", tipo)
                    put("descripcion", "$descripcion · " + if (n == 1) "pago único" else "cuota $i/$n")
                    put("proveedor", proveedor)
                    put("importe", if (i < n) base else Math.round((precio - base * (n - 1)) * 100) / 100.0)
                    put("vencimiento", primerPago.plusMonths((i - 1).toLong()).toString())
                    put("id_contrato_proveedor", id)
                    put("nro_cuota", i)
                })
            }
            w.registrarHistorial(consorcioId, "contrato",
                "Se contrató a $proveedor: $descripcion (${monedaCorta(precio)}, ${if (n == 1) "contado" else "$n cuotas"}).", adminId)
            w.setTransactionSuccessful()
            id
        } finally {
            w.endTransaction()
        }
    }

    /** Marca el trabajo como terminado (lo hacen el administrador o el mismo proveedor). */
    suspend fun terminarTrabajo(c: Contrato, fecha: LocalDate, usuarioId: Long) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.update("contratos_proveedores", ContentValues().apply { put("trabajo_terminado_en", fecha.toString()) },
            "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
        w.registrarHistorial(c.consorcioId, "obra", "${c.proveedor} terminó el trabajo: ${c.descripcion}.", usuarioId)
    }

    /** Vuelve el trabajo a "en proceso" (solo el administrador). */
    suspend fun reabrirTrabajo(c: Contrato) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("contratos_proveedores", ContentValues().apply { putNull("trabajo_terminado_en") },
            "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
    }

    /** Guarda (o reemplaza) el contrato formal en PDF. */
    suspend fun guardarPdf(c: Contrato, pdf: ArchivoGuardado, usuarioId: Long) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.update("contratos_proveedores", ContentValues().apply { put("contrato_pdf", pdf.ruta) },
            "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
        Archivos.borrar(c.contratoPdf)
        w.registrarHistorial(c.consorcioId, "contrato", "Se cargó el contrato firmado con ${c.proveedor}: ${c.descripcion}.", usuarioId)
    }

    suspend fun quitarPdf(c: Contrato) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("contratos_proveedores", ContentValues().apply { putNull("contrato_pdf") },
            "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
        Archivos.borrar(c.contratoPdf)
    }

    /** Proveedores activos con lo que muestra el selector: foto, descripción del Marketplace y reseñas. */
    suspend fun proveedoresConInfo(): List<ProveedorInfo> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT u.id_user, u.name_surname, u.img, cm.descripcion, u.mail,
                      (SELECT AVG(r.estrellas) FROM resenas r WHERE r.cuentas_marketplace = cm.id_cuentas_marketplace),
                      (SELECT COUNT(*) FROM resenas r WHERE r.cuentas_marketplace = cm.id_cuentas_marketplace)
               FROM user u JOIN roles ro ON ro.id_rol = u.role
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               LEFT JOIN cuentas_marketplace cm ON cm.dueno = u.id_user
               WHERE ro.rol = 'Proveedor' AND (e.estado IS NULL OR e.estado = 'Activa') ORDER BY u.name_surname""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ProveedorInfo(
                    c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getString(2),
                    (if (c.isNull(3)) null else c.getString(3)) ?: c.getString(4),
                    if (c.isNull(5)) null else c.getDouble(5), c.getInt(6),
                ))
            }
        }
    }

    /** Cancela el contrato: las cuotas sin pagar dejan de ser deuda. Devuelve cuántas se quitaron. */
    suspend fun cancelar(c: Contrato, usuarioId: Long): Int = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val quitadas = w.delete("deuda",
                "id_contrato_proveedor = ? AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.id_deuda = deuda.id_deuda)",
                arrayOf(c.id.toString()))
            w.update("contratos_proveedores", ContentValues().apply { put("estado", Contrato.CANCELADO) },
                "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
            w.registrarHistorial(c.consorcioId, "contrato", "Se canceló el contrato con ${c.proveedor}: ${c.descripcion}.", usuarioId)
            w.setTransactionSuccessful()
            quitadas
        } finally {
            w.endTransaction()
        }
    }

    /** Elimina un contrato sin cuotas pagas, junto con sus cuotas. */
    suspend fun eliminar(c: Contrato) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("deuda", "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
            w.delete("contratos_proveedores", "id_contrato_proveedor = ?", arrayOf(c.id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    companion object {
        const val TIPO_DEUDA = "Contrato con proveedor"

        /** Después de pagar una cuota: si era la última, el contrato pasa a finalizado. */
        fun actualizarEstado(w: SQLiteDatabase, deudaId: Long, usuarioId: Long) {
            val datos = w.rawQuery(
                """SELECT k.id_contrato_proveedor, k.id_consorcio, u.name_surname, k.descripcion,
                          (SELECT COUNT(*) FROM deuda d WHERE d.id_contrato_proveedor = k.id_contrato_proveedor
                               AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.id_deuda = d.id_deuda))
                   FROM deuda d0 JOIN contratos_proveedores k ON k.id_contrato_proveedor = d0.id_contrato_proveedor
                   JOIN user u ON u.id_user = k.id_proveedor
                   WHERE d0.id_deuda = ? AND k.estado = 'vigente'""",
                arrayOf(deudaId.toString()),
            ).use { if (it.moveToFirst()) arrayOf(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getInt(4)) else null }
                ?: return
            if (datos[4] as Int > 0) return
            w.update("contratos_proveedores", ContentValues().apply { put("estado", Contrato.FINALIZADO) },
                "id_contrato_proveedor = ?", arrayOf(datos[0].toString()))
            w.registrarHistorial(datos[1] as Long, "contrato", "Se terminó de pagar el contrato con ${datos[2]}: ${datos[3]}.", usuarioId)
        }
    }
}

class HistorialRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    /** Historial de los edificios que ve el usuario (o de uno), del más nuevo al más viejo. */
    suspend fun listar(usuario: Usuario, consorcioId: Long? = null): List<EventoHistorial> = withContext(Dispatchers.IO) {
        val args = mutableListOf(usuario.id.toString())
        val porConsorcio = if (consorcioId != null) { args.add(consorcioId.toString()); " AND h.id_consorcio = ?" } else ""
        db.readableDatabase.rawQuery(
            """SELECT h.id_historial_consorcio, h.id_consorcio, c.name_consorcio, h.categoria, h.descripcion, u.name_surname,
                      h.fecha, h.time, h.manual, c.representante
               FROM historial_consorcio h
               JOIN consorcio c ON c.id_consorcio = h.id_consorcio
               LEFT JOIN user u ON u.id_user = h.id_user
               WHERE h.id_consorcio IN (${usuario.sqlConsorciosVisibles})$porConsorcio
               ORDER BY h.fecha DESC, h.time DESC, h.id_historial_consorcio DESC LIMIT 300""",
            args.toTypedArray(),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    EventoHistorial(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                        if (c.isNull(5)) null else c.getString(5), c.getString(6), c.getString(7), c.getInt(8) == 1, c.getLong(9),
                    )
                )
            }
        }
    }

    suspend fun agregar(consorcioId: Long, categoria: String, descripcion: String, fecha: LocalDate, usuarioId: Long) =
        withContext(Dispatchers.IO) {
            db.writableDatabase.insertOrThrow("historial_consorcio", null, ContentValues().apply {
                put("id_consorcio", consorcioId)
                put("categoria", categoria)
                put("descripcion", descripcion.take(255))
                put("id_user", usuarioId)
                put("fecha", fecha.toString())
                put("time", LocalDateTime.now().toLocalTime().withNano(0).toString().let { if (it.length == 5) "$it:00" else it })
                put("manual", 1)
            })
        }

    suspend fun borrar(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("historial_consorcio", "id_historial_consorcio = ? AND manual = 1", arrayOf(id.toString()))
    }
}
