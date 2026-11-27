package com.example.consora_mobile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.CuentaMarketplace
import com.example.consora_mobile.data.CuentaMarketplaceRepository
import com.example.consora_mobile.data.ImagenMarketplace
import com.example.consora_mobile.data.Resena
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Perfil de un proveedor del Marketplace: descripción, web, fotos publicadas y reseñas (cuentas_marketplace).
 * Servicio y destacados todavía no existen en la base y quedan vacíos.
 * Si es el perfil del usuario logueado, puede agregar fotos y borrarlas (manteniendo apretada la foto).
 */
class ProveedorPerfilFragment : Fragment() {

    private val args get() = requireArguments()
    private val proveedorId get() = args.getLong(ARG_ID)
    private val nombre get() = args.getString(ARG_NOMBRE).orEmpty()
    private val img get() = args.getString(ARG_IMG)
    private val repo get() = CuentaMarketplaceRepository(requireContext())

    private val esPropio get() = (requireActivity() as MainActivity).usuario?.id == proveedorId

    // Selector de fotos del sistema: no necesita pedir permisos de almacenamiento
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pedirDescripcion(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_perfil, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<TextView>(R.id.tvTitulo).text = nombre
        view.findViewById<TextView>(R.id.tvNombre).text = nombre
        view.findViewById<TextView>(R.id.tvSubtitulo).setText(R.string.marketplace_servicio_por_completar)
        mostrarAvatar(viewLifecycleOwner.lifecycleScope, view.findViewById(R.id.tvAvatar), view.findViewById(R.id.ivAvatar), nombre, img)

        val destacados = view.findViewById<LinearLayout>(R.id.destacadosRow)
        repeat(4) { destacados.addView(destacadoVacio(destacados)) }

        // No tiene sentido mandarse un mensaje a uno mismo
        view.findViewById<View>(R.id.btnMensaje).apply {
            visibility = if (esPropio) View.GONE else View.VISIBLE
            setOnClickListener { abrirChat() }
        }

        cargar()
    }

