package com.example.consora_mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Consorcio(
    val id: Long,
    val nombre: String,
    val direccion: String,
    val descripcion: String?,
    /** Ruta de la foto elegida (almacenamiento interno), o null para usar la ilustración. */
    val img: String?,
    val cantidadFacturas: Int,
)

class ConsorcioRepository(private val context: Context) {

    /** Consorcios en los que el usuario figura como representante. */
    suspend fun listByRepresentante(usuarioId: Long): List<Consorcio> = withContext(Dispatchers.IO) {
        ConsoraDatabase.get(context).readableDatabase.rawQuery(
            """SELECT c.id_consorcio, c.name_consorcio, c.direccion, c.descripcion, c.img_consorcio,
                      (SELECT COUNT(*) FROM factura f WHERE f.id_consorcio = c.id_consorcio)
               FROM consorcio c WHERE c.representante = ? ORDER BY c.name_consorcio""",
            arrayOf(usuarioId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Consorcio(
                            id = c.getLong(0),
                            nombre = c.getString(1),
                            direccion = c.getString(2),
                            descripcion = if (c.isNull(3)) null else c.getString(3),
                            img = if (c.isNull(4)) null else c.getString(4),
                            cantidadFacturas = c.getInt(5),
                        )
                    )
                }
            }
        }
    }

    /** Consorcios que ve el usuario según su rol (los que representa, los habilitados o el suyo). */
    suspend fun listarVisibles(usuario: Usuario): List<Consorcio> = withContext(Dispatchers.IO) {
        ConsoraDatabase.get(context).readableDatabase.rawQuery(
            """SELECT c.id_consorcio, c.name_consorcio, c.direccion, c.descripcion, c.img_consorcio,
                      (SELECT COUNT(*) FROM factura f WHERE f.id_consorcio = c.id_consorcio)
               FROM consorcio c WHERE c.id_consorcio IN (${usuario.sqlConsorciosVisibles}) ORDER BY c.name_consorcio""",
            arrayOf(usuario.id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Consorcio(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3),
                        if (c.isNull(4)) null else c.getString(4), c.getInt(5))
                )
            }
        }
    }

    suspend fun actualizarImagen(idConsorcio: Long, ruta: String) = withContext(Dispatchers.IO) {
        ConsoraDatabase.get(context).writableDatabase.update(
            "consorcio",
            android.content.ContentValues().apply { put("img_consorcio", ruta) },
            "id_consorcio = ?",
            arrayOf(idConsorcio.toString()),
        )
    }
}
