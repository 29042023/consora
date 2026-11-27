package com.example.consora_mobile.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Proveedor
import com.example.consora_mobile.data.ProveedorRepository
import kotlinx.coroutines.launch
import java.util.Locale

/** Pestaña Marketplace: buscador de proveedores por nombre o tipo de servicio. */
class MarketplaceFragment : Fragment() {

    private var proveedores: List<Proveedor> = emptyList()
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.nav_marketplace), getString(R.string.marketplace_subtitulo))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        val buscador = layoutInflater.inflate(R.layout.view_buscador, content, false)
        content.addView(buscador)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        val etBuscar = buscador.findViewById<EditText>(R.id.etBuscar)
        etBuscar.setHint(R.string.marketplace_buscar)
        etBuscar.doAfterTextChanged { mostrar(it?.toString().orEmpty()) }
        // TODO: filtros
        buscador.findViewById<View>(R.id.btnFiltros).setOnClickListener { }

        viewLifecycleOwner.lifecycleScope.launch {
            proveedores = ProveedorRepository(requireContext()).listar()
            mostrar(etBuscar.text.toString())
        }
    }

    private fun mostrar(busqueda: String) {
        val q = busqueda.trim()
        val filtrados = proveedores.filter {
            q.isEmpty() || it.nombre.contains(q, ignoreCase = true) || it.servicio?.contains(q, ignoreCase = true) == true
        }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_marketplace,
                getString(R.string.marketplace_vacio_titulo),
                getString(if (q.isEmpty()) R.string.marketplace_vacio_mensaje else R.string.marketplace_sin_resultados),
            )
            return
        }
        filtrados.forEach { lista.addView(itemProveedor(lista, it)) }
    }

    private fun itemProveedor(parent: ViewGroup, p: Proveedor): View =
        layoutInflater.inflate(R.layout.item_proveedor, parent, false).apply {
            mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), p.nombre, p.img)
            findViewById<TextView>(R.id.tvNombre).text = p.nombre
            setOnClickListener {
                (requireActivity() as MainActivity).showOverlay(ProveedorPerfilFragment.newInstance(p.id, p.nombre, p.img))
            }
            findViewById<TextView>(R.id.tvServicio).text = p.servicio ?: getString(R.string.marketplace_servicio_por_completar)
            // Sin calificaciones: estrella apagada y sin número
            findViewById<ImageView>(R.id.ivEstrella).imageTintList = ColorStateList.valueOf(
                requireContext().getColor(if (p.estrellas == null) R.color.consora_estrella_apagada else R.color.consora_estrella)
            )
            findViewById<TextView>(R.id.tvEstrellas).apply {
                text = p.estrellas?.let { String.format(Locale("es", "AR"), "%.1f", it) }
                visibility = if (p.estrellas == null) View.GONE else View.VISIBLE
            }
        }
}
