package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Reunión de un consorcio (misma estructura que en la web): virtual con Google Meet o presencial. */
data class Reunion(
    val id: Long,
    val consorcioId: Long,
    val consorcio: String,
    val organizadorId: Long,
    val organizador: String,
    /** Administrador del consorcio: junto con el organizador, gestiona la reunión. */
    val representanteId: Long,
    val titulo: String,
    val descripcion: String?,
    /** "virtual" | "presencial" */
    val modalidad: String,
    val lugar: String?,
    /** yyyy-MM-dd HH:mm:ss */
    val fechaInicio: String,
    val fechaFin: String,
    val meetLink: String?,
    val meetCodigo: String?,
    /** "programada" | "cancelada" (en curso / finalizada salen de la hora, ver [estadoActual]) */
    val estado: String,
    val confirmados: Int,
    /** Respuesta del usuario logueado, si participa: "pendiente" | "acepta" | "rechaza". */
    val miRespuesta: String?,
) {
    val esVirtual get() = modalidad == VIRTUAL

    val estadoActual: String
        get() {
            val ahora = ahora()
            return when {
                estado == CANCELADA -> CANCELADA
                fechaFin <= ahora -> FINALIZADA
                fechaInicio <= ahora -> EN_CURSO
                else -> PROGRAMADA
            }
        }

    /** Todavía se puede invitar, responder o unirse. */
    val activa get() = estadoActual == PROGRAMADA || estadoActual == EN_CURSO

    companion object {
        const val VIRTUAL = "virtual"
        const val PRESENCIAL = "presencial"
        const val PROGRAMADA = "programada"
        const val EN_CURSO = "en_curso"
        const val FINALIZADA = "finalizada"
        const val CANCELADA = "cancelada"

        const val PENDIENTE = "pendiente"
        const val ACEPTA = "acepta"
        const val RECHAZA = "rechaza"

        fun ahora(): String = LocalDateTime.now().format(ChatFormato.FECHA_HORA)

        /** "yyyy-MM-dd HH:mm:ss" -> "23/11 a las 18:00" */
        fun diaHora(fechaHora: String) = "${fechaHora.substring(8, 10)}/${fechaHora.substring(5, 7)} a las ${fechaHora.substring(11, 16)}"
    }
}

data class Participante(
    val id: Long,
    val usuarioId: Long,
    val nombre: String,
    val img: String?,
    /** "propietario" | "asistente" | "proveedor" | "administrador" */
    val rol: String,
    val esOpcional: Boolean,
    val respuesta: String,
    val asistio: Boolean?,
)

data class DocReunion(val id: Long, val doc: String, val tipo: String) {
    val nombre get() = Archivos.nombreVisible(doc)
}

data class DetalleReunion(
    val reunion: Reunion,
    val participantes: List<Participante>,
    val documentos: List<DocReunion>,
    /** Participación del usuario logueado (null si solo la ve por ser del consorcio). */
    val yo: Participante?,
) {
    /** El proveedor invitado ve el link recién cuando acepta. */
    val veLink get() = yo == null || !yo.esOpcional || yo.respuesta == Reunion.ACEPTA
}

class ReunionRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    private val sqlReunion = """
        SELECT r.id_reunion, r.id_consorcio, c.name_consorcio, r.id_organizador, o.name_surname, c.representante,
               r.titulo, r.descripcion, r.modalidad, r.lugar, r.fecha_inicio, r.fecha_fin, r.meet_link, r.meet_codigo, r.estado,
               (SELECT COUNT(*) FROM reunion_participante p WHERE p.id_reunion = r.id_reunion AND p.respuesta = 'acepta'),
               (SELECT p.respuesta FROM reunion_participante p WHERE p.id_reunion = r.id_reunion AND p.id_user = ?)
        FROM reunion r JOIN consorcio c ON c.id_consorcio = r.id_consorcio JOIN user o ON o.id_user = r.id_organizador"""

    private fun Cursor.reunion() = Reunion(
        getLong(0), getLong(1), getString(2), getLong(3), getString(4), getLong(5), getString(6), str(7), getString(8),
        str(9), getString(10), getString(11), str(12), str(13), getString(14), getInt(15), str(16),
    )

    /**
     * Reuniones de los consorcios que ve el usuario, más aquellas en las que participa.
     * El proveedor solo ve las reuniones a las que lo invitaron (y no rechazó).
     */
    suspend fun listar(usuario: Usuario, consorcioId: Long? = null): List<Reunion> = withContext(Dispatchers.IO) {
        val filtro = if (consorcioId != null) " AND r.id_consorcio = $consorcioId" else ""
        val id = usuario.id.toString()
        db.readableDatabase.rawQuery(
            """$sqlReunion WHERE (r.id_consorcio IN (${usuario.sqlConsorciosVisibles})
                  OR r.id_reunion IN (SELECT id_reunion FROM reunion_participante
                                      WHERE id_user = ? AND NOT (rol_participante = 'proveedor' AND respuesta = 'rechaza')))$filtro
               ORDER BY r.fecha_inicio""",
            arrayOf(id, id, id),
        ).use { c -> buildList { while (c.moveToNext()) add(c.reunion()) } }
    }

    suspend fun detalle(reunionId: Long, usuarioId: Long): DetalleReunion? = withContext(Dispatchers.IO) {
        val read = db.readableDatabase
        val r = read.rawQuery("$sqlReunion WHERE r.id_reunion = ?", arrayOf(usuarioId.toString(), reunionId.toString()))
            .use { if (it.moveToFirst()) it.reunion() else null } ?: return@withContext null

        val participantes = read.rawQuery(
            """SELECT p.id_reunion_participante, u.id_user, u.name_surname, u.img, p.rol_participante, p.es_opcional, p.respuesta, p.asistio
               FROM reunion_participante p JOIN user u ON u.id_user = p.id_user
               WHERE p.id_reunion = ?
               ORDER BY CASE p.rol_participante WHEN 'administrador' THEN 0 WHEN 'propietario' THEN 1 WHEN 'asistente' THEN 2 ELSE 3 END,
                        u.name_surname""",
            arrayOf(reunionId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Participante(
                        c.getLong(0), c.getLong(1), c.getString(2), c.str(3), c.getString(4), c.getInt(5) == 1, c.getString(6),
                        if (c.isNull(7)) null else c.getInt(7) == 1,
                    )
                )
            }
        }
        val documentos = read.rawQuery(
            "SELECT id_doc_reunion, doc, tipo FROM doc_reunion WHERE id_reunion = ? ORDER BY id_doc_reunion",
            arrayOf(reunionId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(DocReunion(c.getLong(0), c.getString(1), c.getString(2))) } }

        DetalleReunion(r, participantes, documentos, participantes.firstOrNull { it.usuarioId == usuarioId })
    }

    /**
     * Crea la reunión con su evento en la agenda del consorcio. Participan el organizador (confirmado),
     * los propietarios y los asistentes habilitados; a los [proveedores] les llega la invitación por chat.
     */
    suspend fun crear(
        adminId: Long, consorcioId: Long, consorcio: String, titulo: String, descripcion: String?, modalidad: String,
        lugar: String?, inicio: LocalDateTime, minutos: Long, meetLink: String?, proveedores: List<Long>,
    ): Long = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        val f = ChatFormato.FECHA_HORA
        val ahora = Reunion.ahora()
        val id: Long
        w.beginTransaction()
        try {
            val evento = w.insertOrThrow("agenda_evento", null, ContentValues().apply {
                put("id_user", adminId)
                put("id_consorcio", consorcioId)
                put("titulo", "Reunión: $titulo".take(150))
                put("descripcion", descripcion)
                put("fecha", inicio.toLocalDate().toString())
                put("hora", inicio.toLocalTime().toString())
            })
            id = w.insertOrThrow("reunion", null, ContentValues().apply {
                put("id_consorcio", consorcioId)
                put("id_organizador", adminId)
                put("id_evento", evento)
                put("titulo", titulo)
                put("descripcion", descripcion)
                put("modalidad", modalidad)
                put("lugar", if (modalidad == Reunion.PRESENCIAL) lugar else null)
                put("fecha_inicio", inicio.format(f))
                put("fecha_fin", inicio.plusMinutes(minutos).format(f))
                put("meet_link", if (modalidad == Reunion.VIRTUAL) meetLink else null)
                put("created_at", ahora)
            })
            w.execSQL(
                """INSERT INTO reunion_participante (id_reunion, id_user, rol_participante, respuesta, respondido_en, notificado_en)
                   VALUES (?, ?, 'administrador', 'acepta', ?, ?)""", arrayOf<Any>(id, adminId, ahora, ahora),
            )
            w.execSQL(
                """INSERT OR IGNORE INTO reunion_participante (id_reunion, id_user, invitado_por, rol_participante, notificado_en)
                   SELECT ?, id_user, ?, 'propietario', ? FROM propietario_consorcio WHERE id_consorcio = ?""",
                arrayOf<Any>(id, adminId, ahora, consorcioId),
            )
            w.execSQL(
                """INSERT OR IGNORE INTO reunion_participante (id_reunion, id_user, invitado_por, rol_participante, notificado_en)
                   SELECT ?, a.id_user, ?, 'asistente', ? FROM edificios_asistente ea JOIN asistente a ON a.id_asistente = ea.id_user
                   WHERE ea.id_consorcio = ? AND ea.habilitado = 1 AND a.habilitado = 1""",
                arrayOf<Any>(id, adminId, ahora, consorcioId),
            )
            w.registrarHistorial(consorcioId, "reunion",
                "Se programó la reunión \"$titulo\" para el ${Reunion.diaHora(inicio.format(f))}.", adminId)
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        proveedores.forEach { invitar(id, titulo, consorcio, inicio.format(f), adminId, it) }
        id
    }

    /**
     * Invita a un proveedor como participante opcional: se guarda la invitación y le llega un mensaje
     * por chat. Devuelve false si ya estaba invitado.
     */
    suspend fun invitar(reunionId: Long, titulo: String, consorcio: String, fechaInicio: String, adminId: Long, proveedorId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            val participante = w.insertWithOnConflict("reunion_participante", null, ContentValues().apply {
                put("id_reunion", reunionId)
                put("id_user", proveedorId)
                put("invitado_por", adminId)
                put("rol_participante", "proveedor")
                put("es_opcional", 1)
                put("notificado_en", Reunion.ahora())
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            if (participante == -1L) return@withContext false
            val chat = ChatRepository(context).obtenerOCrearIndividual(adminId, proveedorId)
            w.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chat)
                put("id_user", adminId)
                put("mensaje", "Te invitamos a la reunión \"$titulo\" de $consorcio, el ${Reunion.diaHora(fechaInicio)}.")
                put("id_reunion_participante", participante)
                put("enviado_en", Reunion.ahora())
            })
            true
        }

    /** Cancela una invitación de proveedor que todavía no se respondió. */
    suspend fun cancelarInvitacion(participanteId: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(
            "reunion_participante", "id_reunion_participante = ? AND es_opcional = 1 AND respuesta = 'pendiente'",
            arrayOf(participanteId.toString()),
        )
    }

    /**
     * Guarda la respuesta del participante. Si la invitación le llegó por chat (proveedor),
     * la respuesta también queda como mensaje en ese chat.
     */
    suspend fun responder(participanteId: Long, usuarioId: Long, acepta: Boolean) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.update("reunion_participante", ContentValues().apply {
            put("respuesta", if (acepta) Reunion.ACEPTA else Reunion.RECHAZA)
            put("respondido_en", Reunion.ahora())
        }, "id_reunion_participante = ? AND id_user = ?", arrayOf(participanteId.toString(), usuarioId.toString()))

        val chat = w.rawQuery(
            "SELECT id_chat FROM mensaje_chat WHERE id_reunion_participante = ? LIMIT 1", arrayOf(participanteId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (chat != null) ChatRepository(context).enviar(
            chat, usuarioId,
            if (acepta) "Acepto la invitación, ahí estaré." else "Gracias por la invitación, pero no voy a poder participar.",
        )
    }

    /** Link de Meet cargado a mano (en la web se puede generar con Google Calendar). */
    suspend fun guardarLink(reunionId: Long, link: String?, codigo: String?) = withContext(Dispatchers.IO) {
        db.writableDatabase.update("reunion", ContentValues().apply {
            put("meet_link", link)
            put("meet_codigo", codigo)
        }, "id_reunion = ?", arrayOf(reunionId.toString()))
    }

    /** Después de empezar, el organizador marca quiénes asistieron (entre los que confirmaron). */
    suspend fun guardarAsistencia(reunionId: Long, presentes: Set<Long>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.execSQL("UPDATE reunion_participante SET asistio = 0 WHERE id_reunion = ? AND respuesta = 'acepta'", arrayOf<Any>(reunionId))
        presentes.forEach {
            w.execSQL("UPDATE reunion_participante SET asistio = 1 WHERE id_reunion_participante = ?", arrayOf<Any>(it))
        }
    }

    /** Cancela la reunión y la saca de la agenda. */
    suspend fun cancelar(reunionId: Long, usuarioId: Long? = null) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.rawQuery("SELECT id_consorcio, titulo FROM reunion WHERE id_reunion = ?", arrayOf(reunionId.toString())).use {
            if (it.moveToFirst()) w.registrarHistorial(it.getLong(0), "reunion", "Se canceló la reunión \"${it.getString(1)}\".", usuarioId)
        }
        w.execSQL("DELETE FROM agenda_evento WHERE id_evento = (SELECT id_evento FROM reunion WHERE id_reunion = ?)", arrayOf<Any>(reunionId))
        w.update("reunion", ContentValues().apply { put("estado", Reunion.CANCELADA) }, "id_reunion = ?", arrayOf(reunionId.toString()))
    }

    suspend fun eliminar(reunionId: Long) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.execSQL("DELETE FROM agenda_evento WHERE id_evento = (SELECT id_evento FROM reunion WHERE id_reunion = ?)", arrayOf<Any>(reunionId))
        w.delete("reunion", "id_reunion = ?", arrayOf(reunionId.toString()))
    }

    suspend fun agregarDoc(reunionId: Long, doc: ArchivoGuardado, tipo: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertOrThrow("doc_reunion", null, ContentValues().apply {
            put("id_reunion", reunionId)
            put("doc", doc.ruta)
            put("tipo", tipo)
        })
    }

    suspend fun borrarDoc(doc: DocReunion) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("doc_reunion", "id_doc_reunion = ?", arrayOf(doc.id.toString()))
        Archivos.borrar(doc.doc)
    }
}

private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
