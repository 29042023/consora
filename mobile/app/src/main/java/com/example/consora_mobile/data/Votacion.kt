package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Votación de un consorcio (misma estructura que en la web). */
data class Votacion(
    val id: Long,
    val consorcioId: Long,
    val consorcio: String,
    /** "comun" | "contratacion" */
    val tipo: String,
    val detalle: String,
    /** yyyy-MM-dd HH:mm:ss */
    val fecha: String,
    val fechaLimite: String,
    val cantidadVotos: Int,
    val cantidadPropuestas: Int,
    /** Contratación: cuántos proveedores aceptaron participar. */
    val aceptadas: Int,
    val yaVote: Boolean,
    /** Administrador del consorcio: el único que gestiona la votación. */
    val representanteId: Long,
) {
    val esContratacion get() = tipo == CONTRATACION
    val abierta get() = fechaLimite > ahora()

    /** Una votación común arranca al crearla; una de contratación, cuando algún proveedor acepta. */
    val lanzada get() = !esContratacion || aceptadas > 0

    companion object {
        const val COMUN = "comun"
        const val CONTRATACION = "contratacion"
        const val NINGUNO = "ninguno"
        fun ahora(): String = LocalDateTime.now().format(ChatFormato.FECHA_HORA)
    }
}

/** Proveedor invitado a una contratación. */
data class Propuesta(
    val id: Long,
    val proveedorId: Long,
    val proveedor: String,
    val img: String?,
    val pdf: String?,
    /** null = no respondió, true = aceptó, false = rechazó */
    val aceptacion: Boolean?,
    val contratado: Boolean,
)

/** Una opción de voto con su cantidad de votos. */
data class OpcionVoto(val valor: String, val texto: String, val cantidad: Int, val porcentaje: Int, val gana: Boolean)

data class Voto(val usuario: String, val voto: String)

data class DocVotacion(val id: Long, val doc: String) {
    val nombre get() = Archivos.nombreVisible(doc)
}

data class DetalleVotacion(
    val votacion: Votacion,
    val propuestas: List<Propuesta>,
    val opciones: List<OpcionVoto>,
    val votos: List<Voto>,
    val documentos: List<DocVotacion>,
    val miVoto: String?,
    val puedoVotar: Boolean,
    /** Quiénes pueden votar: propietarios del consorcio + su administrador. */
    val habilitados: Int,
)

/** Proveedor que se puede invitar a una contratación. */
data class ProveedorInvitable(val id: Long, val nombre: String, val img: String?)

class VotacionRepository(private val context: Context) {

    private val db get() = ConsoraDatabase.get(context)

    private val sqlVotacion = """
        SELECT v.id_votacion, v.id_consorcio, c.name_consorcio, v.tipo, v.detalle, v.fecha, v.fecha_limite,
               (SELECT COUNT(*) FROM votacion_user vu WHERE vu.id_votacion = v.id_votacion),
               (SELECT COUNT(*) FROM votacion_contrato vc WHERE vc.id_votacion = v.id_votacion),
               (SELECT COUNT(*) FROM votacion_contrato vc WHERE vc.id_votacion = v.id_votacion AND vc.aceptacion = 1),
               EXISTS (SELECT 1 FROM votacion_user vu WHERE vu.id_votacion = v.id_votacion AND vu.id_user = ?),
               c.representante
        FROM votacion v JOIN consorcio c ON c.id_consorcio = v.id_consorcio"""

    private fun Cursor.votacion() = Votacion(
        getLong(0), getLong(1), getString(2), getString(3), getString(4), getString(5), getString(6),
        getInt(7), getInt(8), getInt(9), getInt(10) == 1, getLong(11),
    )

