package com.example.consora_mobile.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
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
import com.example.consora_mobile.data.AgendaRepository
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.Evento
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

/** Pestaña Agenda: eventos próximos agrupados por día, con alta y baja. */
class AgendaFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo get() = AgendaRepository(requireContext())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.nav_agenda), getString(R.string.agenda_subtitulo))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // El propietario ve la agenda de su edificio, pero no agenda eventos
        if (main.usuario?.esPropietario != true) view.showFab(R.string.agenda_nuevo) { nuevoEvento() }
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        val content = requireView().findViewById<LinearLayout>(R.id.sectionContent)

        viewLifecycleOwner.lifecycleScope.launch {
            val eventos = repo.proximos(usuario)
            content.removeAllViews()
            if (eventos.isEmpty()) {
                content.addEmptyState(
                    R.drawable.ic_nav_agenda,
                    getString(R.string.agenda_vacio_titulo),
                    getString(R.string.agenda_vacio_mensaje),
                )
                return@launch
            }
            // Los eventos ya vienen ordenados; se agrega un título cada vez que cambia el día
            eventos.groupBy { it.fecha }.forEach { (fecha, delDia) ->
                content.addView(tituloDia(Formatos.dia(fecha), primero = content.childCount == 0))
                delDia.forEach { content.addView(itemEvento(content, it)) }
            }
        }
    }

    private fun tituloDia(texto: String, primero: Boolean) = TextView(requireContext()).apply {
        text = texto
        setTextColor(ContextCompat.getColor(context, R.color.consora_teal))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
        val dp = resources.displayMetrics.density
        setPadding(0, if (primero) 0 else (12 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun itemEvento(parent: ViewGroup, e: Evento): View =
        layoutInflater.inflate(R.layout.item_evento, parent, false).apply {
            findViewById<TextView>(R.id.tvHora).text = e.hora ?: getString(R.string.evento_dia_corto)
            findViewById<TextView>(R.id.tvTitulo).text = e.titulo
            findViewById<TextView>(R.id.tvConsorcio).apply {
                text = e.consorcio
                visibility = if (e.consorcio == null) View.GONE else View.VISIBLE
            }
            findViewById<TextView>(R.id.tvDescripcion).apply {
                text = e.descripcion
                visibility = if (e.descripcion.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            // Solo se borran los eventos propios
            if (e.propio) setOnLongClickListener { confirmarEliminar(e); true }
        }

    private fun confirmarEliminar(e: Evento) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.evento_eliminar)
            .setMessage(getString(R.string.evento_eliminar_confirm, e.titulo))
            .setPositiveButton(R.string.eliminar) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.delete(e.id)
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun nuevoEvento() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val consorcios = ConsorcioRepository(requireContext()).listarVisibles(usuario)
            mostrarDialogo(usuario.id, consorcios)
        }
    }

    private fun mostrarDialogo(usuarioId: Long, consorcios: List<Consorcio>) {
        val view = layoutInflater.inflate(R.layout.dialog_evento, null)
        val tilTitulo = view.findViewById<TextInputLayout>(R.id.tilTitulo)
        val tilDescripcion = view.findViewById<TextInputLayout>(R.id.tilDescripcion)
        val etFecha = view.findViewById<TextInputEditText>(R.id.etFecha)
        val etHora = view.findViewById<TextInputEditText>(R.id.etHora)
        val acConsorcio = view.findViewById<MaterialAutoCompleteTextView>(R.id.acConsorcio)

        var fecha = LocalDate.now()
        var hora: LocalTime? = null
        var consorcioId: Long? = null

        etFecha.setText(Formatos.fecha(fecha))
        etHora.setText(R.string.evento_todo_el_dia)

        etFecha.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                // Solo fechas de hoy en adelante
                .setCalendarConstraints(
                    CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build()
                )
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                etFecha.setText(Formatos.fecha(fecha))
            }
            picker.show(childFragmentManager, "fecha")
        }

        etHora.setOnClickListener {
            val inicial = hora ?: LocalTime.of(9, 0)
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(inicial.hour)
                .setMinute(inicial.minute)
                .setNegativeButtonText(R.string.evento_todo_el_dia)
                .build()
            picker.addOnPositiveButtonClickListener {
                hora = LocalTime.of(picker.hour, picker.minute)
                etHora.setText(hora.toString())
            }
            picker.addOnNegativeButtonClickListener {
                hora = null
                etHora.setText(R.string.evento_todo_el_dia)
            }
            picker.show(childFragmentManager, "hora")
        }

        val opciones = listOf(getString(R.string.evento_ninguno)) + consorcios.map { it.nombre }
        acConsorcio.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones))
        acConsorcio.setText(opciones.first(), false)
        acConsorcio.setOnItemClickListener { _, _, position, _ ->
            consorcioId = if (position == 0) null else consorcios[position - 1].id
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.agenda_nuevo)
            .setView(view)
            .setPositiveButton(R.string.evento_guardar, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Se reemplaza el listener para que el diálogo no se cierre si falta el título
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val titulo = tilTitulo.editText?.text?.toString()?.trim().orEmpty()
                if (titulo.isEmpty()) {
                    tilTitulo.error = getString(R.string.error_requerido)
                    return@setOnClickListener
                }
                val descripcion = tilDescripcion.editText?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.insert(usuarioId, consorcioId, titulo, descripcion, fecha.toString(), hora?.toString())
                    Toast.makeText(requireContext(), R.string.evento_guardado, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    cargar()
                }
            }
        }
        dialog.show()
    }
}
