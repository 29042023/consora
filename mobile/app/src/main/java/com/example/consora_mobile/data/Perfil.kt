package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import com.example.consora_mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Datos que el usuario puede cambiar de su perfil (pantalla Cuenta). */
data class DatosPerfil(
    val nombre: String,
    val mail: String,
    val phone: String?,
    val dni: String?,
    /** yyyy-MM-dd */
    val nacimiento: String?,
    /** Solo administradores */
    val matricula: String? = null,
    val fechaAltaMatricula: String? = null,
    val enteOtorgante: String? = null,
)

data class TituloAdmin(val id: Long, val titulo: String)

/** Cambios sobre el propio perfil: datos, foto, contraseña, Marketplace, título y baja (igual que en la web). */
class PerfilRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    private fun existe(sql: String, vararg args: String) =
        db.readableDatabase.rawQuery(sql, arrayOf(*args)).use { it.moveToFirst() }

    /** Guarda los datos; devuelve el mensaje de error (R.string) o null si se guardó. */
    suspend fun guardarDatos(usuarioId: Long, d: DatosPerfil): Int? = withContext(Dispatchers.IO) {
        val id = usuarioId.toString()
        when {
            d.nombre.isBlank() -> return@withContext R.string.perfil_error_nombre
            !android.util.Patterns.EMAIL_ADDRESS.matcher(d.mail).matches() -> return@withContext R.string.error_email_invalid
            existe("SELECT 1 FROM user WHERE mail = ? COLLATE NOCASE AND id_user <> ?", d.mail, id) -> return@withContext R.string.perfil_error_mail
            d.dni != null && existe("SELECT 1 FROM user WHERE dni = ? AND id_user <> ?", d.dni, id) -> return@withContext R.string.perfil_error_dni
            d.matricula != null && existe("SELECT 1 FROM administrador WHERE matricula = ? AND id_user <> ?", d.matricula, id) ->
                return@withContext R.string.perfil_error_matricula
        }
        val w = db.writableDatabase
        w.update("user", ContentValues().apply {
            put("name_surname", d.nombre.trim())
            put("mail", d.mail.trim())
            put("phone", d.phone)
            put("dni", d.dni)
            put("date_nacimiento", d.nacimiento)
        }, "id_user = ?", arrayOf(id))
        if (d.matricula != null) {
            w.update("administrador", ContentValues().apply {
                put("matricula", d.matricula)
                put("fecha_alta_matricula", d.fechaAltaMatricula)
                put("ente_otorgante", d.enteOtorgante)
            }, "id_user = ?", arrayOf(id))
        }
        null
    }

    /** Cambia (o quita, con null) la foto de perfil. */
    suspend fun cambiarFoto(usuarioId: Long, ruta: String?, anterior: String?) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("user", ContentValues().apply { put("img", ruta) }, "id_user = ?", arrayOf(usuarioId.toString()))
        Archivos.borrar(anterior)
    }

    /** Devuelve el mensaje de error (R.string) o null si se cambió. */
    suspend fun cambiarPassword(usuarioId: Long, actual: String, nueva: String, repetida: String): Int? = withContext(Dispatchers.IO) {
        val guardada = db.readableDatabase.rawQuery("SELECT password FROM user WHERE id_user = ?", arrayOf(usuarioId.toString()))
            .use { it.moveToFirst(); it.getString(0) }
        when {
            !PasswordHasher.check(actual, guardada) -> R.string.perfil_error_password_actual
            nueva.length < 6 -> R.string.perfil_error_password_corta
            nueva != repetida -> R.string.perfil_error_password_distintas
            else -> {
                db.writableDatabase.update("user", ContentValues().apply { put("password", PasswordHasher.hash(nueva)) },
                    "id_user = ?", arrayOf(usuarioId.toString()))
                null
            }
        }
    }

    /** Descripción y web del perfil del Marketplace (null si todavía no tiene). */
    suspend fun marketplace(usuarioId: Long): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT descripcion, link FROM cuentas_marketplace WHERE dueno = ?", arrayOf(usuarioId.toString()))
            .use { if (it.moveToFirst()) (if (it.isNull(0)) null else it.getString(0)) to (if (it.isNull(1)) null else it.getString(1)) else null }
    }

    /** Crea o actualiza el perfil del Marketplace. */
    suspend fun guardarMarketplace(usuarioId: Long, descripcion: String?, link: String?) = withContext(Dispatchers.IO) {
        val valores = ContentValues().apply {
            put("descripcion", descripcion)
            // "www.empresa.com" -> "https://www.empresa.com"
            put("link", link?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" })
        }
        val w = db.writableDatabase
        if (w.update("cuentas_marketplace", valores, "dueno = ?", arrayOf(usuarioId.toString())) == 0) {
            w.insertOrThrow("cuentas_marketplace", null, valores.apply { put("dueno", usuarioId) })
        }
    }

    suspend fun titulos(usuarioId: Long): List<TituloAdmin> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT id_titulo, titulo FROM titulo_administrador WHERE id_user = ? ORDER BY id_titulo DESC", arrayOf(usuarioId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(TituloAdmin(c.getLong(0), c.getString(1))) } }
    }

    suspend fun agregarTitulo(usuarioId: Long, ruta: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertOrThrow("titulo_administrador", null, ContentValues().apply {
            put("id_user", usuarioId)
            put("titulo", ruta)
        })
    }

    suspend fun borrarTitulo(t: TituloAdmin) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("titulo_administrador", "id_titulo = ?", arrayOf(t.id.toString()))
        Archivos.borrar(t.titulo)
    }

    /** Consorcios que representa: mientras tenga alguno, no puede darse de baja. */
    suspend fun consorciosACargo(usuarioId: Long): List<String> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT name_consorcio FROM consorcio WHERE representante = ? ORDER BY name_consorcio", arrayOf(usuarioId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    /** La cuenta no se borra: pasa a estado "Baja" (quedan sus facturas y chats). */
    suspend fun darDeBaja(usuarioId: Long, password: String): Boolean = withContext(Dispatchers.IO) {
        val read = db.readableDatabase
        val guardada = read.rawQuery("SELECT password FROM user WHERE id_user = ?", arrayOf(usuarioId.toString()))
            .use { it.moveToFirst(); it.getString(0) }
        if (!PasswordHasher.check(password, guardada)) return@withContext false
        val baja = read.rawQuery("SELECT id_estado_cuenta FROM estados_cuenta WHERE estado = 'Baja'", null)
            .use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: db.writableDatabase.insertOrThrow("estados_cuenta", null, ContentValues().apply {
                put("estado", "Baja")
                put("motivo", "Solicitada por el usuario")
            })
        db.writableDatabase.update("user", ContentValues().apply { put("id_estado_cuenta", baja) },
            "id_user = ?", arrayOf(usuarioId.toString()))
        true
    }
}
