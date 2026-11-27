package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Evento de la agenda (visita, reunión, vencimiento...). */
data class Evento(
    val id: Long,
    val titulo: String,
    val descripcion: String?,
    /** yyyy-MM-dd */
    val fecha: String,
    /** HH:mm, o null si es para todo el día */
    val hora: String?,
    val consorcio: String?,
    /** Lo creó el usuario logueado (solo esos se pueden borrar). */
    val propio: Boolean = true,
)

class AgendaRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    /**
     * Eventos desde hoy en adelante, ordenados por fecha y hora. El administrador ve los suyos;
     * el asistente, los suyos y los de sus edificios; el propietario, los de su consorcio.
     */
    suspend fun proximos(usuario: Usuario): List<Evento> = withContext(Dispatchers.IO) {
        val id = usuario.id.toString()
        val (filtro, args) = when {
            usuario.esAdmin -> "e.id_user = ?" to arrayOf(id)
            usuario.esPropietario -> "e.id_consorcio IN (${usuario.sqlConsorciosVisibles})" to arrayOf(id)
            else -> "(e.id_user = ? OR e.id_consorcio IN (${usuario.sqlConsorciosVisibles}))" to arrayOf(id, id)
        }
        db.readableDatabase.rawQuery(
            """SELECT e.id_evento, e.titulo, e.descripcion, e.fecha, e.hora, c.name_consorcio, e.id_user
               FROM agenda_evento e LEFT JOIN consorcio c ON c.id_consorcio = e.id_consorcio
               WHERE $filtro AND e.fecha >= ?
               ORDER BY e.fecha, e.hora IS NOT NULL, e.hora""",
            args + LocalDate.now().toString(),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Evento(
                            id = c.getLong(0),
                            titulo = c.getString(1),
                            descripcion = if (c.isNull(2)) null else c.getString(2),
                            fecha = c.getString(3),
                            hora = if (c.isNull(4)) null else c.getString(4),
                            consorcio = if (c.isNull(5)) null else c.getString(5),
                            propio = c.getLong(6) == usuario.id,
                        )
                    )
                }
            }
        }
    }

    suspend fun insert(
        idUsuario: Long,
        idConsorcio: Long?,
        titulo: String,
        descripcion: String?,
        fecha: String,
        hora: String?,
    ): Long = withContext(Dispatchers.IO) {
        db.writableDatabase.insertOrThrow("agenda_evento", null, ContentValues().apply {
            put("id_user", idUsuario)
            put("id_consorcio", idConsorcio)
            put("titulo", titulo)
            put("descripcion", descripcion)
            put("fecha", fecha)
            put("hora", hora)
        })
    }

    suspend fun delete(idEvento: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("agenda_evento", "id_evento = ?", arrayOf(idEvento.toString()))
    }
}
