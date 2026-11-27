package com.example.consora_mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Asistente que aparece en la pestaña Asistentes. */
data class Asistente(
    val id: Long,
    val nombre: String,
    val mail: String,
    /** Imagen de perfil ("asset:..." o ruta de archivo), o null para mostrar iniciales. */
    val img: String?,
)

class AsistenteRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).readableDatabase

    /** Asistentes habilitados y con cuenta activa del administrador indicado, ordenados por nombre. */
    suspend fun listar(idAdmin: Long): List<Asistente> = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT u.id_user, u.name_surname, u.mail, u.img
               FROM asistente a JOIN user u ON u.id_user = a.id_user
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               WHERE a.id_admin = ? AND a.habilitado = 1 AND (e.estado IS NULL OR e.estado = 'Activa')
               ORDER BY u.name_surname""",
            arrayOf(idAdmin.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Asistente(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3)))
                }
            }
        }
    }
}
