package com.example.consora_mobile.data

import android.content.Context
import android.database.Cursor
import com.example.consora_mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Error de login con un mensaje listo para mostrar. */
class LoginException(message: String, val credencialesInvalidas: Boolean) : Exception(message)

/**
 * Acceso a los usuarios guardados en la base local. Más adelante se puede
 * reemplazar por llamadas a la API de Django sin cambiar las pantallas.
 */
class UsuarioRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).readableDatabase

    /** Valida correo y contraseña; devuelve el usuario o lanza [LoginException]. */
    suspend fun login(email: String, password: String): Usuario = withContext(Dispatchers.IO) {
        val row = db.rawQuery(
            """SELECT u.id_user, u.password, e.estado
               FROM user u LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               WHERE u.mail = ? COLLATE NOCASE""",
            arrayOf(email),
        ).use { c -> if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1), c.getStringOrNull(2)) else null }

        if (row == null || !PasswordHasher.check(password, row.second)) {
            throw LoginException(context.getString(R.string.error_login_invalid), credencialesInvalidas = true)
        }
        if (row.third != null && row.third != "Activa") {
            throw LoginException(context.getString(R.string.error_login_inactive), credencialesInvalidas = false)
        }
        findById(row.first)!!
    }

    suspend fun getById(id: Long): Usuario? = withContext(Dispatchers.IO) { findById(id) }

    private fun findById(id: Long): Usuario? =
        db.rawQuery(
            """SELECT u.id_user, u.name_surname, u.mail, u.img, r.rol, u.dni, u.phone,
                      u.date_nacimiento, e.estado, u.created_at,
                      (SELECT COUNT(*) FROM consorcio c WHERE c.representante = u.id_user),
                      a.matricula, a.fecha_alta_matricula, a.ente_otorgante
               FROM user u
               JOIN roles r ON r.id_rol = u.role
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               LEFT JOIN administrador a ON a.id_user = u.id_user
               WHERE u.id_user = ?""",
            arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            Usuario(
                id = c.getLong(0),
                nameSurname = c.getString(1),
                mail = c.getString(2),
                img = c.getStringOrNull(3),
                rol = c.getString(4),
                dni = c.getStringOrNull(5),
                phone = c.getStringOrNull(6),
                dateNacimiento = c.getStringOrNull(7),
                estadoCuenta = c.getStringOrNull(8),
                createdAt = c.getString(9),
                cantidadConsorcios = c.getInt(10),
                administrador = c.getStringOrNull(11)?.let { matricula ->
                    Usuario.DatosAdministrador(matricula, c.getStringOrNull(12), c.getStringOrNull(13))
                },
            )
        }
}

private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
