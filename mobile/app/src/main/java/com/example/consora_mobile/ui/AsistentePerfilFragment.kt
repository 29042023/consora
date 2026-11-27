package com.example.consora_mobile.ui

import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.EdificioAsistenteRepository
import com.example.consora_mobile.data.TareaAsistente
import com.example.consora_mobile.data.TareaAsistenteRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Perfil de un asistente: sus tareas (tocando "Tareas") y, en lugar de publicaciones, los edificios del administrador
 * con cada uno habilitado o no para el asistente (edificios_asistente; tocando el edificio se cambia).
 * Las opciones de cada edificio (permisos) todavía no existen en la base y aparecen apagadas.
 */
class AsistentePerfilFragment : Fragment() {

    private val args get() = requireArguments()
    private val asistenteId get() = args.getLong(ARG_ID)
    private val nombre get() = args.getString(ARG_NOMBRE).orEmpty()
    private val tareasRepo get() = TareaAsistenteRepository(requireContext())
    private val edificiosRepo get() = EdificioAsistenteRepository(requireContext())

    /** Consorcios en los que está habilitado; null mientras carga. */
    private var habilitados: MutableSet<Long>? = null
    private val img get() = args.getString(ARG_IMG)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_perfil, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<TextView>(R.id.tvTitulo).text = nombre
        view.findViewById<TextView>(R.id.tvNombre).text = nombre
        view.findViewById<TextView>(R.id.tvSubtitulo).text =
            getString(R.string.asistente_subtitulo, args.getString(ARG_MAIL).orEmpty())
        mostrarAvatar(viewLifecycleOwner.lifecycleScope, view.findViewById(R.id.tvAvatar), view.findViewById(R.id.ivAvatar), nombre, img)
        view.findViewById<View>(R.id.destacados).visibility = View.GONE
        view.findViewById<ImageView>(R.id.ivPestania).setImageResource(R.drawable.ic_nav_consorcios)

        cargarStats()

        view.findViewById<View>(R.id.btnMensaje).setOnClickListener {
            abrirChatCon(asistenteId, nombre, getString(R.string.rol_asistente), img)
        }
        view.findViewById<MaterialButton>(R.id.btnSecundario).apply {
            setText(R.string.asistente_historial)
            setOnClickListener { mostrarHistorial() }
        }

