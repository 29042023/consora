package com.example.consora_mobile.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Carpetas del almacenamiento interno de la app donde se guardan los archivos que carga el usuario:
 *
 * ```
 * img/
 *   usuarios/        fotos de perfil
 *   publicaciones/   fotos de las publicaciones de los proveedores
 *   consorcios/      fotos de los consorcios
 * docs/
 *   facturas/        PDF o imagen de cada factura
 *   titulos/         PDF del título de cada administrador
 *   chats/           archivos y fotos enviados por chat (una carpeta por conversación)
 *   votaciones/      propuestas y documentos de las votaciones (una carpeta por votación)
 *   docgeneral/      otros documentos
 * ```
 */
enum class Carpeta(private val ruta: String) {
    IMG_USUARIOS("img/usuarios"),
    IMG_PUBLICACIONES("img/publicaciones"),
    IMG_CONSORCIOS("img/consorcios"),
    DOCS_FACTURAS("docs/facturas"),
    DOCS_TITULOS("docs/titulos"),
    DOCS_CHATS("docs/chats"),
    DOCS_VOTACIONES("docs/votaciones"),
    DOCS_REUNIONES("docs/reuniones"),
    DOCS_CONTRATOS("docs/contratos"),
    DOCS_GENERAL("docs/docgeneral");

    /** Devuelve la carpeta (la crea si todavía no existe). */
    fun dir(context: Context): File = File(context.filesDir, ruta).apply { mkdirs() }

    /** Copia el archivo elegido por el usuario a esta carpeta y devuelve la ruta. Llamar fuera del hilo principal. */
    fun guardar(context: Context, uri: Uri, nombre: String): String {
        val destino = File(dir(context), nombre)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            destino.outputStream().use { input.copyTo(it) }
        }
        return destino.absolutePath
    }

    companion object {
        /** Crea todas las carpetas; se llama al abrir la app. */
        fun crearTodas(context: Context) = entries.forEach { it.dir(context) }
    }
}
