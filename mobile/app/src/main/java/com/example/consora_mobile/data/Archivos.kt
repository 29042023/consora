package com.example.consora_mobile.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.consora_mobile.R
import com.example.consora_mobile.ui.ConsorcioImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Archivo elegido por el usuario y ya copiado al almacenamiento de la app. */
data class ArchivoGuardado(val ruta: String, val nombre: String)

/** Archivos adjuntos (chat, votaciones, títulos): validar, copiar y abrir con la app que corresponda. */
object Archivos {

    /** Tipos que se pueden adjuntar: PDF, Word, Excel y fotos (igual que en la web). */
    val MIME_PERMITIDOS = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/csv",
        "image/jpeg", "image/png", "image/gif", "image/webp",
    )
    private val EXTENSIONES = setOf("pdf", "doc", "docx", "xls", "xlsx", "csv", "jpg", "jpeg", "png", "gif", "webp")
    const val TAMANO_MAXIMO = 15L * 1024 * 1024

    fun extension(nombre: String) = nombre.substringAfterLast('.', "").lowercase()

    fun esImagen(nombre: String) = extension(nombre) in setOf("jpg", "jpeg", "png", "gif", "webp")

    /** "pdf" | "word" | "excel" | "imagen" | "archivo", para elegir el ícono. */
    fun tipo(nombre: String) = when (extension(nombre)) {
        "pdf" -> "pdf"
        "doc", "docx" -> "word"
        "xls", "xlsx", "csv" -> "excel"
        "jpg", "jpeg", "png", "gif", "webp" -> "imagen"
        else -> "archivo"
    }

    /** Nombre y tamaño del archivo elegido. */
    private fun datos(context: Context, uri: Uri): Pair<String, Long> =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "archivo") to (if (c.isNull(1)) 0L else c.getLong(1)) else null
            } ?: ("archivo" to 0L)

    /** Mensaje de error (R.string) si el archivo no se puede adjuntar, o null si está bien. */
    fun validar(context: Context, uri: Uri, soloPdf: Boolean = false): Int? {
        val (nombre, tamano) = datos(context, uri)
        val ext = extension(nombre)
        return when {
            soloPdf && ext != "pdf" -> R.string.archivo_solo_pdf
            ext !in EXTENSIONES -> R.string.archivo_tipo_invalido
            tamano > TAMANO_MAXIMO -> R.string.archivo_muy_grande
            else -> null
        }
    }

    /**
     * Copia el archivo a [carpeta]/[subcarpeta] como "<código>_<nombre original>"
     * (el código evita pisar archivos con el mismo nombre).
     */
    suspend fun guardar(context: Context, uri: Uri, carpeta: Carpeta, subcarpeta: String? = null): ArchivoGuardado =
        withContext(Dispatchers.IO) {
            val nombre = datos(context, uri).first.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val dir = carpeta.dir(context).let { if (subcarpeta != null) File(it, subcarpeta).apply { mkdirs() } else it }
            val destino = File(dir, "${UUID.randomUUID().toString().take(8)}_$nombre")
            context.contentResolver.openInputStream(uri)!!.use { input -> destino.outputStream().use { input.copyTo(it) } }
            ArchivoGuardado(destino.absolutePath, nombre)
        }

    /** Nombre para mostrar: el original, sin el código del principio. */
    fun nombreVisible(ref: String): String =
        ref.substringAfterLast('/').let { if (it.length > 9 && it[8] == '_') it.substring(9) else it }

    /** Borra un archivo guardado por la app (los incluidos en la app, "asset:...", no se tocan). */
    fun borrar(ref: String?) {
        if (ref != null && !ref.startsWith(ConsorcioImagen.ASSET)) File(ref).delete()
    }

    /** Abre el archivo con la app que corresponda (visor de PDF, Word, Excel, galería...). */
    suspend fun abrir(context: Context, ref: String, nombre: String = nombreVisible(ref)) {
        val archivo = withContext(Dispatchers.IO) {
            if (ref.startsWith(ConsorcioImagen.ASSET)) {
                // Los incluidos en la app se copian primero a la caché, porque otra app no puede leer los assets
                File(context.cacheDir, "abrir").apply { mkdirs() }.let { dir ->
                    File(dir, nombre).also { destino ->
                        context.assets.open(ref.removePrefix(ConsorcioImagen.ASSET)).use { input ->
                            destino.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            } else File(ref).takeIf { it.exists() }
        }
        if (archivo == null) {
            Toast.makeText(context, R.string.archivo_no_encontrado, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        val mime = when (tipo(nombre)) {
            "pdf" -> "application/pdf"
            "word" -> "application/msword"
            "excel" -> "application/vnd.ms-excel"
            "imagen" -> "image/*"
            else -> "*/*"
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.archivo_sin_app, Toast.LENGTH_SHORT).show()
        }
    }
}
