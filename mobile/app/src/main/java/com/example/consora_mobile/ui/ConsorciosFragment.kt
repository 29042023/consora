package com.example.consora_mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.FacturaFormActivity
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import kotlinx.coroutines.launch

class ConsorciosFragment : Fragment() {

    /** Consorcio al que se le está cambiando la foto (mientras está abierta la galería). */
    private var consorcioFotoId: Long? = null

    private var consorcios: List<Consorcio> = emptyList()
    private lateinit var lista: LinearLayout
    private lateinit var etBuscar: EditText

    // Selector de fotos del sistema: no necesita pedir permisos de almacenamiento
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = consorcioFotoId ?: return@registerForActivityResult
        consorcioFotoId = null
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                val ruta = ConsorcioImagen.guardar(requireContext(), uri, id)
                ConsorcioRepository(requireContext()).actualizarImagen(id, ruta)
            }.isSuccess
            Toast.makeText(
                requireContext(),
                if (ok) R.string.consorcio_foto_actualizada else R.string.consorcio_foto_error,
                Toast.LENGTH_SHORT,
            ).show()
            cargar()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        (requireActivity() as MainActivity).usuario.let { u ->
            when {
                u?.esAsistente == true -> inflateSection(inflater, container, getString(R.string.nav_edificios), getString(R.string.consorcios_subtitulo_asistente))
                u?.esPropietario == true -> inflateSection(inflater, container, getString(R.string.nav_mi_edificio), getString(R.string.consorcios_subtitulo_propietario))
                else -> inflateSection(inflater, container, getString(R.string.nav_consorcios), getString(R.string.consorcios_subtitulo))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        val buscador = layoutInflater.inflate(R.layout.view_buscador, content, false)
        content.addView(buscador)
        // Acceso a las votaciones, reuniones, contratos e historial de todos los consorcios
        listOf(
            listOf(
                R.string.votaciones_boton to { VotacionesFragment.newInstance() as Fragment },
                R.string.reuniones_ver_todas to { ReunionesFragment.newInstance() as Fragment },
            ),
            listOf(
                R.string.contratos_boton to { ContratosFragment.newInstance() as Fragment },
                R.string.historial_boton to { HistorialFragment.newInstance() as Fragment },
            ),
            listOf(
                R.string.trabajos_boton to { TrabajosFragment() as Fragment },
                R.string.facturas_procesadas_boton to { FacturasProcesadasFragment() as Fragment },
            ),
        ).forEach { fila -> content.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            fila.forEachIndexed { i, (texto, pantalla) ->
                addView(com.google.android.material.button.MaterialButton(
                    requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
                ).apply {
                    setText(texto)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { if (i == 0) marginEnd = (8 * resources.displayMetrics.density).toInt() }
                    setOnClickListener { (requireActivity() as MainActivity).showOverlay(pantalla()) }
                })
            }
        }) }
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        etBuscar = buscador.findViewById(R.id.etBuscar)
        etBuscar.setHint(R.string.consorcios_buscar)
        etBuscar.doAfterTextChanged { mostrar(it?.toString().orEmpty()) }
        // En Consorcios no hay filtros, solo la búsqueda
        buscador.findViewById<View>(R.id.btnFiltros).visibility = View.GONE

        // Cargan facturas el administrador y el asistente; el propietario solo mira
        if ((requireActivity() as MainActivity).usuario?.esPropietario != true) view.showFab(R.string.factura_titulo) {
            startActivity(Intent(requireContext(), FacturaFormActivity::class.java))
        }
    }

    // Se recarga en onResume para mostrar las facturas recién cargadas
    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val usuario = (requireActivity() as MainActivity).usuario ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            consorcios = ConsorcioRepository(requireContext()).listarVisibles(usuario)
            mostrar(etBuscar.text.toString())
        }
    }

    private fun mostrar(busqueda: String) {
        val main = requireActivity() as MainActivity
        val q = busqueda.trim()
        val filtrados = consorcios.filter {
            q.isEmpty() || it.nombre.contains(q, ignoreCase = true) || it.direccion.contains(q, ignoreCase = true)
        }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_consorcios,
                getString(R.string.consorcios_vacio_titulo),
                getString(if (q.isEmpty()) R.string.consorcios_vacio_mensaje else R.string.consorcios_sin_resultados),
            )
            return
        }
        for (c in filtrados) {
            val item = itemConsorcio(lista, c)
            item.setOnClickListener { main.showOverlay(FacturasFragment.newInstance(c.id, c.nombre)) }
            lista.addView(item)
        }
    }

    private fun itemConsorcio(parent: ViewGroup, c: Consorcio): View {
        val item = layoutInflater.inflate(R.layout.item_consorcio, parent, false)
        item.findViewById<TextView>(R.id.tvNombre).text = c.nombre
        item.findViewById<TextView>(R.id.tvDireccion).text = c.direccion
        item.findViewById<TextView>(R.id.tvDescripcion).apply {
            text = c.descripcion
            visibility = if (c.descripcion.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        // Actividades del consorcio: Facturación (también tocando la tarjeta) y Votaciones
        val main = requireActivity() as MainActivity
        item.findViewById<TextView>(R.id.tvFacturas).apply {
            text = resources.getQuantityString(R.plurals.facturas_cantidad, c.cantidadFacturas, c.cantidadFacturas)
            setOnClickListener { main.showOverlay(FacturasFragment.newInstance(c.id, c.nombre)) }
        }
        item.findViewById<TextView>(R.id.tvVotaciones).setOnClickListener {
            main.showOverlay(VotacionesFragment.newInstance(c.id, c.nombre))
        }
        item.findViewById<TextView>(R.id.tvReuniones).setOnClickListener {
            main.showOverlay(ReunionesFragment.newInstance(c.id, c.nombre))
        }
        item.findViewById<TextView>(R.id.tvContratos).setOnClickListener {
            main.showOverlay(ContratosFragment.newInstance(c.id, c.nombre))
        }
        item.findViewById<TextView>(R.id.tvHistorial).setOnClickListener {
            main.showOverlay(HistorialFragment.newInstance(c.id, c.nombre))
        }

        // Imagen: degradado + ilustración, y encima la foto si el usuario eligió una
        item.findViewById<View>(R.id.imgContainer).background = ConsorcioImagen.fondo(c.id)
        val ivFoto = item.findViewById<ImageView>(R.id.ivFoto)
        val ivIlustracion = item.findViewById<View>(R.id.ivIlustracion)
        c.img?.let { ruta ->
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = ConsorcioImagen.cargar(requireContext(), ruta, resources.displayMetrics.widthPixels) ?: return@launch
                ivFoto.setImageBitmap(bitmap)
                ivFoto.visibility = View.VISIBLE
                ivIlustracion.visibility = View.GONE
            }
        }
        // Solo el administrador cambia la foto del consorcio
        item.findViewById<View>(R.id.btnFoto).visibility = if (main.usuario?.esAdmin == true) View.VISIBLE else View.GONE
        item.findViewById<View>(R.id.btnFoto).setOnClickListener {
            consorcioFotoId = c.id
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        return item
    }
}
