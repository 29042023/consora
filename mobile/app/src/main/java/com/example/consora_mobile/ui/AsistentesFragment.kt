package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Asistente
import com.example.consora_mobile.data.AsistenteRepository
import kotlinx.coroutines.launch

/** Pestaña Asistentes: buscador y lista del equipo de trabajo del administrador. */
class AsistentesFragment : Fragment() {

    private var asistentes: List<Asistente> = emptyList()
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.nav_asistentes), getString(R.string.asistentes_subtitulo))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        val buscador = layoutInflater.inflate(R.layout.view_buscador, content, false)
        content.addView(buscador)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        val etBuscar = buscador.findViewById<EditText>(R.id.etBuscar)
        etBuscar.setHint(R.string.asistentes_buscar)
        etBuscar.doAfterTextChanged { mostrar(it?.toString().orEmpty()) }
        // TODO: filtros
        buscador.findViewById<View>(R.id.btnFiltros).setOnClickListener { }
        // TODO: formulario para crear la cuenta del asistente
        view.showFab(R.string.asistentes_crear_cuenta) { }

        val usuario = (requireActivity() as MainActivity).usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            asistentes = AsistenteRepository(requireContext()).listar(usuario.id)
            mostrar(etBuscar.text.toString())
        }
    }

    private fun mostrar(busqueda: String) {
        val q = busqueda.trim()
        val filtrados = asistentes.filter {
            q.isEmpty() || it.nombre.contains(q, ignoreCase = true) || it.mail.contains(q, ignoreCase = true)
        }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_asistentes,
                getString(R.string.asistentes_vacio_titulo),
                getString(if (q.isEmpty()) R.string.asistentes_vacio_mensaje else R.string.asistentes_sin_resultados),
            )
            return
        }
        filtrados.forEach { lista.addView(itemAsistente(lista, it)) }
    }

    // Misma fila que en el Marketplace, sin la calificación
    private fun itemAsistente(parent: ViewGroup, a: Asistente): View =
        layoutInflater.inflate(R.layout.item_proveedor, parent, false).apply {
            mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), a.nombre, a.img)
            findViewById<TextView>(R.id.tvNombre).text = a.nombre
            setOnClickListener {
                (requireActivity() as MainActivity).showOverlay(AsistentePerfilFragment.newInstance(a.id, a.nombre, a.mail, a.img))
            }
            findViewById<TextView>(R.id.tvServicio).text = a.mail
            findViewById<View>(R.id.ivEstrella).visibility = View.GONE
            findViewById<View>(R.id.tvEstrellas).visibility = View.GONE
        }
}
