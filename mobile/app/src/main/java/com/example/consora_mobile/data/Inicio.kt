package com.example.consora_mobile.data

import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Invitación a una contratación, vista desde el inicio del proveedor. */
data class InvitacionProveedor(
    val votacionId: Long,
    val detalle: String,
    val consorcio: String,
    val fechaLimite: String,
    val aceptacion: Boolean?,
    val contratado: Boolean,
    val pdf: String?,
    /** Chat con el administrador donde llegó la invitación (para responderla). */
    val chatId: Long?,
    val admin: String,
    val adminImg: String?,
) {
    val abierta get() = fechaLimite > Votacion.ahora()
}

/** Unidad de un propietario: su consorcio y quién lo administra. */
data class Unidad(
    val consorcioId: Long,
    val consorcio: String,
    val direccion: String,
    val unidad: String?,
    val adminId: Long,
    val admin: String,
    val adminImg: String?,
)

data class ResumenMarketplace(val publicaciones: Int, val resenas: Int, val promedio: Double?, val descripcion: String?)

/** Datos de las pantallas de inicio del asistente, el proveedor y el propietario. */
class InicioRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).readableDatabase

    suspend fun invitaciones(proveedorId: Long): List<InvitacionProveedor> = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT v.id_votacion, v.detalle, c.name_consorcio, v.fecha_limite, vc.aceptacion, vc.contratado, vc.propuesta_pdf,
                      (SELECT m.id_chat FROM mensaje_chat m WHERE m.id_votacion_contrato = vc.id_votacion_contrato LIMIT 1),
                      a.name_surname, a.img
               FROM votacion_contrato vc
               JOIN votacion v ON v.id_votacion = vc.id_votacion
               JOIN consorcio c ON c.id_consorcio = v.id_consorcio
               JOIN user a ON a.id_user = c.representante
               WHERE vc.id_proveedor = ? ORDER BY v.fecha DESC""",
            arrayOf(proveedorId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    InvitacionProveedor(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        if (c.isNull(4)) null else c.getInt(4) == 1, c.getInt(5) == 1, c.str(6),
                        if (c.isNull(7)) null else c.getLong(7), c.getString(8), c.str(9),
                    )
                )
            }
        }
    }

    suspend fun resumenMarketplace(usuarioId: Long): ResumenMarketplace = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT (SELECT COUNT(*) FROM img_cuentas_marketplace i WHERE i.cuenta = cm.id_cuentas_marketplace),
                      (SELECT COUNT(*) FROM resenas r WHERE r.cuentas_marketplace = cm.id_cuentas_marketplace),
                      (SELECT AVG(r.estrellas) FROM resenas r WHERE r.cuentas_marketplace = cm.id_cuentas_marketplace),
                      cm.descripcion
               FROM cuentas_marketplace cm WHERE cm.dueno = ?""",
            arrayOf(usuarioId.toString()),
        ).use { c ->
            if (c.moveToFirst()) ResumenMarketplace(c.getInt(0), c.getInt(1), if (c.isNull(2)) null else c.getDouble(2), c.str(3))
            else ResumenMarketplace(0, 0, null, null)
        }
    }

    suspend fun unidades(propietarioId: Long): List<Unidad> = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT c.id_consorcio, c.name_consorcio, c.direccion, pc.unidad, a.id_user, a.name_surname, a.img
               FROM propietario_consorcio pc
               JOIN consorcio c ON c.id_consorcio = pc.id_consorcio
               JOIN user a ON a.id_user = c.representante
               WHERE pc.id_user = ?""",
            arrayOf(propietarioId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Unidad(c.getLong(0), c.getString(1), c.getString(2), c.str(3), c.getLong(4), c.getString(5), c.str(6)))
            }
        }
    }

    /** El administrador para el que trabaja el asistente (id, nombre, foto). */
    suspend fun adminDeAsistente(asistenteId: Long): Triple<Long, String, String?>? = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT u.id_user, u.name_surname, u.img FROM asistente a JOIN user u ON u.id_user = a.id_admin
               WHERE a.id_user = ?""",
            arrayOf(asistenteId.toString()),
        ).use { if (it.moveToFirst()) Triple(it.getLong(0), it.getString(1), it.str(2)) else null }
    }
}

private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
