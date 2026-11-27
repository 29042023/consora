package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.Contrato
import com.example.consora_mobile.data.ContratoRepository
import com.example.consora_mobile.data.FormaPago
import com.example.consora_mobile.data.ProveedorInfo
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Contratos con proveedores (todos, o los de un consorcio si se abre desde su tarjeta).
 * El proveedor ve los suyos. Solo el administrador los crea; cada cuota queda como deuda en Facturación.
 */
class ContratosFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { ContratoRepository(requireContext()) }
    private val consorcioId get() = arguments?.getLong(ARG_ID, -1L)?.takeIf { it > 0 }

    private val selectorPdf = SelectorPdf(this)
    private var contratos: List<Contrato> = emptyList()
    private var estado: String? = null
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container,
            getString(if (main.usuario?.esProveedor == true) R.string.contratos_mis else R.string.contratos_titulo),
            arguments?.getString(ARG_NOMBRE) ?: getString(
                if (main.usuario?.esProveedor == true) R.string.contratos_subtitulo_proveedor else R.string.contratos_subtitulo),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)

        // Filtros por estado
        val chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true }
        listOf(null to R.string.contratos_todos, Contrato.VIGENTE to R.string.contratos_vigentes,
            Contrato.FINALIZADO to R.string.contratos_finalizados, Contrato.CANCELADO to R.string.contratos_cancelados,
        ).forEach { (valor, texto) ->
            chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                setText(texto)
                isCheckable = true
                isChecked = valor == estado
                setOnClickListener { estado = valor; mostrar() }
            })
        }
        content.addView(android.widget.HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        if (main.usuario?.esAdmin == true) view.showFab(R.string.contrato_nuevo) {
            nuevoContrato(this, consorcioId, selectorPdf = selectorPdf) { main.showOverlay(ContratoDetalleFragment.newInstance(it)) }
        }
    }

    override fun onResume() {
        super.onResume()
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            contratos = repo.listar(usuario, consorcioId)
            mostrar()
        }
    }

    private fun mostrar() {
        val filtrados = contratos.filter { estado == null || it.estado == estado }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_receipt,
                getString(R.string.contratos_vacio_titulo),
                getString(when {
                    main.usuario?.esAdmin == true -> R.string.contratos_vacio_admin
                    main.usuario?.esProveedor == true -> R.string.contratos_vacio_proveedor
                    else -> R.string.contratos_vacio_mensaje
                }),
            )
            return
        }
        filtrados.forEach { lista.addView(item(it)) }
    }

    private fun item(c: Contrato): View = layoutInflater.inflate(R.layout.item_votacion, lista, false).apply {
        findViewById<TextView>(R.id.tvTipo).text = formaPagoTexto(requireContext(), c.cuotas)
        findViewById<TextView>(R.id.tvEstado).apply {
            val (texto, color) = estadoTexto(c.estado)
            setText(texto)
            setTextColor(ContextCompat.getColor(context, color))
        }
        findViewById<TextView>(R.id.tvDetalle).text = c.descripcion
        findViewById<TextView>(R.id.tvMeta).text = if (main.usuario?.esProveedor == true) c.consorcio
            else if (consorcioId != null) c.proveedor else "${c.proveedor} · ${c.consorcio}"
        findViewById<TextView>(R.id.tvVotos).apply {
            text = getString(R.string.contrato_avance, c.cantPagas, c.cantCuotas, Formatos.moneda(c.pagado), Formatos.moneda(c.precio)) +
                when {
                    c.estado == Contrato.VIGENTE && c.proximaVence != null ->
                        "\n" + getString(R.string.contrato_proximo, Formatos.moneda(c.proximaImporte ?: 0.0), Formatos.fecha(c.proximaVence))
                    c.estado == Contrato.FINALIZADO -> "\n" + getString(R.string.contrato_pagado_completo)
                    else -> ""
                }
            if (c.estado == Contrato.VIGENTE && c.proximaVence != null && LocalDate.parse(c.proximaVence) < LocalDate.now())
                setTextColor(ContextCompat.getColor(context, R.color.consora_error))
        }
        setOnClickListener { main.showOverlay(ContratoDetalleFragment.newInstance(c.id)) }
    }

    companion object {
        private const val ARG_ID = "consorcio_id"
        private const val ARG_NOMBRE = "consorcio_nombre"

        fun formaPagoTexto(context: android.content.Context, cuotas: Int) =
            if (cuotas == 1) context.getString(R.string.contrato_contado) else context.getString(R.string.contrato_n_cuotas, cuotas)

        fun estadoTexto(estado: String): Pair<Int, Int> = when (estado) {
            Contrato.FINALIZADO -> R.string.contrato_estado_finalizado to R.color.consora_teal
            Contrato.CANCELADO -> R.string.contrato_estado_cancelado to R.color.consora_gray
            else -> R.string.contrato_estado_vigente to R.color.consora_navy
        }

        /**
         * Formulario de nuevo contrato. Con [propuestaId] viene de una votación cerrada: el consorcio y el
         * proveedor ya están definidos. Al crear llama a [creado] con el id del contrato.
         */
        fun nuevoContrato(
            f: Fragment, consorcioId: Long?, propuestaId: Long? = null, proveedorId: Long? = null,
            descripcionSugerida: String? = null, selectorPdf: SelectorPdf? = null, creado: (Long) -> Unit,
        ) {
            val main = f.requireActivity() as MainActivity
            val usuario = main.usuario ?: return
            f.viewLifecycleOwner.lifecycleScope.launch {
                val consorcios = ConsorcioRepository(f.requireContext()).listByRepresentante(usuario.id)
                if (consorcios.isEmpty()) {
                    Toast.makeText(f.requireContext(), R.string.votacion_sin_consorcios, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val proveedores = ContratoRepository(f.requireContext()).proveedoresConInfo()
                val formas = ContratoRepository(f.requireContext()).formasPago()
                formulario(f, usuario.id, consorcios, proveedores, formas, consorcioId, propuestaId, proveedorId, descripcionSugerida, selectorPdf, creado)
            }
        }

        private fun formulario(
            f: Fragment, adminId: Long, consorcios: List<Consorcio>, proveedores: List<ProveedorInfo>, formas: List<FormaPago>,
            consorcioId: Long?, propuestaId: Long?, proveedorId: Long?, descripcionSugerida: String?, selectorPdf: SelectorPdf?,
            creado: (Long) -> Unit,
        ) {
            val ctx = f.requireContext()
            val view = f.layoutInflater.inflate(R.layout.dialog_contrato, null)
            val tvGanador = view.findViewById<TextView>(R.id.tvGanador)
            val tilConsorcio = view.findViewById<TextInputLayout>(R.id.tilConsorcio)
            val acConsorcio = view.findViewById<MaterialAutoCompleteTextView>(R.id.acConsorcio)
            val tilProveedor = view.findViewById<TextInputLayout>(R.id.tilProveedor)
            val acProveedor = view.findViewById<MaterialAutoCompleteTextView>(R.id.acProveedor)
            val tilDescripcion = view.findViewById<TextInputLayout>(R.id.tilDescripcion)
            val tilPrecio = view.findViewById<TextInputLayout>(R.id.tilPrecio)
            val etFecha = view.findViewById<TextInputEditText>(R.id.etFecha)
            val chips = view.findViewById<ChipGroup>(R.id.chipsFormaPago)
            val tvResumen = view.findViewById<TextView>(R.id.tvResumen)
            val btnPdf = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPdf)
            var pdfUri: android.net.Uri? = null

            var consorcio = consorcios.firstOrNull { it.id == consorcioId } ?: consorcios.first()
            var proveedor = proveedores.firstOrNull { it.id == proveedorId }
            var fecha = LocalDate.now()
            var forma = formas.first()

            // Consorcio y proveedor se eligen en una lista con buscador, foto e info
            val opcionesConsorcio = consorcios.map {
                OpcionFoto(it.id, it.nombre, it.direccion + (it.descripcion?.let { d -> " · $d" } ?: ""), it.img)
            }
            val opcionesProveedor = proveedores.map {
                OpcionFoto(it.id, it.nombre, it.info, it.img,
                    it.promedio?.let { p -> "⭐ " + String.format(java.util.Locale("es", "AR"), "%.1f", p) + " (${it.cantResenas})" })
            }
            fun pintarConsorcio() { acConsorcio.setText(consorcio.nombre, false); f.fotoEnCampo(tilConsorcio, consorcio.img) }
            fun pintarProveedor() {
                acProveedor.setText(proveedor?.nombre.orEmpty(), false)
                f.fotoEnCampo(tilProveedor, proveedor?.img)
            }
            pintarConsorcio()
            pintarProveedor()
            listOf(acConsorcio, tilConsorcio).forEach { v -> v.setOnClickListener {
                f.elegirConFoto(R.string.contrato_elegir_consorcio, R.string.contrato_buscar_consorcio, opcionesConsorcio, consorcio.id) { o ->
                    consorcio = consorcios.first { it.id == o.id }; pintarConsorcio()
                }
            } }
            listOf(acProveedor, tilProveedor).forEach { v -> v.setOnClickListener {
                f.elegirConFoto(R.string.contrato_elegir_proveedor, R.string.contrato_buscar_proveedor, opcionesProveedor, proveedor?.id) { o ->
                    proveedor = proveedores.first { it.id == o.id }; tilProveedor.error = null; pintarProveedor()
                }
            } }
            tilConsorcio.setEndIconOnClickListener { acConsorcio.performClick() }
            tilProveedor.setEndIconOnClickListener { acProveedor.performClick() }

            // Contrato formal en PDF (opcional)
            if (selectorPdf == null) btnPdf.visibility = View.GONE
            btnPdf.setOnClickListener {
                selectorPdf?.elegir { uri ->
                    com.example.consora_mobile.data.Archivos.validar(ctx, uri, soloPdf = true)?.let {
                        Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); return@elegir
                    }
                    pdfUri = uri
                    btnPdf.text = "📕  " + (ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "contrato.pdf")
                }
            }

            if (propuestaId != null && proveedor != null) {
                tvGanador.text = ctx.getString(R.string.contrato_ganador, proveedor!!.nombre, consorcio.nombre)
                tvGanador.visibility = View.VISIBLE
                tilConsorcio.visibility = View.GONE
                tilProveedor.visibility = View.GONE
            }
            descripcionSugerida?.let { tilDescripcion.editText?.setText(it.take(120)) }

            fun precio() = tilPrecio.editText?.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            fun resumen() {
                val p = precio()
                tvResumen.text = when {
                    p == null || p <= 0 -> ctx.getString(R.string.contrato_resumen_vacio)
                    forma.esContado -> ctx.getString(R.string.contrato_resumen_contado, Formatos.moneda(p))
                    else -> ctx.getString(R.string.contrato_resumen_cuotas, forma.cuotas, Formatos.moneda(kotlin.math.floor(p / forma.cuotas * 100) / 100))
                }
            }
            formas.forEach { fp ->
                chips.addView(Chip(ctx, null, com.google.android.material.R.attr.chipStyle).apply {
                    text = formaPagoTexto(ctx, fp.cuotas)
                    isCheckable = true
                    isChecked = fp == forma
                    setOnClickListener { forma = fp; resumen() }
                })
            }
            tilPrecio.editText?.doAfterTextChanged { tilPrecio.error = null; resumen() }
            resumen()

            fun pintarFecha() = etFecha.setText(Formatos.fecha(fecha))
            pintarFecha()
            etFecha.setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()).build()
                picker.addOnPositiveButtonClickListener { millis ->
                    fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    pintarFecha()
                }
                picker.show(f.childFragmentManager, "fecha")
            }

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.contrato_nuevo)
                .setView(view)
                .setPositiveButton(R.string.contrato_crear, null)
                .setNegativeButton(R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val descripcion = tilDescripcion.editText?.text?.toString()?.trim().orEmpty()
                    val p = precio()
                    tilDescripcion.error = null
                    val elegido = proveedor
                    when {
                        elegido == null -> { tilProveedor.error = ctx.getString(R.string.error_requerido); return@setOnClickListener }
                        descripcion.isEmpty() -> { tilDescripcion.error = ctx.getString(R.string.error_requerido); return@setOnClickListener }
                        p == null || p <= 0 -> { tilPrecio.error = ctx.getString(R.string.contrato_precio_invalido); return@setOnClickListener }
                    }
                    f.viewLifecycleOwner.lifecycleScope.launch {
                        val pdf = pdfUri?.let {
                            com.example.consora_mobile.data.Archivos.guardar(ctx, it, com.example.consora_mobile.data.Carpeta.DOCS_CONTRATOS)
                        }
                        val id = ContratoRepository(ctx).crear(
                            adminId, consorcio.id, elegido!!.id, elegido.nombre, descripcion, p!!, forma, fecha, propuestaId, pdf,
                        )
                        Toast.makeText(ctx,
                            if (forma.esContado) ctx.getString(R.string.contrato_creado_contado)
                            else ctx.getString(R.string.contrato_creado_cuotas, forma.cuotas),
                            Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        creado(id)
                    }
                }
            }
            dialog.show()
        }

        /** Todos los contratos, o los de un consorcio. */
        fun newInstance(consorcioId: Long? = null, nombre: String? = null) = ContratosFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, consorcioId ?: -1L)
                putString(ARG_NOMBRE, nombre)
            }
        }
    }
}