    private fun cargar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cuenta = repo.obtener(proveedorId)
            mostrarInfo(requireView(), cuenta)
            mostrarPublicaciones(requireView().findViewById(R.id.contenidoPerfil), cuenta?.imagenes.orEmpty())
        }
    }

    private fun mostrarInfo(view: View, cuenta: CuentaMarketplace?) {
        // Trabajos = publicaciones; tocando Reseñas o Calificación se abren las reseñas
        val resenas = cuenta?.resenas.orEmpty()
        val stats = view.findViewById<LinearLayout>(R.id.statsRow)
        stats.removeAllViews()
        stats.addView(stat(stats, cuenta?.imagenes?.size?.toString() ?: "0", getString(R.string.perfil_trabajos)))
        listOf(
            stat(stats, resenas.size.toString(), getString(R.string.perfil_resenas)),
            stat(stats, cuenta?.calificacion?.let(::formatoEstrellas), getString(R.string.perfil_calificacion)),
        ).forEach {
            it.setBackgroundResource(android.R.drawable.list_selector_background)
            it.setOnClickListener { verResenas(cuenta) }
            stats.addView(it)
        }

        cuenta?.descripcion?.takeIf { it.isNotBlank() }?.let {
            view.findViewById<TextView>(R.id.tvDescripcion).apply {
                text = it
                setTypeface(null, Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, R.color.consora_navy))
            }
        }

        // Link debajo de la descripción, sin "https://www." como en Instagram
        val link = cuenta?.link?.takeIf { it.isNotBlank() }
        view.findViewById<TextView>(R.id.tvLink).apply {
            visibility = if (link != null) View.VISIBLE else View.GONE
            text = link?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")?.trimEnd('/')
            setOnClickListener { link?.let(::abrirLink) }
        }

        view.findViewById<MaterialButton>(R.id.btnSecundario).apply {
            setText(R.string.perfil_contacto)
            // TODO: datos de contacto
            setOnClickListener { }
        }
    }

    /** Grilla de 3 columnas con las fotos; el dueño ve primero un cuadro para agregar una nueva. */
    private fun mostrarPublicaciones(contenido: LinearLayout, imagenes: List<ImagenMarketplace>) {
        contenido.removeAllViews()
        if (imagenes.isEmpty() && !esPropio) {
            contenido.addEmptyState(
                R.drawable.ic_grid,
                getString(R.string.perfil_sin_publicaciones_titulo),
                getString(R.string.perfil_sin_publicaciones_mensaje),
            )
            return
        }

        val celdas = buildList<View> {
            if (esPropio) add(celdaAgregar())
            imagenes.forEach { add(celdaImagen(it)) }
        }
        val lado = resources.displayMetrics.widthPixels / COLUMNAS
        val margen = (1 * resources.displayMetrics.density).toInt()
        celdas.chunked(COLUMNAS).forEach { fila ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            fila.forEach { celda ->
                row.addView(celda, LinearLayout.LayoutParams(0, lado, 1f).apply { setMargins(margen, margen, margen, margen) })
            }
            // Relleno para que la última fila no estire sus fotos
            repeat(COLUMNAS - fila.size) { row.addView(View(requireContext()), LinearLayout.LayoutParams(0, lado, 1f)) }
            contenido.addView(row)
        }
    }

    private fun celdaImagen(imagen: ImagenMarketplace) = ImageView(requireContext()).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(ContextCompat.getColor(context, R.color.consora_divider))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val ancho = resources.displayMetrics.widthPixels / COLUMNAS
        viewLifecycleOwner.lifecycleScope.launch {
            ConsorcioImagen.cargar(context, imagen.img, ancho)?.let(::setImageBitmap)
        }
        setOnClickListener { verImagen(imagen) }
        if (esPropio) setOnLongClickListener { confirmarBorrar(imagen); true }
    }

    private fun celdaAgregar() = FrameLayout(requireContext()).apply {
        setBackgroundResource(R.drawable.bg_destacado_vacio)
        contentDescription = getString(R.string.perfil_agregar_foto)
        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_add)
                setColorFilter(ContextCompat.getColor(context, R.color.consora_navy))
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER,
            ),
        )
        setOnClickListener {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    /** Hoja con las reseñas; quien no es el dueño puede escribir (o editar) la suya. */
    private fun verResenas(cuenta: CuentaMarketplace?) {
        val usuario = (requireActivity() as MainActivity).usuario ?: return
        val resenas = cuenta?.resenas.orEmpty()
        val sheet = BottomSheetDialog(requireContext())
        val contenido = layoutInflater.inflate(R.layout.sheet_resenas, null)

        contenido.findViewById<TextView>(R.id.tvResumen).text = cuenta?.calificacion?.let {
            resources.getQuantityString(R.plurals.resenas_resumen, resenas.size, formatoEstrellas(it), resenas.size)
        } ?: getString(R.string.resenas_vacio)

        val mia = resenas.firstOrNull { it.autorId == usuario.id }
        contenido.findViewById<MaterialButton>(R.id.btnEscribir).apply {
            visibility = if (esPropio) View.GONE else View.VISIBLE
            setText(if (mia != null) R.string.resena_editar else R.string.resena_escribir)
            setOnClickListener {
                sheet.dismiss()
                escribirResena(usuario.id, mia)
            }
        }

        val lista = contenido.findViewById<LinearLayout>(R.id.listaResenas)
        resenas.forEach { lista.addView(itemResena(lista, it)) }

        sheet.setContentView(contenido)
        sheet.show()
    }

    private fun itemResena(parent: ViewGroup, r: Resena): View =
        layoutInflater.inflate(R.layout.item_resena, parent, false).apply {
            mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), r.autorNombre, r.autorImg)
            findViewById<TextView>(R.id.tvNombre).text = r.autorNombre
            findViewById<TextView>(R.id.tvEstrellas).text = "★".repeat(r.estrellas) + "☆".repeat(5 - r.estrellas)
            findViewById<TextView>(R.id.tvFecha).text = Formatos.fecha(r.creation.take(10))
            findViewById<TextView>(R.id.tvTexto).apply {
                text = r.descripcion
                visibility = if (r.descripcion.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }

    private fun escribirResena(autor: Long, anterior: Resena?) {
        val vista = layoutInflater.inflate(R.layout.dialog_resena, null)
        val rating = vista.findViewById<RatingBar>(R.id.ratingBar)
        val et = vista.findViewById<EditText>(R.id.etResena)
        anterior?.let {
            rating.rating = it.estrellas.toFloat()
            et.setText(it.descripcion)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (anterior != null) R.string.resena_editar else R.string.resena_escribir)
            .setView(vista)
            .setPositiveButton(R.string.resena_guardar) { _, _ ->
                val estrellas = rating.rating.toInt().coerceIn(1, 5)
                val texto = et.text.toString().trim().ifEmpty { null }
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.guardarResena(proveedorId, autor, estrellas, texto)
                    Toast.makeText(requireContext(), R.string.resena_guardada, Toast.LENGTH_SHORT).show()
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 4.666 -> "4,7" */
    private fun formatoEstrellas(promedio: Double) = String.format(Locale("es", "AR"), "%.1f", promedio)

    /** Texto opcional de la publicación; después guarda la foto. */
    private fun pedirDescripcion(uri: Uri) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val et = EditText(requireContext()).apply {
            setHint(R.string.perfil_publicacion_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
        }
        val contenedor = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perfil_nueva_publicacion)
            .setView(contenedor)
            .setPositiveButton(R.string.perfil_publicar) { _, _ ->
                val descripcion = et.text.toString().trim().ifEmpty { null }
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = runCatching { repo.agregarImagen(proveedorId, uri, descripcion) }.isSuccess
                    if (!ok) Toast.makeText(requireContext(), R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Muestra la publicación: foto en grande, texto y fecha. */
    private fun verImagen(imagen: ImagenMarketplace) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val iv = ImageView(requireContext()).apply {
            adjustViewBounds = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ConsorcioImagen.cargar(requireContext(), imagen.img, resources.displayMetrics.widthPixels)?.let(iv::setImageBitmap)
        }
        val vista = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(iv)
            imagen.descripcion?.let {
                addView(TextView(context).apply {
                    text = it
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(context, R.color.consora_navy))
                    setPadding(padding, padding / 2, padding, 0)
                })
            }
            addView(TextView(context).apply {
                text = Formatos.fecha(imagen.creation.take(10))
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.consora_gray))
                setPadding(padding, padding / 4, padding, padding / 2)
            })
        }
        MaterialAlertDialogBuilder(requireContext()).setView(vista).show()
    }

    private fun confirmarBorrar(imagen: ImagenMarketplace) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perfil_borrar_foto)
            .setPositiveButton(R.string.perfil_borrar) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.borrarImagen(imagen)
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun abrirLink(link: String) {
        val url = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://$link"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.perfil_sin_navegador, Toast.LENGTH_SHORT).show()
        }
    }

    private fun destacadoVacio(parent: ViewGroup) = View(parent.context).apply {
        val size = (64 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(margin, 0, margin, 0) }
        setBackgroundResource(R.drawable.bg_destacado_vacio)
    }

    private fun abrirChat() =
        abrirChatCon(proveedorId, nombre, getString(R.string.rol_proveedor), img)

    companion object {
        private const val COLUMNAS = 3
        private const val ARG_ID = "proveedor_id"
        private const val ARG_NOMBRE = "proveedor_nombre"
        private const val ARG_IMG = "proveedor_img"

        fun newInstance(id: Long, nombre: String, img: String?) = ProveedorPerfilFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, id)
                putString(ARG_NOMBRE, nombre)
                putString(ARG_IMG, img)
            }
        }
    }
}
