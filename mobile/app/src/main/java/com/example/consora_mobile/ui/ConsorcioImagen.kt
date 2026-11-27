package com.example.consora_mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import com.example.consora_mobile.data.Carpeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Imagen de los consorcios: degradado + ilustración por defecto, o una foto elegida. */
object ConsorcioImagen {

    /** Prefijo de las imágenes incluidas en la app (app/src/main/assets). */
    const val ASSET = "asset:"

    /** Pares de colores (arriba, abajo); cada consorcio usa uno según su id. */
    private val degradados = listOf(
        0xFF0D2A4F.toInt() to 0xFF14A3A3.toInt(),
        0xFF14A3A3.toInt() to 0xFF0B6E78.toInt(),
        0xFF1E4A7A.toInt() to 0xFF4FB8C9.toInt(),
        0xFF0D2A4F.toInt() to 0xFF2C6E9B.toInt(),
        0xFF0B6E78.toInt() to 0xFF0D2A4F.toInt(),
    )

    fun fondo(idConsorcio: Long) = degradados[(idConsorcio % degradados.size).toInt()].let { (arriba, abajo) ->
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(arriba, abajo))
    }

    /** Copia la foto elegida al almacenamiento interno de la app y devuelve la ruta. */
    suspend fun guardar(context: Context, uri: Uri, idConsorcio: Long): String = withContext(Dispatchers.IO) {
        val dir = Carpeta.IMG_CONSORCIOS.dir(context)
        // Nombre nuevo cada vez, así no queda en caché la foto anterior
        dir.listFiles { f -> f.name.startsWith("consorcio_${idConsorcio}_") }?.forEach { it.delete() }
        val destino = File(dir, "consorcio_${idConsorcio}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            destino.outputStream().use { input.copyTo(it) }
        }
        destino.absolutePath
    }

    /**
     * Lee una imagen reducida al ancho pedido, para no gastar memoria de más.
     * [ref] puede ser "asset:img/..." (imagen incluida en la app, en assets) o la ruta de un archivo.
     */
    suspend fun cargar(context: Context, ref: String, anchoPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val decode: (BitmapFactory.Options) -> Bitmap?
        if (ref.startsWith(ASSET)) {
            val ruta = ref.removePrefix(ASSET)
            if (runCatching { context.assets.open(ruta).close() }.isFailure) return@withContext null
            decode = { opts -> context.assets.open(ruta).use { BitmapFactory.decodeStream(it, null, opts) } }
        } else {
            if (!File(ref).exists()) return@withContext null
            decode = { BitmapFactory.decodeFile(ref, it) }
        }
        val tam = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(tam)
        var escala = 1
        while (tam.outWidth / (escala * 2) >= anchoPx) escala *= 2
        decode(BitmapFactory.Options().apply { inSampleSize = escala })
    }
}