        val contenido = view.findViewById<LinearLayout>(R.id.contenidoPerfil)
        val usuario = (requireActivity() as MainActivity).usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val edificios = ConsorcioRepository(requireContext()).listByRepresentante(usuario.id)
            habilitados = edificiosRepo.habilitados(asistenteId).toMutableSet()
            cargarStats()
            if (edificios.isEmpty()) {
                contenido.addEmptyState(
                    R.drawable.ic_nav_consorcios,
                    getString(R.string.asistente_sin_edificios_titulo),
                    getString(R.string.asistente_sin_edificios_mensaje),
                )
                return@launch
            }
            contenido.addView(TextView(requireContext()).apply {
                setText(R.string.asistente_edificios_titulo)
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.consora_gray))
                val p = (20 * resources.displayMetrics.density).toInt()
                setPadding(p, p / 2, p, 0)
            })
            edificios.forEach { contenido.addView(itemEdificio(contenido, it)) }
        }
    }

    /** Números del perfil; "Tareas" muestra las que están en proceso y al tocarlo abre la lista. */
    private fun cargarStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tareas = tareasRepo.listar(asistenteId)
            val stats = requireView().findViewById<LinearLayout>(R.id.statsRow)
            stats.removeAllViews()
            stats.addView(stat(stats, habilitados?.size?.toString(), getString(R.string.asistente_edificios)))
            stats.addView(stat(stats, tareas.count { !it.completada }.toString(), getString(R.string.asistente_tareas)).apply {
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { verTareas(tareas) }
            })
            // TODO: antigüedad
            stats.addView(statVacio(stats, getString(R.string.asistente_antiguedad)))
        }
    }

    private fun verTareas(tareas: List<TareaAsistente>) {
        val sheet = BottomSheetDialog(requireContext())
        val contenido = layoutInflater.inflate(R.layout.sheet_tareas, null)
        contenido.findViewById<TextView>(R.id.tvTitulo).text = getString(R.string.tareas_titulo, nombre)
        val completadas = tareas.count { it.completada }
        contenido.findViewById<TextView>(R.id.tvResumen).text =
            if (tareas.isEmpty()) getString(R.string.tareas_vacio)
            else getString(R.string.tareas_resumen, tareas.size - completadas, completadas)

        contenido.findViewById<View>(R.id.btnNueva).setOnClickListener {
            sheet.dismiss()
            nuevaTarea()
        }

        val lista = contenido.findViewById<LinearLayout>(R.id.listaTareas)
        tareas.forEach { t ->
            lista.addView(itemTarea(lista, t) { sheet.dismiss() })
        }

        sheet.setContentView(contenido)
        sheet.show()
    }

    /** Tocando la tarea se marca como completada (o vuelve a en proceso); manteniendo apretado se borra. */
    private fun itemTarea(parent: ViewGroup, t: TareaAsistente, cerrarHoja: () -> Unit): View =
        layoutInflater.inflate(R.layout.item_tarea, parent, false).apply {
            val cb = findViewById<MaterialCheckBox>(R.id.cbCompletada)
            val tvDescripcion = findViewById<TextView>(R.id.tvDescripcion)
            val tvEstado = findViewById<TextView>(R.id.tvEstado)
            fun pintar(completada: Boolean) {
                cb.isChecked = completada
                tvDescripcion.text = t.descripcion
                tvDescripcion.paintFlags = if (completada) tvDescripcion.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else tvDescripcion.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvEstado.text = getString(
                    if (completada) R.string.tarea_completada else R.string.tarea_en_proceso,
                    Formatos.fecha(t.createdAt.take(10)),
                )
            }
            pintar(t.completada)

            var completada = t.completada
            val cambiar = {
                completada = !completada
                pintar(completada)
                viewLifecycleOwner.lifecycleScope.launch {
                    tareasRepo.cambiarEstado(t.id, completada)
                    cargarStats()
                }
            }
            cb.setOnClickListener { cambiar() }
            setOnClickListener { cambiar() }
            setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.tarea_borrar)
                    .setMessage(t.descripcion)
                    .setPositiveButton(R.string.perfil_borrar) { _, _ ->
                        cerrarHoja()
                        viewLifecycleOwner.lifecycleScope.launch {
                            tareasRepo.borrar(t.id)
                            cargarStats()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }

    private fun nuevaTarea() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val et = EditText(requireContext()).apply {
            setHint(R.string.tarea_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
        }
        val contenedor = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tarea_nueva)
            .setView(contenedor)
            .setPositiveButton(R.string.tarea_asignar) { _, _ ->
                val descripcion = et.text.toString().trim()
                if (descripcion.isEmpty()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    tareasRepo.crear(asistenteId, descripcion)
                    cargarStats()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun itemEdificio(parent: ViewGroup, c: Consorcio): View =
        layoutInflater.inflate(R.layout.item_edificio_asistente, parent, false).apply {
            findViewById<TextView>(R.id.tvEdificio).text = c.nombre
            findViewById<TextView>(R.id.tvDireccion).text = c.direccion

            // El switch (o tocar el edificio) habilita o deshabilita al asistente
            val sw = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swHabilitado)
            sw.isChecked = habilitados?.contains(c.id) == true
            sw.setOnCheckedChangeListener { _, habilitar ->
                val set = habilitados ?: return@setOnCheckedChangeListener
                if (habilitar) set += c.id else set -= c.id
                cargarStats()
                viewLifecycleOwner.lifecycleScope.launch { edificiosRepo.cambiar(asistenteId, c.id, habilitar) }
            }
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { sw.toggle() }

            val permisos = findViewById<ChipGroup>(R.id.permisos)
            resources.getStringArray(R.array.asistente_permisos).forEach { permisos.addView(permisoApagado(permisos, it)) }
        }

    /** Opción del edificio sin habilitar: gris, como las estrellas sin calificación. */
    private fun permisoApagado(parent: ViewGroup, texto: String) = TextView(parent.context).apply {
        text = texto
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, R.color.consora_gray))
        setBackgroundResource(R.drawable.bg_chip_apagado)
        val h = (10 * resources.displayMetrics.density).toInt()
        val v = (4 * resources.displayMetrics.density).toInt()
        setPadding(h, v, h, v)
    }

    // TODO: mostrar el historial real cuando exista en la base
    private fun mostrarHistorial() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.asistente_historial_titulo, nombre))
            .setMessage(R.string.asistente_historial_vacio)
            .setPositiveButton(R.string.cerrar, null)
            .show()
    }

    companion object {
        private const val ARG_ID = "asistente_id"
        private const val ARG_NOMBRE = "asistente_nombre"
        private const val ARG_MAIL = "asistente_mail"
        private const val ARG_IMG = "asistente_img"

        fun newInstance(id: Long, nombre: String, mail: String, img: String?) = AsistentePerfilFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, id)
                putString(ARG_NOMBRE, nombre)
                putString(ARG_MAIL, mail)
                putString(ARG_IMG, img)
            }
        }
    }
}
