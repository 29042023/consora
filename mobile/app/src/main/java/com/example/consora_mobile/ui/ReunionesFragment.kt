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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.ProveedorInvitable
import com.example.consora_mobile.data.Reunion
import com.example.consora_mobile.data.ReunionRepository
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Reuniones de los consorcios (todas, o las de un consorcio si se abre desde su tarjeta).
 * El proveedor ve las reuniones a las que lo invitaron. Solo el administrador las crea.
 */
class ReunionesFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { ReunionRepository(requireContext()) }
    private val consorcioId get() = arguments?.getLong(ARG_ID, -1L)?.takeIf { it > 0 }

    private var reuniones: List<Reunion> = emptyList()
    private var proximas = true
    private lateinit var lista: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container, getString(R.string.reuniones_titulo),
            arguments?.getString(ARG_NOMBRE) ?: getString(R.string.reuniones_subtitulo),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)

        // Filtros: Próximas / Pasadas y canceladas
        val chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf(true to R.string.reuniones_proximas, false to R.string.reuniones_pasadas).forEach { (valor, texto) ->
            chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                setText(texto)
                isCheckable = true
                isChecked = valor == proximas
                setOnClickListener { proximas = valor; mostrar() }
            })
        }
        content.addView(chips)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        if (main.usuario?.esAdmin == true) view.showFab(R.string.reunion_nueva) { nuevaReunion() }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            reuniones = repo.listar(usuario, consorcioId)
            mostrar()
        }
    }

    private fun mostrar() {
        val filtradas = reuniones.filter { it.activa == proximas }.let { if (proximas) it else it.reversed() }
        lista.removeAllViews()
        if (filtradas.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_agenda,
                getString(if (proximas) R.string.reuniones_vacio_proximas else R.string.reuniones_vacio_pasadas),
                getString(if (main.usuario?.esAdmin == true) R.string.reuniones_vacio_admin else R.string.reuniones_vacio_mensaje),
            )
            return
        }
        filtradas.forEach { lista.addView(item(it)) }
    }

    private fun item(r: Reunion): View = layoutInflater.inflate(R.layout.item_votacion, lista, false).apply {
        findViewById<TextView>(R.id.tvTipo).apply {
            setText(if (r.esVirtual) R.string.reunion_virtual_meet else R.string.reunion_presencial)
            logoMeet(r.esVirtual)
        }
        findViewById<TextView>(R.id.tvEstado).apply {
            val (texto, color) = estado(r)
            setText(texto)
            setTextColor(ContextCompat.getColor(context, color))
        }
        findViewById<TextView>(R.id.tvDetalle).text = r.titulo
        findViewById<TextView>(R.id.tvMeta).text = getString(
            R.string.reunion_meta, r.consorcio, Formatos.fechaHora(r.fechaInicio), Formatos.hora(r.fechaFin),
        )
        findViewById<TextView>(R.id.tvVotos).text = buildList {
            add(resources.getQuantityString(R.plurals.confirmados_cantidad, r.confirmados, r.confirmados))
            when (r.miRespuesta) {
                Reunion.PENDIENTE -> if (r.activa) add(getString(R.string.reunion_confirma))
                Reunion.ACEPTA -> add(getString(R.string.reunion_vas))
                Reunion.RECHAZA -> add(getString(R.string.reunion_no_vas))
            }
        }.joinToString(" · ")
        setOnClickListener { main.showOverlay(ReunionDetalleFragment.newInstance(r.id)) }
    }

    /** Formulario de nueva reunión. */
    private fun nuevaReunion() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val consorcios = ConsorcioRepository(requireContext()).listByRepresentante(usuario.id)
            if (consorcios.isEmpty()) {
                Toast.makeText(requireContext(), R.string.votacion_sin_consorcios, Toast.LENGTH_LONG).show()
                return@launch
            }
            mostrarFormulario(usuario.id, consorcios, VotacionRepository(requireContext()).proveedores())
        }
    }

    private fun mostrarFormulario(adminId: Long, consorcios: List<Consorcio>, proveedores: List<ProveedorInvitable>) {
        val view = layoutInflater.inflate(R.layout.dialog_reunion, null)
        val acConsorcio = view.findViewById<MaterialAutoCompleteTextView>(R.id.acConsorcio)
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleModalidad)
        val tilTitulo = view.findViewById<TextInputLayout>(R.id.tilTitulo)
        val tilDescripcion = view.findViewById<TextInputLayout>(R.id.tilDescripcion)
        val etInicio = view.findViewById<TextInputEditText>(R.id.etInicio)
        val acDuracion = view.findViewById<MaterialAutoCompleteTextView>(R.id.acDuracion)
        val tilLink = view.findViewById<TextInputLayout>(R.id.tilLink)
        val tilLugar = view.findViewById<TextInputLayout>(R.id.tilLugar)
        val swProveedor = view.findViewById<MaterialSwitch>(R.id.swProveedor)
        val tvAyuda = view.findViewById<View>(R.id.tvProveedorAyuda)
        val btnProveedores = view.findViewById<MaterialButton>(R.id.btnProveedores)

        var consorcio = consorcios.firstOrNull { it.id == consorcioId } ?: consorcios.first()
        var fecha = LocalDate.now().plusDays(3)
        var hora = LocalTime.of(19, 0)
        var minutos = 60L
        var elegidos = emptySet<Long>()

        acConsorcio.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, consorcios.map { it.nombre }))
        acConsorcio.setText(consorcio.nombre, false)
        acConsorcio.setOnItemClickListener { _, _, pos, _ -> consorcio = consorcios[pos] }

        val duraciones = DURACIONES.map { (m, t) -> m to getString(t) }
        acDuracion.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, duraciones.map { it.second }))
        acDuracion.setText(duraciones.first { it.first == minutos }.second, false)
        acDuracion.setOnItemClickListener { _, _, pos, _ -> minutos = duraciones[pos].first }

        fun esVirtual() = toggle.checkedButtonId == R.id.btnVirtual
        toggle.addOnButtonCheckedListener { _, _, _ ->
            tilLink.visibility = if (esVirtual()) View.VISIBLE else View.GONE
            tilLugar.visibility = if (esVirtual()) View.GONE else View.VISIBLE
        }

        fun pintarProveedores() { btnProveedores.text = getString(R.string.votacion_elegir_proveedores_n, elegidos.size) }
        pintarProveedores()
        swProveedor.setOnCheckedChangeListener { _, activo ->
            tvAyuda.visibility = if (activo) View.VISIBLE else View.GONE
            btnProveedores.visibility = if (activo) View.VISIBLE else View.GONE
        }
        btnProveedores.setOnClickListener {
            elegirProveedores(R.string.reunion_elegir_proveedor, proveedores, elegidos, multiple = true) {
                elegidos = it
                pintarProveedores()
            }
        }

        fun pintarInicio() = etInicio.setText("${Formatos.fecha(fecha)} $hora")
        pintarInicio()
        etInicio.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                val reloj = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(hora.hour).setMinute(hora.minute).build()
                reloj.addOnPositiveButtonClickListener { hora = LocalTime.of(reloj.hour, reloj.minute); pintarInicio() }
                reloj.show(childFragmentManager, "hora")
                pintarInicio()
            }
            picker.show(childFragmentManager, "fecha")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reunion_nueva_titulo)
            .setView(view)
            .setPositiveButton(R.string.reunion_crear, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Se reemplaza el listener para que el diálogo no se cierre si falta algo
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val titulo = tilTitulo.editText?.text?.toString()?.trim().orEmpty()
                val descripcion = tilDescripcion.editText?.text?.toString()?.trim().orEmpty().ifEmpty { null }
                val link = linkValido(tilLink.editText?.text?.toString())
                val lugar = tilLugar.editText?.text?.toString()?.trim().orEmpty()
                val inicio = fecha.atTime(hora)
                tilTitulo.error = null; tilLink.error = null; tilLugar.error = null
                when {
                    titulo.isEmpty() -> { tilTitulo.error = getString(R.string.error_requerido); return@setOnClickListener }
                    !inicio.isAfter(LocalDateTime.now()) -> {
                        Toast.makeText(requireContext(), R.string.reunion_fecha_pasada, Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    esVirtual() && link == INVALIDO -> { tilLink.error = getString(R.string.reunion_link_invalido); return@setOnClickListener }
                    !esVirtual() && lugar.isEmpty() -> { tilLugar.error = getString(R.string.error_requerido); return@setOnClickListener }
                    swProveedor.isChecked && elegidos.isEmpty() -> {
                        Toast.makeText(requireContext(), R.string.reunion_sin_proveedor, Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                }
                val invitados = if (swProveedor.isChecked) elegidos.toList() else emptyList()
                viewLifecycleOwner.lifecycleScope.launch {
                    val id = repo.crear(
                        adminId, consorcio.id, consorcio.nombre, titulo, descripcion,
                        if (esVirtual()) Reunion.VIRTUAL else Reunion.PRESENCIAL, lugar, inicio, minutos, link, invitados,
                    )
                    Toast.makeText(
                        requireContext(),
                        if (invitados.isEmpty()) getString(R.string.reunion_creada)
                        else resources.getQuantityString(R.plurals.reunion_creada_proveedores, invitados.size, invitados.size),
                        Toast.LENGTH_LONG,
                    ).show()
                    dialog.dismiss()
                    main.showOverlay(ReunionDetalleFragment.newInstance(id))
                }
            }
        }
        dialog.show()
    }

    companion object {
        private const val ARG_ID = "consorcio_id"
        private const val ARG_NOMBRE = "consorcio_nombre"
        const val INVALIDO = "invalido"

        val DURACIONES = listOf(
            30L to R.string.duracion_30, 60L to R.string.duracion_60, 90L to R.string.duracion_90,
            120L to R.string.duracion_120, 180L to R.string.duracion_180,
        )

        /** null si está vacío, [INVALIDO] si no es un link https, o el link (con https:// agregado si faltaba). */
        fun linkValido(texto: String?): String? {
            val t = texto?.trim().orEmpty()
            if (t.isEmpty()) return null
            val link = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
            return if (link.startsWith("https://") && android.util.Patterns.WEB_URL.matcher(link).matches()) link else INVALIDO
        }

        /** Texto y color del estado de una reunión. */
        fun estado(r: Reunion): Pair<Int, Int> = when (r.estadoActual) {
            Reunion.EN_CURSO -> R.string.reunion_estado_en_curso to R.color.consora_teal
            Reunion.FINALIZADA -> R.string.reunion_estado_finalizada to R.color.consora_gray
            Reunion.CANCELADA -> R.string.reunion_estado_cancelada to R.color.consora_error
            else -> R.string.reunion_estado_programada to R.color.consora_navy
        }

        /** Todas las reuniones, o las de un consorcio. */
        fun newInstance(consorcioId: Long? = null, nombre: String? = null) = ReunionesFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, consorcioId ?: -1L)
                putString(ARG_NOMBRE, nombre)
            }
        }
    }
}
