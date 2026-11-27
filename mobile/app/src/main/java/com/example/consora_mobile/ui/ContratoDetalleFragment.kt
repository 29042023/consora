package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Contrato
import com.example.consora_mobile.data.ContratoRepository
import com.example.consora_mobile.data.Cuota
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Detalle de un contrato: avance de pagos, cuotas (pagas y pendientes) y acciones del administrador. */
class ContratoDetalleFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { ContratoRepository(requireContext()) }
    private val contratoId get() = requireArguments().getLong(ARG_ID)
    private val dp get() = resources.displayMetrics.density

    private lateinit var content: LinearLayout
    private lateinit var t: Tarjetas
    private val selectorPdf = SelectorPdf(this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.contrato_titulo), "")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        content = view.findViewById(R.id.sectionContent)
        t = Tarjetas(requireContext(), content)
    }

    // Se recarga en onResume para mostrar las cuotas recién pagadas desde Facturación
    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val c = repo.detalle(contratoId) ?: return@launch
            requireView().findViewById<TextView>(R.id.tvSectionSubtitle).text = c.consorcio
            mostrar(c, repo.cuotas(contratoId))
        }
    }

    private fun mostrar(c: Contrato, cuotas: List<Cuota>) {
        val usuario = main.usuario ?: return
        val gestiona = c.representanteId == usuario.id
        val hoy = LocalDate.now()
        content.removeAllViews()

        // Cabecera con el avance
        t.tarjeta().apply {
            val (estado, color) = ContratosFragment.estadoTexto(c.estado)
            addView(t.texto(ContratosFragment.formaPagoTexto(requireContext(), c.cuotas) + "  ·  " + getString(estado), color, bold = true, sp = 13f))
            addView(t.margen(t.texto(c.descripcion, R.color.consora_navy, bold = true, sp = 17f), 6))
            // Tocando el proveedor se abre su perfil del Marketplace
            addView(t.margen(t.texto("👷  ${c.proveedor}", if (usuario.esProveedor) R.color.consora_gray else R.color.consora_teal,
                bold = !usuario.esProveedor).apply {
                if (!usuario.esProveedor) setOnClickListener {
                    main.showOverlay(ProveedorPerfilFragment.newInstance(c.proveedorId, c.proveedor, c.proveedorImg))
                }
            }, 8))
            addView(t.texto("🏢  ${c.consorcio}", R.color.consora_gray))
            addView(t.margen(t.texto(getString(R.string.contrato_pagos_hechos, c.cantPagas, c.cantCuotas, c.porcentaje), R.color.consora_navy, bold = true, sp = 13f), 10))
            addView(LinearProgressIndicator(requireContext()).apply {
                max = 100
                progress = c.porcentaje
                trackCornerRadius = (4 * dp).toInt()
                setIndicatorColor(ContextCompat.getColor(context, R.color.consora_teal))
            })
        }

        // Resumen
        t.tarjeta(R.string.contrato_resumen).apply {
            addView(t.fila(getString(R.string.contrato_precio_total), Formatos.moneda(c.precio)))
            addView(t.fila(getString(R.string.contrato_pagado), Formatos.moneda(c.pagado), R.color.consora_teal))
            addView(t.fila(getString(R.string.contrato_saldo), Formatos.moneda(c.saldo)))
            if (c.estado == Contrato.VIGENTE && c.proximaVence != null) addView(t.fila(
                getString(R.string.contrato_proximo_vencimiento), Formatos.fecha(c.proximaVence),
                if (LocalDate.parse(c.proximaVence) < hoy) R.color.consora_error else R.color.consora_navy,
            ))
        }

        // Contrato formal en PDF: lo ven todos, lo carga el administrador
        t.tarjeta(R.string.contrato_formal).apply {
            val pdf = c.contratoPdf
            if (pdf != null) addView(t.texto("📕  " + com.example.consora_mobile.data.Archivos.nombreVisible(pdf), R.color.consora_teal, bold = true).apply {
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { com.example.consora_mobile.data.Archivos.abrir(requireContext(), pdf) } }
            }) else addView(t.texto(getString(if (gestiona) R.string.contrato_formal_vacio_admin else R.string.contrato_formal_vacio), R.color.consora_gray))
            if (gestiona) addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(t.boton(if (pdf == null) R.string.contrato_formal_subir else R.string.contrato_formal_reemplazar, outlined = true, peso = true) {
                    selectorPdf.elegir { uri ->
                        com.example.consora_mobile.data.Archivos.validar(requireContext(), uri, soloPdf = true)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show(); return@elegir
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            val guardado = com.example.consora_mobile.data.Archivos.guardar(requireContext(), uri,
                                com.example.consora_mobile.data.Carpeta.DOCS_CONTRATOS, "contrato_${c.id}")
                            repo.guardarPdf(c, guardado, usuario.id)
                            Toast.makeText(requireContext(), R.string.contrato_formal_cargado, Toast.LENGTH_SHORT).show()
                            onResume()
                        }
                    }
                })
                if (pdf != null) addView(t.boton(R.string.contrato_formal_quitar, outlined = true, peso = true) {
                    confirmar(getString(R.string.contrato_formal_quitar_confirmar)) {
                        viewLifecycleOwner.lifecycleScope.launch { repo.quitarPdf(c); onResume() }
                    }
                })
            })
        }

        // Trabajo: en proceso o terminado, aparte de los pagos
        if (c.estado != Contrato.CANCELADO) t.tarjeta(R.string.contrato_trabajo_titulo).apply {
            val terminado = c.trabajoTerminadoEn
            addView(t.texto(
                if (terminado != null) getString(R.string.contrato_trabajo_terminado, Formatos.fecha(terminado))
                else getString(R.string.contrato_trabajo_en_proceso, Formatos.fecha(c.fecha)),
                if (terminado != null) R.color.consora_teal else R.color.consora_pendiente, bold = true,
            ))
            if (terminado == null && (gestiona || c.proveedorId == usuario.id)) addView(t.boton(R.string.contrato_marcar_terminado) {
                marcarTerminado(c, usuario.id)
            })
            if (terminado != null && gestiona) addView(t.boton(R.string.contrato_reabrir, outlined = true) {
                viewLifecycleOwner.lifecycleScope.launch { repo.reabrirTrabajo(c); onResume() }
            })
        }

        // Cuotas
        t.tarjeta(if (c.cantCuotas == 1) R.string.contrato_pago else R.string.contrato_cuotas).apply {
            if (cuotas.isEmpty()) addView(t.texto(getString(R.string.contrato_cancelado_sin_pagos), R.color.consora_gray))
            cuotas.forEach { q -> addView(filaCuota(q, c.cantCuotas == 1, hoy)) }
            // Las cuotas se pagan en Facturación, cargándoles la factura
            if (c.estado == Contrato.VIGENTE && !usuario.esPropietario && !usuario.esProveedor) addView(t.boton(R.string.contrato_pagar, outlined = true) {
                main.showOverlay(FacturasFragment.newInstance(c.consorcioId, c.consorcio))
            })
        }

        // Votación de origen
        c.votacionId?.let { v ->
            t.tarjeta().apply {
                addView(t.texto("🗳  " + getString(R.string.contrato_de_votacion), R.color.consora_teal, bold = true).apply {
                    setOnClickListener { main.showOverlay(VotacionDetalleFragment.newInstance(v)) }
                })
            }
        }

        // Acciones del administrador
        if (gestiona) LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            if (c.estado == Contrato.VIGENTE) addView(t.boton(R.string.contrato_cancelar, outlined = true, peso = true) {
                confirmar(getString(R.string.contrato_cancelar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val quitadas = repo.cancelar(c, usuario.id)
                        Toast.makeText(requireContext(),
                            resources.getQuantityString(R.plurals.contrato_cancelado, quitadas, quitadas), Toast.LENGTH_LONG).show()
                        onResume()
                    }
                }
            })
            if (c.cantPagas == 0) addView(t.boton(R.string.contrato_eliminar, outlined = true, peso = true) {
                confirmar(getString(R.string.contrato_eliminar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repo.eliminar(c)
                        parentFragmentManager.popBackStack()
                    }
                }
            })
            content.addView(this)
        }
    }

    /** Elige la fecha (hasta hoy) y marca el trabajo terminado. */
    private fun marcarTerminado(c: Contrato, usuarioId: Long) {
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.contrato_marcar_terminado)
            .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(com.google.android.material.datepicker.CalendarConstraints.Builder()
                .setValidator(com.google.android.material.datepicker.DateValidatorPointBackward.now()).build())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val fecha = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            viewLifecycleOwner.lifecycleScope.launch {
                repo.terminarTrabajo(c, fecha, usuarioId)
                Toast.makeText(requireContext(), R.string.trabajos_terminado_ok, Toast.LENGTH_SHORT).show()
                onResume()
            }
        }
        picker.show(childFragmentManager, "terminado")
    }

    private fun filaCuota(q: Cuota, unica: Boolean, hoy: LocalDate) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(t.texto((if (q.pagada) "✅  " else "⏳  ") +
                if (unica) getString(R.string.contrato_pago_unico) else getString(R.string.contrato_cuota_n, q.nro),
                R.color.consora_navy, bold = true))
            val vencida = !q.pagada && LocalDate.parse(q.vencimiento) < hoy
            addView(t.texto(
                if (q.pagada) getString(R.string.contrato_pagada_el, Formatos.fecha(q.pagadaEl!!), q.comprobante.orEmpty())
                else getString(if (vencida) R.string.contrato_vencida else R.string.contrato_vence, Formatos.fecha(q.vencimiento)),
                when { q.pagada -> R.color.consora_teal; vencida -> R.color.consora_error; else -> R.color.consora_gray },
                sp = 12f,
            ))
        })
        addView(t.texto(Formatos.moneda(q.importe), R.color.consora_navy, bold = true))
    }

    private fun confirmar(mensaje: String, accion: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(mensaje)
            .setPositiveButton(R.string.ok) { _, _ -> accion() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_ID = "contrato_id"

        fun newInstance(contratoId: Long) = ContratoDetalleFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, contratoId) }
        }
    }
}
