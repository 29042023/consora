package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ChatFormato {
    /** Formato en que se guarda `enviado_en` en la base. */
    val FECHA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}

/**
 * Consorcio, unidad y dirección de un propietario, armado en SQL.
 * Ej.: "Edificio San Martín 4° B (San Martín 850, CABA)". Null si no es propietario.
 */
private const val SQL_UBICACION = """(SELECT cs.name_consorcio || COALESCE(' ' || pc.unidad, '') || ' (' || cs.direccion || ')'
        FROM propietario_consorcio pc JOIN consorcio cs ON cs.id_consorcio = pc.id_consorcio
        WHERE pc.id_user = u.id_user LIMIT 1)"""

/** Rol, y para los propietarios también el consorcio al que pertenecen. */
private fun subtitulo(rol: String, ubicacion: String?) = if (ubicacion == null) rol else "$rol · $ubicacion"

/** Un chat en la lista: con quién es y cuál fue el último mensaje. */
data class ChatResumen(
    val id: Long,
    val otroNombre: String,
    /** Rol + consorcio (si es propietario). */
    val otroSubtitulo: String,
    /** Imagen de perfil ("asset:..." o ruta de archivo), o null para mostrar iniciales. */
    val otroImg: String?,
    val ultimoMensaje: String?,
    val ultimoEnviadoEn: String?,
    val ultimoEsMio: Boolean,
)

data class Mensaje(
    val id: Long,
    val usuarioId: Long,
    val texto: String,
    /** yyyy-MM-dd HH:mm:ss */
    val enviadoEn: String,
    /** Archivo adjunto (ruta o "asset:..."), o null. */
    val archivo: String? = null,
    val nombreArchivo: String? = null,
    /** Invitación a una votación de contratación, si el mensaje es eso. */
    val invitacion: InvitacionChat? = null,
    /** Invitación a una reunión (proveedor invitado como participante opcional). */
    val invitacionReunion: InvitacionReunionChat? = null,
)

/** Invitación a una reunión, tal como se ve en el chat. */
data class InvitacionReunionChat(
    val id: Long,
    val reunionId: Long,
    val usuarioId: Long,
    val titulo: String,
    val consorcio: String,
    /** yyyy-MM-dd HH:mm:ss */
    val fechaInicio: String,
    val fechaFin: String,
    val estado: String,
    val modalidad: String,
    val lugar: String?,
    /** "pendiente" | "acepta" | "rechaza" */
    val respuesta: String,
) {
    val cancelada get() = estado == Reunion.CANCELADA
    val activa get() = !cancelada && fechaFin > Reunion.ahora()
}

/** Invitación a una votación de contratación, tal como se ve en el chat. */
data class InvitacionChat(
    val id: Long,
    val votacionId: Long,
    val proveedorId: Long,
    val consorcio: String,
    /** yyyy-MM-dd HH:mm:ss */
    val fechaLimite: String,
    /** null = no respondió, true = aceptó, false = rechazó */
    val aceptacion: Boolean?,
    val contratado: Boolean,
) {
    val abierta get() = fechaLimite > java.time.LocalDateTime.now().format(ChatFormato.FECHA_HORA)
}

/** La otra persona de un chat individual, con lo necesario para abrir su perfil. */
data class OtroMiembro(
    val id: Long,
    val nombre: String,
    val mail: String,
    val img: String?,
    val rol: String,
    /** Si es asistente, el administrador para el que trabaja. */
    val idAdmin: Long?,
)

/** Usuario con el que se puede iniciar un chat. */
data class Contacto(val id: Long, val nombre: String, val subtitulo: String, val img: String?)

class ChatRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    /** Chats individuales del usuario, el de actividad más reciente primero. */
    suspend fun listByUsuario(usuarioId: Long): List<ChatResumen> = withContext(Dispatchers.IO) {
        val ultimo = "FROM mensaje_chat m WHERE m.id_chat = c.id_chat ORDER BY m.enviado_en DESC, m.id_mensaje DESC LIMIT 1"
        db.readableDatabase.rawQuery(
            """SELECT c.id_chat, u.name_surname, r.rol, $SQL_UBICACION, u.img,
                      -- Si el último mensaje es solo un archivo, se muestra su nombre
                      (SELECT CASE WHEN m.mensaje <> '' THEN m.mensaje
                                   WHEN m.archivo IS NOT NULL THEN '📎 ' || COALESCE(m.nombre_archivo, 'Archivo')
                                   ELSE m.mensaje END $ultimo),
                      (SELECT m.enviado_en $ultimo),
                      (SELECT m.id_user $ultimo)
               FROM chat c
               JOIN miembros_chat yo ON yo.id_chat = c.id_chat AND yo.id_user = ?
               JOIN miembros_chat otro ON otro.id_chat = c.id_chat AND otro.id_user <> yo.id_user
               JOIN user u ON u.id_user = otro.id_user
               JOIN roles r ON r.id_rol = u.role
               ORDER BY 7 IS NULL, 7 DESC""",
            arrayOf(usuarioId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ChatResumen(
                            id = c.getLong(0),
                            otroNombre = c.getString(1),
                            otroSubtitulo = subtitulo(c.getString(2), if (c.isNull(3)) null else c.getString(3)),
                            otroImg = if (c.isNull(4)) null else c.getString(4),
                            ultimoMensaje = if (c.isNull(5)) null else c.getString(5),
                            ultimoEnviadoEn = if (c.isNull(6)) null else c.getString(6),
                            ultimoEsMio = !c.isNull(7) && c.getLong(7) == usuarioId,
                        )
                    )
                }
            }
        }
    }

    /** La otra persona del chat (el que no es [usuarioId]). */
    suspend fun otroMiembro(chatId: Long, usuarioId: Long): OtroMiembro? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT u.id_user, u.name_surname, u.mail, u.img, r.rol, a.id_admin
               FROM miembros_chat m
               JOIN user u ON u.id_user = m.id_user
               JOIN roles r ON r.id_rol = u.role
               LEFT JOIN asistente a ON a.id_user = u.id_user
               WHERE m.id_chat = ? AND m.id_user <> ?
               LIMIT 1""",
            arrayOf(chatId.toString(), usuarioId.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return@withContext null
            OtroMiembro(
                c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3),
                c.getString(4), if (c.isNull(5)) null else c.getLong(5),
            )
        }
    }

    suspend fun mensajes(chatId: Long): List<Mensaje> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT m.id_mensaje, m.id_user, m.mensaje, m.enviado_en, m.archivo, m.nombre_archivo,
                      vc.id_votacion_contrato, vc.id_votacion, vc.id_proveedor, cs.name_consorcio, v.fecha_limite,
                      vc.aceptacion, vc.contratado,
                      rp.id_reunion_participante, rp.id_reunion, rp.id_user, r.titulo, rc.name_consorcio,
                      r.fecha_inicio, r.fecha_fin, r.estado, r.modalidad, r.lugar, rp.respuesta
               FROM mensaje_chat m
               LEFT JOIN votacion_contrato vc ON vc.id_votacion_contrato = m.id_votacion_contrato
               LEFT JOIN votacion v ON v.id_votacion = vc.id_votacion
               LEFT JOIN consorcio cs ON cs.id_consorcio = v.id_consorcio
               LEFT JOIN reunion_participante rp ON rp.id_reunion_participante = m.id_reunion_participante
               LEFT JOIN reunion r ON r.id_reunion = rp.id_reunion
               LEFT JOIN consorcio rc ON rc.id_consorcio = r.id_consorcio
               WHERE m.id_chat = ? ORDER BY m.enviado_en, m.id_mensaje""",
            arrayOf(chatId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val invitacion = if (c.isNull(6)) null else InvitacionChat(
                        id = c.getLong(6), votacionId = c.getLong(7), proveedorId = c.getLong(8),
                        consorcio = c.getString(9), fechaLimite = c.getString(10),
                        aceptacion = if (c.isNull(11)) null else c.getInt(11) == 1,
                        contratado = c.getInt(12) == 1,
                    )
                    val invitacionReunion = if (c.isNull(13)) null else InvitacionReunionChat(
                        id = c.getLong(13), reunionId = c.getLong(14), usuarioId = c.getLong(15), titulo = c.getString(16),
                        consorcio = c.getString(17), fechaInicio = c.getString(18), fechaFin = c.getString(19),
                        estado = c.getString(20), modalidad = c.getString(21), lugar = if (c.isNull(22)) null else c.getString(22),
                        respuesta = c.getString(23),
                    )
                    add(
                        Mensaje(
                            c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                            if (c.isNull(4)) null else c.getString(4), if (c.isNull(5)) null else c.getString(5),
                            invitacion, invitacionReunion,
                        )
                    )
                }
            }
        }
    }

    /** Envía un mensaje de texto, un archivo, o las dos cosas. */
    suspend fun enviar(chatId: Long, usuarioId: Long, texto: String, archivo: ArchivoGuardado? = null): Mensaje =
        withContext(Dispatchers.IO) {
            val enviadoEn = LocalDateTime.now().format(ChatFormato.FECHA_HORA)
            val id = db.writableDatabase.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chatId)
                put("id_user", usuarioId)
                put("mensaje", texto)
                put("archivo", archivo?.ruta)
                put("nombre_archivo", archivo?.nombre)
                put("enviado_en", enviadoEn)
            })
            Mensaje(id, usuarioId, texto, enviadoEn, archivo?.ruta, archivo?.nombre)
        }

    /** Todos los usuarios activos menos el propio, para iniciar un chat nuevo. */
    suspend fun contactos(usuarioId: Long): List<Contacto> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT u.id_user, u.name_surname, r.rol, $SQL_UBICACION, u.img
               FROM user u JOIN roles r ON r.id_rol = u.role
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               WHERE u.id_user <> ? AND (e.estado IS NULL OR e.estado = 'Activa')
               ORDER BY u.name_surname""",
            arrayOf(usuarioId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Contacto(
                            id = c.getLong(0),
                            nombre = c.getString(1),
                            subtitulo = subtitulo(c.getString(2), if (c.isNull(3)) null else c.getString(3)),
                            img = if (c.isNull(4)) null else c.getString(4),
                        )
                    )
                }
            }
        }
    }

    /** Devuelve el chat individual entre los dos usuarios, creándolo si no existe. */
    suspend fun obtenerOCrearIndividual(usuarioId: Long, otroId: Long): Long = withContext(Dispatchers.IO) {
        val read = db.readableDatabase
        val existente = read.rawQuery(
            """SELECT c.id_chat FROM chat c
               JOIN tipo_chat t ON t.id_tipo_chat = c.id_tipo_chat AND t.name_chat = 'Individual'
               JOIN miembros_chat a ON a.id_chat = c.id_chat AND a.id_user = ?
               JOIN miembros_chat b ON b.id_chat = c.id_chat AND b.id_user = ?
               LIMIT 1""",
            arrayOf(usuarioId.toString(), otroId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (existente != null) return@withContext existente

        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val tipo = w.rawQuery("SELECT id_tipo_chat FROM tipo_chat WHERE name_chat = 'Individual'", null)
                .use { it.moveToFirst(); it.getLong(0) }
            val chatId = w.insertOrThrow("chat", null, ContentValues().apply { put("id_tipo_chat", tipo) })
            for (uid in listOf(usuarioId, otroId)) {
                w.insertOrThrow("miembros_chat", null, ContentValues().apply {
                    put("id_chat", chatId)
                    put("id_user", uid)
                })
            }
            w.setTransactionSuccessful()
            chatId
        } finally {
            w.endTransaction()
        }
    }
}
