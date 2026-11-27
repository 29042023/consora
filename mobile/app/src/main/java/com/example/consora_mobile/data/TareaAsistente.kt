package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Tarea que el administrador le asigna a un asistente. */
data class TareaAsistente(
    val id: Long,
    val descripcion: String,
    val estado: String,
    /** "yyyy-MM-dd HH:mm:ss" */
    val createdAt: String,
) {
    val completada get() = estado == COMPLETADO

    companion object {
        // Valores posibles de la columna estado
        const val EN_PROCESO = "en proceso"
        const val COMPLETADO = "completado"
    }
}

class TareaAsistenteRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).writableDatabase

    /** Tareas del asistente: primero las en proceso, después las completadas; las más nuevas arriba. */
    suspend fun listar(idAsistente: Long): List<TareaAsistente> = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT id_tarea, descripcion, estado, created_at FROM tareas_asistente
               WHERE id_user = ? ORDER BY estado = ? , created_at DESC""",
            arrayOf(idAsistente.toString(), TareaAsistente.COMPLETADO),
        ).use { c ->
            buildList { while (c.moveToNext()) add(TareaAsistente(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))) }
        }
    }

    suspend fun crear(idAsistente: Long, descripcion: String) = withContext(Dispatchers.IO) {
        db.insertOrThrow("tareas_asistente", null, ContentValues().apply {
            put("id_user", idAsistente)
            put("descripcion", descripcion)
            put("estado", TareaAsistente.EN_PROCESO)
            put("created_at", LocalDateTime.now().format(ChatFormato.FECHA_HORA))
        })
    }

    /** Marca la tarea como completada o la vuelve a poner en proceso. */
    suspend fun cambiarEstado(idTarea: Long, completada: Boolean) = withContext(Dispatchers.IO) {
        db.update(
            "tareas_asistente",
            ContentValues().apply { put("estado", if (completada) TareaAsistente.COMPLETADO else TareaAsistente.EN_PROCESO) },
            "id_tarea = ?",
            arrayOf(idTarea.toString()),
        )
    }

    suspend fun borrar(idTarea: Long) = withContext(Dispatchers.IO) {
        db.delete("tareas_asistente", "id_tarea = ?", arrayOf(idTarea.toString()))
    }
}
