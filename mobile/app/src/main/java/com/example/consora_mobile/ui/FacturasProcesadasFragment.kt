package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Archivos
import com.example.consora_mobile.data.FacturaProcesada
import com.example.consora_mobile.data.FacturaRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** Todas las facturas cargadas de los consorcios del usuario, con búsqueda, filtro por edificio y gasto por tipo. */
class FacturasProcesadasFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val dp get() = resources.displayMetrics.density

    private var facturas: List<FacturaProcesada> = emptyList()
    private var consorcio: Long? = null
    private var busqueda = ""
    private lateinit var chips: ChipGroup
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.facturas_procesadas_titulo), getString(R.string.facturas_procesadas_subtitulo))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        val buscador = layoutInflater.inflate(R.layout.view_buscador, content, false)
        buscador.findViewById<View>(R.id.btnFiltros).visibility = View.GONE
        buscador.findViewById<EditText>(R.id.etBuscar).apply {
            setHint(R.string.facturas_buscar)
            doAfterTextChanged { busqueda = it?.toString()?.trim().orEmpty(); mostrar() }
        }
        content.addView(buscador)
        chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true }
        content.addView(HorizontalScrollView(requireContext()).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)
    }

    override fun onResume() {
        super.onResume()
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            facturas = FacturaRepository(requireContext()).procesadas(usuario)
            // Un chip por edificio (si hay más de uno)
            chips.removeAllViews()
            val edificios = facturas.map { it.consorcioId to it.consorcio }.distinct()
            if (edificios.size > 1) (listOf<Pair<Long?, String>>(null to getString(R.string.facturas_todos)) + edificios).forEach { (id, nombre) ->
                chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                    text = nombre
                    isCheckable = true
                    isChecked = id == consorcio
                    setOnClickListener { consorcio = id; mostrar() }
                })
            }
            mostrar()
        }
    }

    private fun mostrar() {
        val q = busqueda.lowercase()
        val filtradas = facturas.filter {
            (consorcio == null || it.consorcioId == consorcio) &&
                (q.isEmpty() || it.proveedor.lowercase().contains(q) || it.nroComprobante.lowercase().contains(q) || it.deuda.lowercase().contains(q))
        }
        lista.removeAllViews()
        if (filtradas.isEmpty()) {
            lista.addEmptyState(R.drawable.ic_receipt, getString(R.string.facturas_vacio), "")
            return
        }
        val total = filtradas.sumOf { it.importe }
        val t = Tarjetas(requireContext(), lista)

        // Resumen: cantidad, total y gasto por tipo
        t.tarjeta(R.string.facturas_gasto_tipo).apply {
            addView(t.texto(getString(R.string.facturas_resumen, filtradas.size, Formatos.moneda(total)), R.color.consora_navy, bold = true, sp = 15f))
            filtradas.groupBy { it.tipo }.mapValues { (_, l) -> l.sumOf { it.importe } to l.size }
                .entries.sortedByDescending { it.value.first }.forEach { (tipo, datos) ->
                    addView(t.fila("$tipo (${datos.second})", Formatos.moneda(datos.first)))
                    addView(LinearProgressIndicator(requireContext()).apply {
                        max = 100
                        progress = (datos.first * 100 / total).toInt()
                        trackCornerRadius = (4 * dp).toInt()
                        setIndicatorColor(ContextCompat.getColor(context, R.color.consora_teal))
                    })
                }
        }

        // Facturas: tocando se abre el archivo adjunto, o el contrato si era una cuota
        filtradas.forEach { f ->
            lista.addView(layoutInflater.inflate(R.layout.item_factura, lista, false).apply {
                findViewById<TextView>(R.id.tvProveedor).text = f.proveedor
                findViewById<TextView>(R.id.tvTipo).text = "${f.tipo} · ${f.deuda}"
                findViewById<TextView>(R.id.tvDetalle).text = listOfNotNull(
                    "Nº ${f.nroComprobante}", Formatos.fecha(f.fecha), f.consorcio.takeIf { consorcio == null },
                    getString(R.string.facturas_cargada_por, f.cargadaPor),
                ).joinToString(" · ")
                findViewById<TextView>(R.id.tvImporte).text = Formatos.moneda(f.importe)
                findViewById<View>(R.id.tvAdjunto).visibility = if (f.archivo != null) View.VISIBLE else View.GONE
                when {
                    f.archivo != null -> setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), f.archivo) } }
                    f.contratoId != null -> setOnClickListener { main.showOverlay(ContratoDetalleFragment.newInstance(f.contratoId)) }
                }
            })
        }
    }
}
