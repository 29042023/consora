package com.example.consora_mobile.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.EventoHistorial
import com.example.consora_mobile.data.HistorialRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Historial de los edificios (todos los que ve el usuario, o uno si se abre desde su tarjeta):
 * contratos, pagos, votaciones, reuniones y obras, agrupados por mes. El administrador carga obras a mano.
 */
class HistorialFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { HistorialRepository(requireContext()) }
    private val consorcioId get() = arguments?.getLong(ARG_ID, -1L)?.takeIf { it > 0 }
    private val dp get() = resources.displayMetrics.density

    private var eventos: List<EventoHistorial> = emptyList()
    private var categoria: String? = null
    private lateinit var lista: LinearLayout
    private lateinit var t: Tarjetas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(
            inflater, container, getString(R.string.historial_titulo),
            arguments?.getString(ARG_NOMBRE) ?: getString(R.string.historial_subtitulo),
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)

        // Filtro por tipo, en una fila deslizable
        val chips = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true }
        (listOf<String?>(null) + EventoHistorial.CATEGORIAS).forEach { valor ->
            chips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                text = if (valor == null) getString(R.string.historial_todo) else categoriaTexto(valor)
                isCheckable = true
                isChecked = valor == categoria
                setOnClickListener { categoria = valor; mostrar() }
            })
        }
        content.addView(HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)
        t = Tarjetas(requireContext(), lista)

        if (main.usuario?.esAdmin == true) view.showFab(R.string.historial_agregar) { agregar() }
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            eventos = repo.listar(usuario, consorcioId)
            mostrar()
        }
    }

    private fun mostrar() {
        val filtrados = eventos.filter { categoria == null || it.categoria == categoria }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(R.drawable.ic_nav_agenda, getString(R.string.historial_vacio_titulo), getString(R.string.historial_vacio_mensaje))
            return
        }
        val mes = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "AR"))
        filtrados.groupBy { it.fecha.take(7) }.forEach { (clave, delMes) ->
            lista.addView(TextView(requireContext()).apply {
                text = LocalDate.parse("$clave-01").format(mes).uppercase()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(context, R.color.consora_gray))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding((4 * dp).toInt(), (8 * dp).toInt(), 0, (6 * dp).toInt())
            })
            t.tarjeta().apply { delMes.forEachIndexed { i, e -> addView(fila(e, i > 0)) } }
        }
    }

    private fun fila(e: EventoHistorial, separador: Boolean) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ((if (separador) 10 else 0) * dp).toInt(), 0, (4 * dp).toInt())
        addView(TextView(requireContext()).apply {
            text = icono(e.categoria)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, (12 * dp).toInt(), 0)
        })
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(t.texto(e.descripcion, R.color.consora_navy, bold = true))
            addView(t.texto(listOfNotNull(
                categoriaTexto(e.categoria),
                e.consorcio.takeIf { consorcioId == null },
                Formatos.fecha(e.fecha) + if (e.manual) "" else " " + e.hora.take(5),
                e.usuario,
            ).joinToString(" · "), R.color.consora_gray, sp = 12f))
        })
        // El administrador puede borrar las entradas que cargó a mano
        if (e.manual && e.representanteId == main.usuario?.id) setOnLongClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.historial_borrar_confirmar)
                .setPositiveButton(R.string.ok) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.borrar(e.id); cargar() } }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    /** Entrada manual: una obra, un mantenimiento u otro hecho del edificio. */
    private fun agregar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val consorcios = ConsorcioRepository(requireContext()).listByRepresentante(usuario.id)
            if (consorcios.isEmpty()) {
                Toast.makeText(requireContext(), R.string.votacion_sin_consorcios, Toast.LENGTH_LONG).show()
                return@launch
            }
            val ctx = requireContext()
            var consorcio = consorcios.firstOrNull { it.id == consorcioId } ?: consorcios.first()
            var cat = EventoHistorial.MANUALES.first()
            var fecha = LocalDate.now()

            val pad = (24 * dp).toInt()
            val form = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, (8 * dp).toInt(), pad, 0) }
            fun desplegable(hint: Int, opciones: List<String>, inicial: String, elegir: (Int) -> Unit) =
                TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
                    this.hint = getString(hint)
                    addView(MaterialAutoCompleteTextView(context).apply {
                        inputType = 0
                        setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, opciones))
                        setText(inicial, false)
                        setOnItemClickListener { _, _, pos, _ -> elegir(pos) }
                    })
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = (12 * dp).toInt() }
                }
            form.addView(desplegable(R.string.historial_edificio, consorcios.map { it.nombre }, consorcio.nombre) { consorcio = consorcios[it] })
            form.addView(desplegable(R.string.historial_tipo, EventoHistorial.MANUALES.map { categoriaTexto(it) }, categoriaTexto(cat)) {
                cat = EventoHistorial.MANUALES[it]
            })
            val etFecha = TextInputEditText(ctx).apply { isFocusable = false; setText(Formatos.fecha(fecha)) }
            form.addView(TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                hint = getString(R.string.historial_fecha)
                addView(etFecha)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = (12 * dp).toInt() }
            })
            etFecha.setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build())
                    .build()
                picker.addOnPositiveButtonClickListener { millis ->
                    fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    etFecha.setText(Formatos.fecha(fecha))
                }
                picker.show(childFragmentManager, "fecha")
            }
            val tilDescripcion = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                hint = getString(R.string.historial_que_paso)
                addView(TextInputEditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 2
                })
            }
            form.addView(tilDescripcion)

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.historial_agregar)
                .setView(form)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val texto = tilDescripcion.editText?.text?.toString()?.trim().orEmpty()
                    if (texto.isEmpty()) { tilDescripcion.error = getString(R.string.error_requerido); return@setOnClickListener }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repo.agregar(consorcio.id, cat, texto, fecha, usuario.id)
                        Toast.makeText(ctx, R.string.historial_agregado, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        cargar()
                    }
                }
            }
            dialog.show()
        }
    }

    private fun categoriaTexto(c: String) = getString(when (c) {
        "contrato" -> R.string.historial_cat_contrato
        "pago" -> R.string.historial_cat_pago
        "votacion" -> R.string.historial_cat_votacion
        "reunion" -> R.string.historial_cat_reunion
        "obra" -> R.string.historial_cat_obra
        else -> R.string.historial_cat_otro
    })

    private fun icono(c: String) = when (c) {
        "contrato" -> "📄"
        "pago" -> "💵"
        "votacion" -> "🗳"
        "reunion" -> "📹"
        "obra" -> "🛠"
        else -> "ℹ️"
    }

    companion object {
        private const val ARG_ID = "consorcio_id"
        private const val ARG_NOMBRE = "consorcio_nombre"

        /** Historial de todos los edificios del usuario, o de uno. */
        fun newInstance(consorcioId: Long? = null, nombre: String? = null) = HistorialFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, consorcioId ?: -1L)
                putString(ARG_NOMBRE, nombre)
            }
        }
    }
}
