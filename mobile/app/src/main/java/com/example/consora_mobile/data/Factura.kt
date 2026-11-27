package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Factura(
    val id: Long,
    val idConsorcio: Long,
    /** Tipo de la deuda a la que corresponde (Luz, Agua, Ascensor...). */
    val tipoDeuda: String,
    /** Descripción de la deuda a la que corresponde. */
    val deuda: String,
    val proveedor: String,
    val nroComprobante: String,
    /** yyyy-MM-dd */
    val fecha: String,
    val importe: Double,
    /** Ruta del PDF o imagen adjunta, guardada en el almacenamiento interno de la app. */
    val archivo: String?,
)

/** Deuda de un consorcio que todavía no tiene factura cargada. */
data class Deuda(
    val id: Long,
    val tipo: String,
    val descripcion: String,
    val proveedor: String?,
    val importe: Double,
    /** yyyy-MM-dd */
    val vencimiento: String?,
)

/** Factura de cualquier consorcio del usuario, para "Facturas procesadas". */
data class FacturaProcesada(
    val id: Long,
    val consorcioId: Long,
    val consorcio: String,
    val tipo: String,
    val deuda: String,
    val proveedor: String,
    val nroComprobante: String,
    /** yyyy-MM-dd */
    val fecha: String,
    val importe: Double,
    val archivo: String?,
    val cargadaPor: String,
    /** Si la factura pagó una cuota de un contrato. */
    val contratoId: Long?,
)

class FacturaRepository(private val context: Context) {

    /** Todas las facturas de los consorcios que ve el usuario, de la más nueva a la más vieja. */
    suspend fun procesadas(usuario: Usuario): List<FacturaProcesada> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT f.id_factura, f.id_consorcio, c.name_consorcio, t.nombre, d.descripcion, f.proveedor,
                      f.nro_comprobante, f.fecha, f.importe, f.archivo, u.name_surname, d.id_contrato_proveedor
               FROM factura f
               JOIN deuda d ON d.id_deuda = f.id_deuda
               JOIN tipo_deuda t ON t.id_tipo_deuda = d.id_tipo_deuda
               JOIN consorcio c ON c.id_consorcio = f.id_consorcio
               JOIN user u ON u.id_user = f.id_user
               WHERE f.id_consorcio IN (${usuario.sqlConsorciosVisibles})
               ORDER BY f.fecha DESC, f.id_factura DESC""",
            arrayOf(usuario.id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(FacturaProcesada(
                    c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5),
                    c.getString(6), c.getString(7), c.getDouble(8), if (c.isNull(9)) null else c.getString(9),
                    c.getString(10), if (c.isNull(11)) null else c.getLong(11),
                ))
            }
        }
    }

    private val db get() = ConsoraDatabase.get(context)

    suspend fun listByConsorcio(idConsorcio: Long): List<Factura> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT f.id_factura, f.id_consorcio, t.nombre, d.descripcion, f.proveedor,
                      f.nro_comprobante, f.fecha, f.importe, f.archivo
               FROM factura f
               JOIN deuda d ON d.id_deuda = f.id_deuda
               JOIN tipo_deuda t ON t.id_tipo_deuda = d.id_tipo_deuda
               WHERE f.id_consorcio = ? ORDER BY f.fecha DESC, f.id_factura DESC""",
            arrayOf(idConsorcio.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Factura(
                            id = c.getLong(0),
                            idConsorcio = c.getLong(1),
                            tipoDeuda = c.getString(2),
                            deuda = c.getString(3),
                            proveedor = c.getString(4),
                            nroComprobante = c.getString(5),
                            fecha = c.getString(6),
                            importe = c.getDouble(7),
                            archivo = if (c.isNull(8)) null else c.getString(8),
                        )
                    )
                }
            }
        }
    }

    /** Deudas del consorcio que todavía no tienen factura, ordenadas por vencimiento. */
    suspend fun deudasPendientes(idConsorcio: Long): List<Deuda> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT d.id_deuda, t.nombre, d.descripcion, d.proveedor, d.importe, d.vencimiento
               FROM deuda d JOIN tipo_deuda t ON t.id_tipo_deuda = d.id_tipo_deuda
               WHERE d.id_consorcio = ?
                 AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.id_deuda = d.id_deuda)
               ORDER BY d.vencimiento IS NULL, d.vencimiento""",
            arrayOf(idConsorcio.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Deuda(
                            id = c.getLong(0),
                            tipo = c.getString(1),
                            descripcion = c.getString(2),
                            proveedor = if (c.isNull(3)) null else c.getString(3),
                            importe = c.getDouble(4),
                            vencimiento = if (c.isNull(5)) null else c.getString(5),
                        )
                    )
                }
            }
        }
    }

    suspend fun insert(
        idConsorcio: Long,
        idDeuda: Long,
        idUsuario: Long,
        proveedor: String,
        nroComprobante: String,
        fecha: String,
        importe: Double,
        archivo: String?,
    ): Long = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val id = w.insertOrThrow("factura", null, ContentValues().apply {
                put("id_consorcio", idConsorcio)
                put("id_deuda", idDeuda)
                put("id_user", idUsuario)
                put("proveedor", proveedor)
                put("nro_comprobante", nroComprobante)
                put("fecha", fecha)
                put("importe", importe)
                put("archivo", archivo)
            })
            // Queda en el historial del edificio; si era la última cuota de un contrato, lo finaliza
            val deuda = w.rawQuery("SELECT descripcion FROM deuda WHERE id_deuda = ?", arrayOf(idDeuda.toString()))
                .use { if (it.moveToFirst()) it.getString(0) else "" }
            w.registrarHistorial(idConsorcio, "pago", "Se pagó \"$deuda\" a $proveedor (${monedaCorta(importe)}).", idUsuario)
            ContratoRepository.actualizarEstado(w, idDeuda, idUsuario)
            w.setTransactionSuccessful()
            id
        } finally {
            w.endTransaction()
        }
    }
}
