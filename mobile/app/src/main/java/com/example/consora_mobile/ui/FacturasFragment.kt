package com.example.consora_mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.FacturaFormActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.FacturaRepository
import kotlinx.coroutines.launch

/** Facturas cargadas para un consorcio. Se abre tocando un consorcio. */
class FacturasFragment : Fragment() {

    private val consorcioId get() = requireArguments().getLong(ARG_ID)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container,
            getString(R.string.facturas_titulo), requireArguments().getString(ARG_NOMBRE).orEmpty(),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if ((requireActivity() as com.example.consora_mobile.MainActivity).usuario?.esPropietario != true) view.showFab(R.string.factura_titulo) {
            startActivity(
                Intent(requireContext(), FacturaFormActivity::class.java)
                    .putExtra(FacturaFormActivity.EXTRA_CONSORCIO_ID, consorcioId)
            )
        }
    }

    // Se recarga en onResume para mostrar la factura recién cargada
    override fun onResume() {
        super.onResume()
        val view = requireView()
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)

        viewLifecycleOwner.lifecycleScope.launch {
            val facturas = FacturaRepository(requireContext()).listByConsorcio(consorcioId)
            content.removeAllViews()
            if (facturas.isEmpty()) {
                content.addEmptyState(
                    R.drawable.ic_receipt,
                    getString(R.string.facturas_vacio_titulo),
                    getString(R.string.facturas_vacio_mensaje),
                )
                return@launch
            }

            view.findViewById<TextView>(R.id.tvSectionSubtitle).text = getString(
                R.string.facturas_total, Formatos.moneda(facturas.sumOf { it.importe }),
            ).let { "${requireArguments().getString(ARG_NOMBRE)} · $it" }

            for (f in facturas) {
                val item = layoutInflater.inflate(R.layout.item_factura, content, false)
                item.findViewById<TextView>(R.id.tvProveedor).text = f.proveedor
                item.findViewById<TextView>(R.id.tvTipo).text = "${f.tipoDeuda} · ${f.deuda}"
                item.findViewById<TextView>(R.id.tvDetalle).text = "Nº ${f.nroComprobante} · ${Formatos.fecha(f.fecha)}"
                item.findViewById<TextView>(R.id.tvImporte).text = Formatos.moneda(f.importe)
                item.findViewById<View>(R.id.tvAdjunto).visibility = if (f.archivo != null) View.VISIBLE else View.GONE
                content.addView(item)
            }
        }
    }

    companion object {
        private const val ARG_ID = "consorcio_id"
        private const val ARG_NOMBRE = "consorcio_nombre"

        fun newInstance(consorcioId: Long, nombre: String) = FacturasFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, consorcioId)
                putString(ARG_NOMBRE, nombre)
            }
        }
    }
}
