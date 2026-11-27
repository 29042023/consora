package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Contrato
import com.example.consora_mobile.data.ContratoRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Trabajos contratados a proveedores, en proceso o terminados (aparte de cómo se van pagando).
 * El proveedor ve los suyos. Tocando el proveedor se abre su perfil del Marketplace.
 */
class TrabajosFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { ContratoRepository(requireContext()) }

    private var trabajos: List<Contrato> = emptyList()
    private var terminados = false
    private lateinit var chips: ChipGroup
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container,
            getString(if (main.usuario?.esProveedor == true) R.string.trabajos_mis else R.string.trabajos_titulo),
            getString(R.string.trabajos_subtitulo),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true }
        content.addView(chips)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)
    }

    override fun onResume() {
        super.onResume()
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            trabajos = repo.listar(usuario).filter { it.estado != Contrato.CANCELADO }
            mostrar()
        }
    }

    private fun mostrar() {
        // Pestañas con la cantidad de cada una
        chips.removeAllViews()
        listOf(false to R.string.trabajos_en_proceso, true to R.string.trabajos_terminados).forEach { (valor, texto) ->
            chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                text = getString(texto, trabajos.count { it.trabajoTerminado == valor })
                isCheckable = true
                isChecked = valor == terminados
                setOnClickListener { terminados = valor; mostrar() }
            })
        }
        val filtrados = trabajos.filter { it.trabajoTerminado == terminados }
            .sortedWith(if (terminados) compareByDescending { it.trabajoTerminadoEn } else compareBy { it.fecha })
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_consorcios,
                getString(if (terminados) R.string.trabajos_vacio_terminados else R.string.trabajos_vacio_proceso),
                getString(R.string.trabajos_vacio_mensaje),
            )
            return
        }
        filtrados.forEach { lista.addView(item(it)) }
    }

    private fun item(c: Contrato): View = layoutInflater.inflate(R.layout.item_trabajo, lista, false).apply {
        val usuario = main.usuario!!
        val esProveedor = usuario.esProveedor
        // El proveedor ve la foto del edificio; el resto, la del proveedor
        if (esProveedor) mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), c.consorcio, c.consorcioImg)
        else mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), c.proveedor, c.proveedorImg)
        findViewById<TextView>(R.id.tvBadge).text = if (c.trabajoTerminado) "✅" else "🛠"
        findViewById<TextView>(R.id.tvTrabajo).text = c.descripcion

        val abrirPerfil = View.OnClickListener {
            main.showOverlay(ProveedorPerfilFragment.newInstance(c.proveedorId, c.proveedor, c.proveedorImg))
        }
        findViewById<TextView>(R.id.tvProveedor).apply {
            text = if (esProveedor) c.consorcio else "${c.proveedor}  ›"
            if (esProveedor) setTextColor(ContextCompat.getColor(context, R.color.consora_navy)) else setOnClickListener(abrirPerfil)
        }
        if (!esProveedor) findViewById<View>(R.id.avatar).setOnClickListener(abrirPerfil)

        val hoy = LocalDate.now()
        val inicio = LocalDate.parse(c.fecha)
        findViewById<TextView>(R.id.tvMeta).text = listOfNotNull(
            c.consorcio.takeIf { !esProveedor },
            c.trabajoTerminadoEn?.let { getString(R.string.trabajos_termino, Formatos.fecha(it), ChronoUnit.DAYS.between(inicio, LocalDate.parse(it)).coerceAtLeast(0)) }
                ?: getString(R.string.trabajos_desde, Formatos.fecha(c.fecha), ChronoUnit.DAYS.between(inicio, hoy).coerceAtLeast(0)),
        ).joinToString(" · ")
        findViewById<LinearProgressIndicator>(R.id.progreso).apply {
            max = 100
            progress = c.porcentaje
            setIndicatorColor(ContextCompat.getColor(context, if (c.estado == Contrato.FINALIZADO) R.color.consora_teal else R.color.consora_navy))
        }
        findViewById<TextView>(R.id.tvPagos).text = if (c.estado == Contrato.FINALIZADO) getString(R.string.contrato_pagado_completo) + " · " + Formatos.moneda(c.precio)
            else getString(R.string.contrato_avance, c.cantPagas, c.cantCuotas, Formatos.moneda(c.pagado), Formatos.moneda(c.precio))

        // Lo marcan terminado el administrador del consorcio o el mismo proveedor
        findViewById<MaterialButton>(R.id.btnTerminar).apply {
            visibility = if (!c.trabajoTerminado && (c.representanteId == usuario.id || c.proveedorId == usuario.id)) View.VISIBLE else View.GONE
            setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.contrato_marcar_terminado)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            repo.terminarTrabajo(c, hoy, usuario.id)
                            android.widget.Toast.makeText(requireContext(), R.string.trabajos_terminado_ok, android.widget.Toast.LENGTH_SHORT).show()
                            onResume()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        setOnClickListener { main.showOverlay(ContratoDetalleFragment.newInstance(c.id)) }
    }
}
