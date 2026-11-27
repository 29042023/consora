package com.example.consora_mobile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.example.consora_mobile.data.DetalleReunion
import com.example.consora_mobile.data.Participante
import com.example.consora_mobile.data.Reunion
import com.example.consora_mobile.data.ReunionRepository
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Detalle de una reunión: link de Meet, mi respuesta, proveedores invitados, participantes y documentos. */
class ReunionDetalleFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo by lazy { ReunionRepository(requireContext()) }
    private val reunionId get() = requireArguments().getLong(ARG_ID)
    private val dp get() = resources.displayMetrics.density

    private lateinit var content: LinearLayout

    /** Tipo del documento que se está subiendo (orden del día, acta u otro). */
    private var tipoDoc = "otro"
    private val subirDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.firstNotNullOfOrNull { Archivos.validar(requireContext(), it) }?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        viewLifecycleOwner.lifecycleScope.launch {
            uris.forEach {
                repo.agregarDoc(reunionId, Archivos.guardar(requireContext(), it, Carpeta.DOCS_REUNIONES, "reunion_$reunionId"), tipoDoc)
            }
            cargar()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.reunion_titulo), "")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        content = view.findViewById(R.id.sectionContent)
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val d = repo.detalle(reunionId, usuario.id) ?: return@launch
            requireView().findViewById<TextView>(R.id.tvSectionSubtitle).text = d.reunion.consorcio
            mostrar(d, usuario.id)
        }
    }

    private fun mostrar(d: DetalleReunion, usuarioId: Long) {
        val r = d.reunion
        val gestiona = r.organizadorId == usuarioId || r.representanteId == usuarioId
        content.removeAllViews()

        // Cabecera
        tarjeta().apply {
            val (estado, color) = ReunionesFragment.estado(r)
            addView(texto(getString(if (r.esVirtual) R.string.reunion_virtual_meet else R.string.reunion_presencial) +
                "  ·  " + getString(estado), color, bold = true, sp = 13f).apply { logoMeet(r.esVirtual) })
            addView(texto(r.titulo, R.color.consora_navy, bold = true, sp = 17f).conMargen(top = 6))
            r.descripcion?.let { addView(texto(it, R.color.consora_navy).conMargen(top = 4)) }
            addView(texto("📅  " + Formatos.dia(r.fechaInicio.take(10)) + ", " + Formatos.hora(r.fechaInicio) +
                " a " + Formatos.hora(r.fechaFin) + " h", R.color.consora_gray).conMargen(top = 8))
            if (!r.esVirtual) addView(texto("📍  " + r.lugar.orEmpty(), R.color.consora_gray).conMargen(top = 2))
            addView(texto(getString(R.string.reunion_organiza, r.organizador), R.color.consora_gray, sp = 12f).conMargen(top = 6))

            // Link de Meet: el proveedor invitado lo ve recién cuando acepta
            if (r.esVirtual && r.activa) when {
                r.meetLink != null && (gestiona || d.veLink) -> {
                    addView(texto(r.meetLink, R.color.consora_teal, bold = true).conMargen(top = 10))
                    r.meetCodigo?.let { addView(texto(getString(R.string.reunion_codigo, it), R.color.consora_gray, sp = 13f)) }
                    addView(boton(R.string.reunion_unirme) { abrirLink(r.meetLink) }.apply {
                        // Logo de Meet en el botón, con sus colores (sobre fondo blanco)
                        setIcon(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_google_meet))
                        iconTint = null
                        iconSize = (22 * dp).toInt()
                        setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.white))
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.consora_navy))
                        strokeColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.consora_divider))
                        strokeWidth = (1 * dp).toInt()
                    })
                }
                r.meetLink != null -> addView(texto(getString(R.string.reunion_link_al_aceptar), R.color.consora_pendiente, bold = true).conMargen(top = 10))
                else -> addView(texto(getString(R.string.reunion_sin_link), R.color.consora_gray).conMargen(top = 10))
            }
        }

        // Mi respuesta (participantes que no gestionan)
        val yo = d.yo
        if (yo != null && r.activa && !gestiona) tarjeta(R.string.reunion_vas_a_participar).apply {
            addView(texto(getString(when (yo.respuesta) {
                Reunion.ACEPTA -> R.string.reunion_confirmaste
                Reunion.RECHAZA -> R.string.reunion_avisaste_no
                else -> if (yo.esOpcional) R.string.reunion_te_invitaron else R.string.reunion_confirma_asistencia
            }), if (yo.respuesta == Reunion.ACEPTA) R.color.consora_teal else R.color.consora_gray, bold = yo.respuesta == Reunion.ACEPTA))
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                if (yo.respuesta != Reunion.ACEPTA) addView(boton(R.string.reunion_voy, peso = true) { responder(yo, true) })
                if (yo.respuesta != Reunion.RECHAZA) addView(boton(R.string.reunion_no_voy, outlined = true, peso = true) { responder(yo, false) })
            })
        }

        // Link cargado a mano (administrador)
        if (gestiona && r.esVirtual && r.activa) tarjeta(R.string.reunion_google_meet).apply {
            addView(texto(getString(R.string.reunion_link_web), R.color.consora_gray, sp = 13f))
            addView(boton(if (r.meetLink == null) R.string.reunion_cargar_link else R.string.reunion_cambiar_link, outlined = true) { editarLink(r) })
        }

        // Proveedores invitados (opcionales)
        if (gestiona) tarjeta(R.string.reunion_proveedores_invitados).apply {
            val invitados = d.participantes.filter { it.rol == "proveedor" }
            if (invitados.isEmpty()) addView(texto(getString(R.string.reunion_sin_proveedores), R.color.consora_gray, sp = 13f))
            invitados.forEach { p ->
                addView(texto(p.nombre, R.color.consora_navy, bold = true, sp = 15f).conMargen(top = 8))
                val (estado, color) = when (p.respuesta) {
                    Reunion.ACEPTA -> R.string.reunion_proveedor_acepto to R.color.consora_teal
                    Reunion.RECHAZA -> R.string.votacion_rechazo to R.color.consora_error
                    else -> R.string.votacion_esperando_respuesta to R.color.consora_pendiente
                }
                addView(texto(getString(estado), color, bold = true, sp = 13f))
                if (r.activa && p.respuesta == Reunion.PENDIENTE) addView(boton(R.string.votacion_cancelar_invitacion, outlined = true) {
                    confirmar(getString(R.string.votacion_cancelar_invitacion)) {
                        viewLifecycleOwner.lifecycleScope.launch { repo.cancelarInvitacion(p.id); cargar() }
                    }
                })
            }
            if (r.activa) addView(boton(R.string.reunion_invitar_proveedor_btn, outlined = true) { invitarProveedor(d) })
        }

        // Participantes
        tarjeta(R.string.reunion_participantes).apply {
            val confirmados = d.participantes.filter { it.respuesta == Reunion.ACEPTA }
            val pendientes = d.participantes.filter { it.respuesta == Reunion.PENDIENTE }
            val rechazaron = d.participantes.filter { it.respuesta == Reunion.RECHAZA }
            if (confirmados.isEmpty()) addView(texto(getString(R.string.reunion_nadie_confirmo), R.color.consora_gray, sp = 13f))
            confirmados.forEach {
                addView(texto("✔  ${it.nombre}  ·  ${rol(it)}" + if (it.asistio == true) "  ·  " + getString(R.string.reunion_asistio) else "",
                    R.color.consora_navy).conMargen(top = 4))
            }
            if (pendientes.isNotEmpty()) addView(texto(getString(R.string.reunion_sin_responder), R.color.consora_gray, bold = true, sp = 13f).conMargen(top = 10))
            pendientes.forEach { addView(texto("⏳  ${it.nombre}  ·  ${rol(it)}", R.color.consora_gray).conMargen(top = 4)) }
            if (rechazaron.isNotEmpty()) addView(texto(getString(R.string.reunion_no_van), R.color.consora_gray, bold = true, sp = 13f).conMargen(top = 10))
            rechazaron.forEach { addView(texto("✖  ${it.nombre}  ·  ${rol(it)}", R.color.consora_gray).conMargen(top = 4)) }

            // Una vez empezada, el administrador marca quiénes asistieron
            if (gestiona && confirmados.isNotEmpty() && (r.estadoActual == Reunion.EN_CURSO || r.estadoActual == Reunion.FINALIZADA)) {
                addView(boton(R.string.reunion_marcar_asistencia, outlined = true) { marcarAsistencia(confirmados) })
            }
        }

        // Documentos: tocando se abren, manteniendo apretado se borran
        tarjeta(R.string.votacion_documentos).apply {
            if (d.documentos.isEmpty()) addView(texto(getString(R.string.reunion_sin_docs), R.color.consora_gray, sp = 13f))
            d.documentos.forEach { doc ->
                addView(texto("📄  ${doc.nombre}  ·  ${getString(tipoDocTexto(doc.tipo))}", R.color.consora_teal, bold = true).conMargen(top = 6).apply {
                    setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), doc.doc, doc.nombre) } }
                    if (gestiona) setOnLongClickListener {
                        confirmar(getString(R.string.votacion_borrar_doc, doc.nombre)) {
                            viewLifecycleOwner.lifecycleScope.launch { repo.borrarDoc(doc); cargar() }
                        }
                        true
                    }
                })
            }
            if (gestiona) addView(boton(R.string.votacion_subir_docs, outlined = true) { elegirTipoYSubir(r) })
        }

        // Acciones (organizador / administrador del consorcio)
        if (gestiona) LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            if (r.activa) addView(boton(R.string.reunion_cancelar, outlined = true, peso = true) {
                confirmar(getString(R.string.reunion_cancelar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch { repo.cancelar(reunionId, main.usuario?.id); cargar() }
                }
            })
            addView(boton(R.string.votacion_eliminar, outlined = true, peso = true) {
                confirmar(getString(R.string.reunion_eliminar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repo.eliminar(reunionId)
                        parentFragmentManager.popBackStack()
                    }
                }
            })
            content.addView(this)
        }
    }

    private fun rol(p: Participante) = getString(when (p.rol) {
        "administrador" -> R.string.rol_administrador
        "asistente" -> R.string.rol_asistente
        "proveedor" -> R.string.rol_proveedor
        else -> R.string.rol_propietario
    }) + if (p.esOpcional) " " + getString(R.string.reunion_opcional) else ""

    private fun tipoDocTexto(tipo: String) = when (tipo) {
        "orden_del_dia" -> R.string.doc_orden_del_dia
        "acta" -> R.string.doc_acta
        else -> R.string.doc_otro
    }

    private fun responder(yo: Participante, acepta: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.responder(yo.id, yo.usuarioId, acepta)
            Toast.makeText(requireContext(), if (acepta) R.string.reunion_aceptaste else R.string.reunion_rechazaste, Toast.LENGTH_SHORT).show()
            cargar()
        }
    }

    private fun abrirLink(link: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.reunion_no_se_pudo_abrir, Toast.LENGTH_SHORT).show()
        }
    }

    private fun editarLink(r: Reunion) {
        val contenedor = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val etLink = EditText(requireContext()).apply {
            setHint(R.string.reunion_link_campo)
            setText(r.meetLink.orEmpty())
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val etCodigo = EditText(requireContext()).apply {
            setHint(R.string.reunion_codigo_campo)
            setText(r.meetCodigo.orEmpty())
        }
        contenedor.addView(etLink)
        contenedor.addView(etCodigo)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reunion_google_meet)
            .setView(contenedor)
            .setPositiveButton(R.string.ok) { _, _ ->
                val link = ReunionesFragment.linkValido(etLink.text.toString())
                if (link == ReunionesFragment.INVALIDO) {
                    Toast.makeText(requireContext(), R.string.reunion_link_invalido, Toast.LENGTH_SHORT).show()
                } else viewLifecycleOwner.lifecycleScope.launch {
                    repo.guardarLink(reunionId, link, etCodigo.text.toString().trim().ifEmpty { null })
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun invitarProveedor(d: DetalleReunion) {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val invitados = d.participantes.map { it.usuarioId }.toSet()
            val disponibles = VotacionRepository(requireContext()).proveedores().filter { it.id !in invitados }
            if (disponibles.isEmpty()) {
                Toast.makeText(requireContext(), R.string.votacion_todos_invitados, Toast.LENGTH_SHORT).show()
                return@launch
            }
            elegirProveedores(R.string.reunion_invitar_proveedor_btn, disponibles, emptySet(), multiple = false) { ids ->
                val elegido = disponibles.first { it.id in ids }
                val r = d.reunion
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.invitar(reunionId, r.titulo, r.consorcio, r.fechaInicio, usuario.id, elegido.id)
                    Toast.makeText(requireContext(), getString(R.string.votacion_invitacion_enviada, elegido.nombre), Toast.LENGTH_SHORT).show()
                    cargar()
                }
            }
        }
    }

    private fun marcarAsistencia(confirmados: List<Participante>) {
        val marcados = BooleanArray(confirmados.size) { confirmados[it].asistio == true }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reunion_marcar_asistencia)
            .setMultiChoiceItems(confirmados.map { it.nombre }.toTypedArray(), marcados) { _, i, marcado -> marcados[i] = marcado }
            .setPositiveButton(R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.guardarAsistencia(reunionId, confirmados.filterIndexed { i, _ -> marcados[i] }.map { it.id }.toSet())
                    Toast.makeText(requireContext(), R.string.reunion_asistencia_guardada, Toast.LENGTH_SHORT).show()
                    cargar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun elegirTipoYSubir(r: Reunion) {
        val tipos = listOf("orden_del_dia", "acta", "otro")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reunion_tipo_doc)
            .setItems(tipos.map { getString(tipoDocTexto(it)) }.toTypedArray()) { _, i ->
                tipoDoc = tipos[i]
                subirDocs.launch(Archivos.MIME_PERMITIDOS)
            }
            .show()
    }

    private fun confirmar(mensaje: String, accion: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(mensaje)
            .setPositiveButton(R.string.ok) { _, _ -> accion() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Tarjeta con título opcional; devuelve el contenedor donde agregar filas. */
    private fun tarjeta(titulo: Int? = null): LinearLayout {
        val cuerpo = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            if (titulo != null) addView(TextView(context, null, 0, R.style.ConsoraCardTitle).apply { setText(titulo) })
        }
        content.addView(MaterialCardView(requireContext(), null, com.google.android.material.R.attr.materialCardViewOutlinedStyle).apply {
            radius = 16 * dp
            strokeColor = ContextCompat.getColor(context, R.color.consora_divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (12 * dp).toInt() }
            addView(cuerpo)
        })
        return cuerpo
    }

    private fun texto(t: String, color: Int, bold: Boolean = false, sp: Float = 14f) = TextView(requireContext()).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(ContextCompat.getColor(context, color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun TextView.conMargen(top: Int) = apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (top * dp).toInt() }
    }

    private fun boton(texto: Int, outlined: Boolean = false, peso: Boolean = false, accion: () -> Unit) =
        MaterialButton(requireContext(), null,
            if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle,
        ).apply {
            setText(texto)
            layoutParams = (if (peso) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                .apply { topMargin = (6 * dp).toInt(); if (peso) marginEnd = (6 * dp).toInt() }
            setOnClickListener { accion() }
        }

    companion object {
        private const val ARG_ID = "reunion_id"

        fun newInstance(reunionId: Long) = ReunionDetalleFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, reunionId) }
        }
    }
}
