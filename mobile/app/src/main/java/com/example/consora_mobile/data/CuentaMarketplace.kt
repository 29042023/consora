package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/** Perfil del Marketplace de un proveedor o administrador: descripción, web y fotos publicadas. */
data class CuentaMarketplace(
    val id: Long,
    val dueno: Long,
    val descripcion: String?,
    /** Web propia del proveedor, si tiene. */
    val link: String?,
    /** Fotos publicadas, la más nueva primero. */
    val imagenes: List<ImagenMarketplace>,
    /** Reseñas recibidas, la más nueva primero. */
    val resenas: List<Resena>,
) {
    /** Promedio de estrellas, o null si no tiene reseñas. */
    val calificacion: Double? get() = resenas.takeIf { it.isNotEmpty() }?.map { it.estrellas }?.average()
}

data class Resena(
    val id: Long,
    val autorId: Long,
    val autorNombre: String,
    val autorImg: String?,
    /** 1 a 5 */
    val estrellas: Int,
    val descripcion: String?,
    /** "yyyy-MM-dd HH:mm:ss" */
    val creation: String,
)

/** Publicación: una foto con su texto. */
data class ImagenMarketplace(
    val id: Long,
    /** Ruta del archivo (en img/publicaciones) o "asset:..." si viene incluida en la app. */
    val img: String,
    val descripcion: String?,
    /** "yyyy-MM-dd HH:mm:ss" */
    val creation: String,
)

class CuentaMarketplaceRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).writableDatabase

    /** Cuenta del Marketplace del usuario, o null si todavía no tiene. */
    suspend fun obtener(dueno: Long): CuentaMarketplace? = withContext(Dispatchers.IO) {
        val cuenta = db.rawQuery(
            "SELECT id_cuentas_marketplace, descripcion, link FROM cuentas_marketplace WHERE dueno = ?",
            arrayOf(dueno.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return@withContext null
            Triple(c.getLong(0), if (c.isNull(1)) null else c.getString(1), if (c.isNull(2)) null else c.getString(2))
        }
        val (id, descripcion, link) = cuenta
        val imagenes = db.rawQuery(
            """SELECT id_img_cuentas_marketplace, img, descripcion, creation FROM img_cuentas_marketplace
               WHERE cuenta = ? ORDER BY creation DESC, id_img_cuentas_marketplace DESC""",
            arrayOf(id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(ImagenMarketplace(c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getString(2), c.getString(3)))
                }
            }
        }
        val resenas = db.rawQuery(
            """SELECT r.id_resenas_cuentas_marketplace, u.id_user, u.name_surname, u.img, r.estrellas, r.descripcion, r.creation
               FROM resenas r JOIN user u ON u.id_user = r.autor
               WHERE r.cuentas_marketplace = ? ORDER BY r.creation DESC""",
            arrayOf(id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Resena(
                        c.getLong(0), c.getLong(1), c.getString(2), if (c.isNull(3)) null else c.getString(3),
                        c.getInt(4), if (c.isNull(5)) null else c.getString(5), c.getString(6),
                    ))
                }
            }
        }
        CuentaMarketplace(id, dueno, descripcion, link, imagenes, resenas)
    }

    /** Guarda la reseña del autor (si ya había dejado una, la reemplaza). */
    suspend fun guardarResena(dueno: Long, autor: Long, estrellas: Int, descripcion: String?) = withContext(Dispatchers.IO) {
        db.insertWithOnConflict("resenas", null, ContentValues().apply {
            put("cuentas_marketplace", idCuenta(dueno) ?: crearCuenta(dueno))
            put("autor", autor)
            put("estrellas", estrellas)
            put("descripcion", descripcion)
            put("creation", LocalDateTime.now().format(ChatFormato.FECHA_HORA))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Guarda la foto en img/publicaciones y la publica en la cuenta (la crea si el usuario no tenía). */
    suspend fun agregarImagen(dueno: Long, uri: Uri, descripcion: String?) = withContext(Dispatchers.IO) {
        val cuenta = idCuenta(dueno) ?: crearCuenta(dueno)
        val ruta = Carpeta.IMG_PUBLICACIONES.guardar(context, uri, "cuenta_${cuenta}_${System.currentTimeMillis()}.jpg")
        db.insertOrThrow("img_cuentas_marketplace", null, ContentValues().apply {
            put("cuenta", cuenta)
            put("img", ruta)
            put("descripcion", descripcion)
            put("creation", LocalDateTime.now().format(ChatFormato.FECHA_HORA))
        })
    }

    /** Borra la foto de la base y su archivo. */
    suspend fun borrarImagen(imagen: ImagenMarketplace) = withContext(Dispatchers.IO) {
        db.delete("img_cuentas_marketplace", "id_img_cuentas_marketplace = ?", arrayOf(imagen.id.toString()))
        // Las imágenes incluidas en la app (assets) no se pueden borrar
        if (!imagen.img.startsWith("asset:")) File(imagen.img).delete()
    }

    private fun crearCuenta(dueno: Long): Long =
        db.insertOrThrow("cuentas_marketplace", null, ContentValues().apply { put("dueno", dueno) })

    private fun idCuenta(dueno: Long): Long? = db.rawQuery(
        "SELECT id_cuentas_marketplace FROM cuentas_marketplace WHERE dueno = ?", arrayOf(dueno.toString()),
    ).use { if (it.moveToFirst()) it.getLong(0) else null }
}
