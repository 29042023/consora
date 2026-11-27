package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Archivos
import com.example.consora_mobile.data.Carpeta
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.Votacion
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Votaciones de los consorcios del administrador (todas, o las de un consorcio si se abre
 * desde su tarjeta). Comunes (Sí / No / Abstención) o de contratación de proveedores.
 */
class VotacionesFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { VotacionRepository(requireContext()) }
    private val consorcioId get() = requireArguments().getLong(ARG_ID, -1L).takeIf { it > 0 }

    private var votaciones: List<Votacion> = emptyList()
    private var filtro = FILTRO_TODAS
    private lateinit var lista: LinearLayout

    /** Documentos elegidos en el formulario de nueva votación (se copian al crearla). */
    private var docsElegidos: List<android.net.Uri> = emptyList()
    private var alElegirDocs: (() -> Unit)? = null
    private val elegirDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val error = uris.firstNotNullOfOrNull { Archivos.validar(requireContext(), it) }
        if (error != null) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        docsElegidos = uris
        alElegirDocs?.invoke()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container, getString(R.string.votaciones_titulo),
            requireArguments().getString(ARG_NOMBRE) ?: getString(R.string.votaciones_subtitulo),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)

        // Filtros: Todas / Abiertas / Cerradas
        val chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf(FILTRO_TODAS to R.string.votaciones_todas, FILTRO_ABIERTAS to R.string.votaciones_abiertas,
            FILTRO_CERRADAS to R.string.votaciones_cerradas).forEach { (valor, texto) ->
            chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                setText(texto)
                isCheckable = true
                isChecked = valor == filtro
                setOnClickListener { filtro = valor; mostrar() }
            })
        }
        content.addView(chips)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        // Solo el administrador crea votaciones; asistentes y propietarios las ven
        if (main.usuario?.esAdmin == true) view.showFab(R.string.votacion_nueva) { nuevaVotacion() }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            votaciones = repo.listar(usuario, consorcioId)
            mostrar()
        }
    }

    private fun mostrar() {
        val filtradas = votaciones.filter {
            when (filtro) {
                FILTRO_ABIERTAS -> it.abierta
                FILTRO_CERRADAS -> !it.abierta
                else -> true
            }
        }
        lista.removeAllViews()
        if (filtradas.isEmpty()) {
            lista.addEmptyState(R.drawable.ic_nav_agenda, getString(R.string.votaciones_vacio_titulo), getString(R.string.votaciones_vacio_mensaje))
            return
        }
        filtradas.forEach { lista.addView(item(it)) }
    }

    private fun item(v: Votacion): View = layoutInflater.inflate(R.layout.item_votacion, lista, false).apply {
        findViewById<TextView>(R.id.tvTipo).setText(if (v.esContratacion) R.string.votacion_tipo_contratacion else R.string.votacion_tipo_comun)
        findViewById<TextView>(R.id.tvEstado).apply {
            val (texto, color) = estado(v)
            setText(texto)
            setTextColor(ContextCompat.getColor(context, color))
        }
        findViewById<TextView>(R.id.tvDetalle).text = v.detalle
        findViewById<TextView>(R.id.tvMeta).text = getString(
            if (v.abierta) R.string.votacion_meta_cierra else R.string.votacion_meta_cerro, v.consorcio, Formatos.fechaHora(v.fechaLimite),
        )
        findViewById<TextView>(R.id.tvVotos).text = buildList {
            add(resources.getQuantityString(R.plurals.votos_cantidad, v.cantidadVotos, v.cantidadVotos))
            if (v.esContratacion) add(resources.getQuantityString(R.plurals.proveedores_cantidad, v.cantidadPropuestas, v.cantidadPropuestas))
            if (v.yaVote) add(getString(R.string.votacion_votaste))
        }.joinToString(" · ")
        setOnClickListener { main.showOverlay(VotacionDetalleFragment.newInstance(v.id)) }
    }

    /** Formulario de nueva votación. */
    private fun nuevaVotacion() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val consorcios = ConsorcioRepository(requireContext()).listByRepresentante(usuario.id)
            if (consorcios.isEmpty()) {
                Toast.makeText(requireContext(), R.string.votacion_sin_consorcios, Toast.LENGTH_LONG).show()
                return@launch
            }
            mostrarFormulario(usuario.id, consorcios, repo.proveedores())
        }
    }

    private fun mostrarFormulario(adminId: Long, consorcios: List<Consorcio>, proveedores: List<com.example.consora_mobile.data.ProveedorInvitable>) {
        val view = layoutInflater.inflate(R.layout.dialog_votacion, null)
        val acConsorcio = view.findViewById<MaterialAutoCompleteTextView>(R.id.acConsorcio)
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleTipo)
        val tvAyuda = view.findViewById<TextView>(R.id.tvTipoAyuda)
        val tilDetalle = view.findViewById<TextInputLayout>(R.id.tilDetalle)
        val etLimite = view.findViewById<TextInputEditText>(R.id.etLimite)
        val btnProveedores = view.findViewById<MaterialButton>(R.id.btnProveedores)
        val btnDocs = view.findViewById<MaterialButton>(R.id.btnDocs)

        var consorcio = consorcios.firstOrNull { it.id == consorcioId } ?: consorcios.first()
        var fecha = LocalDate.now().plusDays(7)
        var hora = LocalTime.of(20, 0)
        var elegidos = emptySet<Long>()
        docsElegidos = emptyList()

        acConsorcio.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, consorcios.map { it.nombre }))
        acConsorcio.setText(consorcio.nombre, false)
        acConsorcio.setOnItemClickListener { _, _, pos, _ -> consorcio = consorcios[pos] }

        fun esContratacion() = toggle.checkedButtonId == R.id.btnContratacion
        fun pintarProveedores() {
            btnProveedores.text = getString(R.string.votacion_elegir_proveedores_n, elegidos.size)
        }
        toggle.addOnButtonCheckedListener { _, _, _ ->
            btnProveedores.visibility = if (esContratacion()) View.VISIBLE else View.GONE
            tvAyuda.setText(if (esContratacion()) R.string.votacion_ayuda_contratacion else R.string.votacion_ayuda_comun)
        }
        pintarProveedores()
        btnProveedores.setOnClickListener {
            elegirProveedores(R.string.votacion_elegir_proveedores, proveedores, elegidos, multiple = true) {
                elegidos = it
                pintarProveedores()
            }
        }

        fun pintarLimite() = etLimite.setText("${Formatos.fecha(fecha)} $hora")
        pintarLimite()
        etLimite.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                val reloj = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(hora.hour).setMinute(hora.minute).build()
                reloj.addOnPositiveButtonClickListener { hora = LocalTime.of(reloj.hour, reloj.minute); pintarLimite() }
                reloj.show(childFragmentManager, "hora")
                pintarLimite()
            }
            picker.show(childFragmentManager, "fecha")
        }

        alElegirDocs = { btnDocs.text = getString(R.string.votacion_docs_n, docsElegidos.size) }
        btnDocs.setOnClickListener { elegirDocs.launch(Archivos.MIME_PERMITIDOS) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.votacion_nueva)
            .setView(view)
            .setPositiveButton(R.string.votacion_crear, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Se reemplaza el listener para que el diálogo no se cierre si falta algo
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val detalle = tilDetalle.editText?.text?.toString()?.trim().orEmpty()
                val limite = fecha.atTime(hora)
                val invitados = elegidos.toList()
                when {
                    detalle.isEmpty() -> { tilDetalle.error = getString(R.string.error_requerido); return@setOnClickListener }
                    !limite.isAfter(java.time.LocalDateTime.now()) -> {
                        Toast.makeText(requireContext(), R.string.votacion_limite_pasado, Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    esContratacion() && invitados.isEmpty() -> {
                        Toast.makeText(requireContext(), R.string.votacion_sin_proveedores, Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val tipo = if (esContratacion()) Votacion.CONTRATACION else Votacion.COMUN
                    val id = repo.crear(
                        adminId, consorcio.id, consorcio.nombre, tipo, detalle,
                        limite.format(com.example.consora_mobile.data.ChatFormato.FECHA_HORA), invitados, emptyList(),
                    )
                    // Documentos en docs/votaciones/votacion_<id>/
                    docsElegidos.forEach { uri ->
                        repo.agregarDoc(id, Archivos.guardar(requireContext(), uri, Carpeta.DOCS_VOTACIONES, "votacion_$id"))
                    }
                    Toast.makeText(
                        requireContext(),
                        if (tipo == Votacion.CONTRATACION) getString(R.string.votacion_creada_contratacion, invitados.size)
                        else getString(R.string.votacion_creada),
                        Toast.LENGTH_LONG,
                    ).show()
                    dialog.dismiss()
                    main.showOverlay(VotacionDetalleFragment.newInstance(id))
                }
            }
        }
        dialog.show()
    }

    companion object {
        private const val ARG_ID = "consorcio_id"
        private const val ARG_NOMBRE = "consorcio_nombre"
        private const val FILTRO_TODAS = 0
        private const val FILTRO_ABIERTAS = 1
        private const val FILTRO_CERRADAS = 2

        /** Texto y color del estado de una votación. */
        fun estado(v: Votacion): Pair<Int, Int> = when {
            v.abierta && !v.lanzada -> R.string.votacion_estado_esperando to R.color.consora_pendiente
            v.abierta -> R.string.votacion_estado_abierta to R.color.consora_teal
            else -> R.string.votacion_estado_cerrada to R.color.consora_gray
        }

        /** Todas las votaciones, o las de un consorcio. */
        fun newInstance(consorcioId: Long? = null, nombre: String? = null) = VotacionesFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, consorcioId ?: -1L)
                putString(ARG_NOMBRE, nombre)
            }
        }
    }
}
