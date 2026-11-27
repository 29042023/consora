package com.example.consora_mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Proveedor que aparece en el Marketplace. */
data class Proveedor(
    val id: Long,
    val nombre: String,
    /** Imagen de perfil ("asset:..." o ruta de archivo), o null para mostrar iniciales. */
    val img: String?,
    /** Tipo de servicio que ofrece; null mientras no exista en la base. */
    val servicio: String?,
    /** Calificación promedio (1 a 5 estrellas); null si no tiene reseñas. */
    val estrellas: Double?,
)

class ProveedorRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).readableDatabase

    /** Usuarios activos con rol Proveedor y su calificación promedio, ordenados por nombre. */
    suspend fun listar(): List<Proveedor> = withContext(Dispatchers.IO) {
        // TODO: traer el tipo de servicio cuando exista en la base
        db.rawQuery(
            """SELECT u.id_user, u.name_surname, u.img,
                      (SELECT AVG(rs.estrellas) FROM cuentas_marketplace cm
                       JOIN resenas rs ON rs.cuentas_marketplace = cm.id_cuentas_marketplace
                       WHERE cm.dueno = u.id_user)
               FROM user u JOIN roles r ON r.id_rol = u.role
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               WHERE r.rol = 'Proveedor' AND (e.estado IS NULL OR e.estado = 'Activa')
               ORDER BY u.name_surname""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Proveedor(
                        c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getString(2),
                        servicio = null, estrellas = if (c.isNull(3)) null else c.getDouble(3),
                    ))
                }
            }
        }
    }
}