    /** Votaciones de los consorcios que ve el usuario (todas o las de un consorcio). */
    suspend fun listar(usuario: Usuario, consorcioId: Long? = null): List<Votacion> = withContext(Dispatchers.IO) {
        val filtro = if (consorcioId != null) " AND v.id_consorcio = $consorcioId" else ""
        db.readableDatabase.rawQuery(
            "$sqlVotacion WHERE v.id_consorcio IN (${usuario.sqlConsorciosVisibles})$filtro ORDER BY v.fecha DESC",
            arrayOf(usuario.id.toString(), usuario.id.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.votacion()) } }
    }

    suspend fun detalle(votacionId: Long, usuarioId: Long): DetalleVotacion? = withContext(Dispatchers.IO) {
        val read = db.readableDatabase
        val v = read.rawQuery("$sqlVotacion WHERE v.id_votacion = ?", arrayOf(usuarioId.toString(), votacionId.toString()))
            .use { if (it.moveToFirst()) it.votacion() else null } ?: return@withContext null

        val propuestas = read.rawQuery(
            """SELECT vc.id_votacion_contrato, u.id_user, u.name_surname, u.img, vc.propuesta_pdf, vc.aceptacion, vc.contratado
               FROM votacion_contrato vc JOIN user u ON u.id_user = vc.id_proveedor
               WHERE vc.id_votacion = ? ORDER BY u.name_surname""",
            arrayOf(votacionId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Propuesta(
                        c.getLong(0), c.getLong(1), c.getString(2), c.str(3), c.str(4),
                        if (c.isNull(5)) null else c.getInt(5) == 1, c.getInt(6) == 1,
                    )
                )
            }
        }

        val votosCrudos = read.rawQuery(
            """SELECT u.name_surname, vu.voto, vu.id_user FROM votacion_user vu JOIN user u ON u.id_user = vu.id_user
               WHERE vu.id_votacion = ? ORDER BY vu.votado_en DESC""",
            arrayOf(votacionId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getLong(2))) } }

        // Opciones: Sí / No / Abstención, o en contratación los que aceptaron + "No contratar a ninguno"
        val opcionesBase = if (v.esContratacion) {
            propuestas.filter { it.aceptacion == true }.map { it.id.toString() to it.proveedor } +
                (Votacion.NINGUNO to "No contratar a ninguno")
        } else listOf("si" to "Sí", "no" to "No", "abstencion" to "Abstención")
        val total = votosCrudos.size
        val conteo = opcionesBase.map { (valor, _) -> votosCrudos.count { it.second == valor } }
        val maximo = conteo.maxOrNull() ?: 0
        val opciones = opcionesBase.mapIndexed { i, (valor, texto) ->
            OpcionVoto(valor, texto, conteo[i], if (total > 0) conteo[i] * 100 / total else 0, maximo > 0 && conteo[i] == maximo)
        }
        val textoDe = opcionesBase.toMap()

        val documentos = read.rawQuery(
            "SELECT id_doc_votacion, doc FROM doc_votacion WHERE id_votacion = ? ORDER BY id_doc_votacion",
            arrayOf(votacionId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(DocVotacion(c.getLong(0), c.getString(1))) } }

        val votantes = votantes(v.consorcioId)
        DetalleVotacion(
            votacion = v,
            propuestas = propuestas,
            opciones = opciones,
            votos = votosCrudos.map { Voto(it.first, textoDe[it.second] ?: it.second) },
            documentos = documentos,
            miVoto = votosCrudos.firstOrNull { it.third == usuarioId }?.second,
            puedoVotar = usuarioId in votantes,
            habilitados = votantes.size,
        )
    }

    /** Propietarios del consorcio y su administrador. */
    private fun votantes(consorcioId: Long): Set<Long> = db.readableDatabase.rawQuery(
        """SELECT id_user FROM propietario_consorcio WHERE id_consorcio = ?
           UNION SELECT representante FROM consorcio WHERE id_consorcio = ?""",
        arrayOf(consorcioId.toString(), consorcioId.toString()),
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    suspend fun proveedores(): List<ProveedorInvitable> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT u.id_user, u.name_surname, u.img FROM user u JOIN roles r ON r.id_rol = u.role
               LEFT JOIN estados_cuenta e ON e.id_estado_cuenta = u.id_estado_cuenta
               WHERE r.rol = 'Proveedor' AND (e.estado IS NULL OR e.estado = 'Activa') ORDER BY u.name_surname""",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(ProveedorInvitable(c.getLong(0), c.getString(1), c.str(2))) } }
    }

    /** Crea la votación con sus documentos; en contratación invita por chat a los proveedores. */
    suspend fun crear(
        adminId: Long, consorcioId: Long, consorcio: String, tipo: String, detalle: String, fechaLimite: String,
        proveedores: List<Long>, documentos: List<ArchivoGuardado>,
    ): Long = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        val id = w.insertOrThrow("votacion", null, ContentValues().apply {
            put("id_consorcio", consorcioId)
            put("tipo", tipo)
            put("detalle", detalle)
            put("fecha", Votacion.ahora())
            put("fecha_limite", fechaLimite)
        })
        documentos.forEach { agregarDoc(id, it) }
        if (tipo == Votacion.CONTRATACION) proveedores.forEach { invitar(id, consorcio, detalle, adminId, it) }
        id
    }

    /** Invita a un proveedor: se guarda la invitación y le llega un mensaje por chat. */
    suspend fun invitar(votacionId: Long, consorcio: String, detalle: String, adminId: Long, proveedorId: Long) =
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            val invitacion = w.insertOrThrow("votacion_contrato", null, ContentValues().apply {
                put("id_votacion", votacionId)
                put("id_proveedor", proveedorId)
            })
            val chat = ChatRepository(context).obtenerOCrearIndividual(adminId, proveedorId)
            w.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chat)
                put("id_user", adminId)
                put("mensaje", "Te invitamos a participar en una votación de contratación de $consorcio: $detalle")
                put("id_votacion_contrato", invitacion)
                put("enviado_en", Votacion.ahora())
            })
        }

    /** Cancela una invitación que todavía no se respondió (el mensaje queda sin la tarjeta). */
    suspend fun cancelarInvitacion(propuestaId: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("votacion_contrato", "id_votacion_contrato = ? AND aceptacion IS NULL", arrayOf(propuestaId.toString()))
    }

    /**
     * El proveedor acepta o rechaza desde el chat. Al aceptar puede adjuntar su propuesta en PDF;
     * la respuesta queda como mensaje en el chat.
     */
    suspend fun responder(invitacionId: Long, chatId: Long, proveedorId: Long, acepta: Boolean, pdf: ArchivoGuardado?) =
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            w.update("votacion_contrato", ContentValues().apply {
                put("aceptacion", if (acepta) 1 else 0)
                if (acepta && pdf != null) put("propuesta_pdf", pdf.ruta)
            }, "id_votacion_contrato = ? AND id_proveedor = ? AND aceptacion IS NULL", arrayOf(invitacionId.toString(), proveedorId.toString()))
            ChatRepository(context).enviar(
                chatId, proveedorId,
                if (!acepta) "Gracias por la invitación, pero esta vez no voy a participar."
                else "Acepto participar en la votación." + if (pdf != null) " Te adjunto mi propuesta." else "",
                if (acepta) pdf?.copy(nombre = "Propuesta.pdf") else null,
            )
        }

    suspend fun votar(votacionId: Long, usuarioId: Long, voto: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict("votacion_user", null, ContentValues().apply {
            put("id_votacion", votacionId)
            put("id_user", usuarioId)
            put("voto", voto)
            put("votado_en", Votacion.ahora())
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun cerrar(votacionId: Long, usuarioId: Long? = null) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.update("votacion", ContentValues().apply { put("fecha_limite", Votacion.ahora()) },
            "id_votacion = ?", arrayOf(votacionId.toString()))
        w.rawQuery("SELECT id_consorcio, detalle FROM votacion WHERE id_votacion = ?", arrayOf(votacionId.toString())).use {
            if (it.moveToFirst()) w.registrarHistorial(it.getLong(0), "votacion", "Se cerró la votación: ${it.getString(1).take(180)}", usuarioId)
        }
    }

    /** Al cerrar, el administrador marca a qué proveedor se contrata. */
    suspend fun contratar(votacionId: Long, propuestaId: Long, usuarioId: Long? = null) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.execSQL("UPDATE votacion_contrato SET contratado = (id_votacion_contrato = ?) WHERE id_votacion = ?",
            arrayOf<Any>(propuestaId, votacionId))
        w.rawQuery(
            """SELECT v.id_consorcio, v.detalle, u.name_surname FROM votacion_contrato vc
               JOIN votacion v ON v.id_votacion = vc.id_votacion JOIN user u ON u.id_user = vc.id_proveedor
               WHERE vc.id_votacion_contrato = ?""", arrayOf(propuestaId.toString()),
        ).use {
            if (it.moveToFirst()) w.registrarHistorial(it.getLong(0), "votacion",
                "Por votación se eligió a ${it.getString(2)}: ${it.getString(1).take(150)}", usuarioId)
        }
    }

    suspend fun agregarDoc(votacionId: Long, doc: ArchivoGuardado) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertOrThrow("doc_votacion", null, ContentValues().apply {
            put("id_votacion", votacionId)
            put("doc", doc.ruta)
        })
    }

    suspend fun borrarDoc(doc: DocVotacion) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("doc_votacion", "id_doc_votacion = ?", arrayOf(doc.id.toString()))
        Archivos.borrar(doc.doc)
    }

    suspend fun eliminar(votacionId: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("votacion", "id_votacion = ?", arrayOf(votacionId.toString()))
    }
}

private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
