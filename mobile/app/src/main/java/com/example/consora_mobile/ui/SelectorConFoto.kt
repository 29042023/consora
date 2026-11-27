package com.example.consora_mobile.ui

import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Opción con foto para [elegirConFoto]: título, una línea de info y un dato a la derecha (ej. "⭐ 4,5 (2)"). */
data class OpcionFoto(val id: Long, val titulo: String, val sub: String?, val img: String?, val extra: String? = null)

private fun normalizar(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/**
 * Lista con buscador, foto (o iniciales), nombre e info. Al tocar una opción se elige y se cierra.
 * La búsqueda no distingue tildes y busca también en la línea de info (ej. "ascensor", "plomería").
 */
fun Fragment.elegirConFoto(titulo: Int, buscar: Int, opciones: List<OpcionFoto>, elegido: Long?, alElegir: (OpcionFoto) -> Unit) {
    val dp = resources.displayMetrics.density
    val contenedor = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
    }
    val buscador = layoutInflater.inflate(R.layout.view_buscador, contenedor, false)
    buscador.findViewById<View>(R.id.btnFiltros).visibility = View.GONE
    val etBuscar = buscador.findViewById<EditText>(R.id.etBuscar).apply { setHint(buscar) }
    contenedor.addView(buscador)
    val lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    val vacio = TextView(requireContext()).apply {
        setText(R.string.selector_sin_resultados)
        setTextColor(context.getColor(R.color.consora_gray))
        setPadding(0, (12 * dp).toInt(), 0, 0)
        visibility = View.GONE
    }
    contenedor.addView(android.widget.ScrollView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (380 * dp).toInt())
        addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(lista); addView(vacio) })
    })

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setView(contenedor)
        .setNegativeButton(R.string.cancel, null)
        .create()

    // Cada fila reutiliza el ítem del Marketplace: foto, nombre, info y puntaje
    val filas = opciones.map { o ->
        layoutInflater.inflate(R.layout.item_proveedor, lista, false).apply {
            mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), o.titulo, o.img)
            findViewById<TextView>(R.id.tvNombre).text = if (o.id == elegido) "✓  ${o.titulo}" else o.titulo
            findViewById<TextView>(R.id.tvServicio)?.apply {
                text = o.sub
                visibility = if (o.sub.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            findViewById<View>(R.id.ivEstrella)?.visibility = View.GONE
            findViewById<TextView>(R.id.tvEstrellas)?.apply {
                text = o.extra
                visibility = if (o.extra.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            setOnClickListener { dialog.dismiss(); alElegir(o) }
            lista.addView(this)
        } to normalizar(o.titulo + " " + o.sub.orEmpty())
    }
    etBuscar.doAfterTextChanged { q ->
        val texto = normalizar(q?.toString()?.trim().orEmpty())
        filas.forEach { (fila, busqueda) -> fila.visibility = if (busqueda.contains(texto)) View.VISIBLE else View.GONE }
        vacio.visibility = if (filas.none { it.first.visibility == View.VISIBLE }) View.VISIBLE else View.GONE
    }
    dialog.show()
}

/** Muestra la foto de la opción elegida al inicio de un campo (o la saca si no hay). */
fun Fragment.fotoEnCampo(til: com.google.android.material.textfield.TextInputLayout, img: String?) {
    til.startIconDrawable = null
    if (img == null) return
    viewLifecycleOwner.lifecycleScope.launch {
        val bitmap = ConsorcioImagen.cargar(requireContext(), img, 96) ?: return@launch
        val redonda = androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(resources, bitmap).apply { isCircular = true }
        til.startIconDrawable = redonda
        til.setStartIconTintList(null)
    }
}

/** Selector de un PDF del teléfono. Se crea como propiedad del Fragment (antes de que arranque). */
class SelectorPdf(fragment: Fragment) {
    private var alElegir: ((Uri) -> Unit)? = null
    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) alElegir?.invoke(uri)
    }

    fun elegir(alElegir: (Uri) -> Unit) {
        this.alElegir = alElegir
        launcher.launch(arrayOf("application/pdf"))
    }
}
