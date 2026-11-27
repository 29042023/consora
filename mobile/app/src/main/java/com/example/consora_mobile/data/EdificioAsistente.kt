package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Edificios en los que un asistente está habilitado (tabla edificios_asistente).
 * Los métodos reciben el id_user del asistente; la tabla guarda su id_asistente.
 */
class EdificioAsistenteRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context).writableDatabase

    /** Ids de los consorcios en los que el asistente está habilitado. */
    suspend fun habilitados(idUserAsistente: Long): Set<Long> = withContext(Dispatchers.IO) {
        db.rawQuery(
            """SELECT ea.id_consorcio FROM edificios_asistente ea
               JOIN asistente a ON a.id_asistente = ea.id_user
               WHERE a.id_user = ? AND ea.habilitado = 1""",
            arrayOf(idUserAsistente.toString()),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    /** Habilita o deshabilita al asistente en el edificio (crea la fila si no existía). */
    suspend fun cambiar(idUserAsistente: Long, idConsorcio: Long, habilitado: Boolean) = withContext(Dispatchers.IO) {
        val idAsistente = db.rawQuery(
            "SELECT id_asistente FROM asistente WHERE id_user = ?", arrayOf(idUserAsistente.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else return@withContext }

        db.insertWithOnConflict("edificios_asistente", null, ContentValues().apply {
            put("id_consorcio", idConsorcio)
            put("id_user", idAsistente)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.update(
            "edificios_asistente",
            ContentValues().apply { put("habilitado", if (habilitado) 1 else 0) },
            "id_consorcio = ? AND id_user = ?",
            arrayOf(idConsorcio.toString(), idAsistente.toString()),
        )
    }
}
