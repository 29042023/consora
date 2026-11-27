package com.example.consora_mobile.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.example.consora_mobile.data.DetalleVotacion
import com.example.consora_mobile.data.Propuesta
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** Detalle de una votación: invitados, votar, resultados, votos y documentos. */
class VotacionDetalleFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity

    private val selectorPdf = SelectorPdf(this)

    /** Contrato cargado a partir de esta votación, si ya existe. */
    private var contratoId: Long? = null
    private val repo by lazy { VotacionRepository(requireContext()) }
    private val votacionId get() = requireArguments().getLong(ARG_ID)
    private val dp get() = resources.displayMetrics.density

    private lateinit var content: LinearLayout

    private val subirDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.firstNotNullOfOrNull { Archivos.validar(requireContext(), it) }?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        viewLifecycleOwner.lifecycleScope.launch {
            uris.forEach { repo.agregarDoc(votacionId, Archivos.guardar(requireContext(), it, Carpeta.DOCS_VOTACIONES, "votacion_$votacionId")) }
            cargar()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.votacion_titulo), "")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        content = view.findViewById(R.id.sectionContent)
        cargar()
    }

    private fun cargar() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val d = repo.detalle(votacionId, usuario.id) ?: return@launch
            contratoId = com.example.consora_mobile.data.ContratoRepository(requireContext()).deVotacion(votacionId)
            requireView().findViewById<TextView>(R.id.tvSectionSubtitle).text = d.votacion.consorcio
            mostrar(d, usuario.id)
        }
    }

    private fun mostrar(d: DetalleVotacion, usuarioId: Long) {
        val v = d.votacion
        // Solo el administrador del consorcio gestiona; propietarios votan; asistentes solo miran
        val gestiona = v.representanteId == usuarioId
        content.removeAllViews()

        // Cabecera
        tarjeta().apply {
            val (estado, color) = VotacionesFragment.estado(v)
            addView(texto(getString(if (v.esContratacion) R.string.votacion_tipo_contratacion else R.string.votacion_tipo_comun) +
                "  ·  " + getString(estado), color, bold = true, sp = 13f))
            addView(texto(v.detalle, R.color.consora_navy, bold = true, sp = 16f).conMargen(top = 6))
            addView(texto(getString(if (v.abierta) R.string.votacion_meta_cierra else R.string.votacion_meta_cerro,
                getString(R.string.votacion_creada_el, Formatos.fechaHora(v.fecha)), Formatos.fechaHora(v.fechaLimite)), R.color.consora_gray).conMargen(top = 4))
            d.propuestas.firstOrNull { it.contratado }?.let { p ->
                addView(texto(getString(R.string.votacion_contratado, p.proveedor), R.color.consora_teal, bold = true).conMargen(top = 8))
                // El contrato (precio y forma de pago) se carga después de elegir al proveedor
                val contrato = contratoId
                if (contrato != null) addView(boton(R.string.contrato_ver, outlined = true) {
                    main.showOverlay(ContratoDetalleFragment.newInstance(contrato))
                }) else if (gestiona) addView(boton(R.string.contrato_cargar) {
                    ContratosFragment.nuevoContrato(this@VotacionDetalleFragment, v.consorcioId, p.id, p.proveedorId, v.detalle, selectorPdf) {
                        main.showOverlay(ContratoDetalleFragment.newInstance(it))
                    }
                })
            }
        }

        // Proveedores invitados (contratación): la invitación les llega por chat
        if (v.esContratacion) tarjeta(R.string.votacion_invitados).apply {
            d.propuestas.forEach { addView(filaPropuesta(it, v.abierta, gestiona)) }
            if (d.propuestas.isEmpty()) addView(texto(getString(R.string.votacion_sin_invitados), R.color.consora_gray))
            if (gestiona && v.abierta) addView(boton(R.string.votacion_invitar_otro, outlined = true) { invitarOtro(d) })
        }

        // Votar
        if (v.abierta && !v.lanzada) {
            tarjeta().apply { addView(texto(getString(R.string.votacion_no_lanzada), R.color.consora_pendiente, bold = true)) }
        } else if (v.abierta && d.puedoVotar) tarjeta(if (d.miVoto != null) R.string.votacion_cambiar_voto else R.string.votacion_votar).apply {
            val grupo = RadioGroup(requireContext())
            d.opciones.forEachIndexed { i, op ->
                grupo.addView(RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = op.texto
                    tag = op.valor
                    isChecked = op.valor == d.miVoto
                })
            }
            addView(grupo)
            addView(boton(R.string.votacion_votar) {
                val elegido = grupo.findViewById<RadioButton>(grupo.checkedRadioButtonId)?.tag as? String
                if (elegido == null) {
                    Toast.makeText(requireContext(), R.string.votacion_elegi_opcion, Toast.LENGTH_SHORT).show()
                } else viewLifecycleOwner.lifecycleScope.launch {
                    repo.votar(votacionId, usuarioId, elegido)
                    Toast.makeText(requireContext(), R.string.votacion_voto_ok, Toast.LENGTH_SHORT).show()
                    cargar()
                }
            })
        }

        // Resultados
        tarjeta(R.string.votacion_resultados).apply {
            d.opciones.forEach { op ->
                addView(texto((if (op.gana && !v.abierta) "🏆 " else "") + op.texto + "  ·  ${op.cantidad} (${op.porcentaje}%)",
                    R.color.consora_navy, bold = op.gana).conMargen(top = 6))
                addView(LinearProgressIndicator(requireContext()).apply {
                    max = 100
                    progress = op.porcentaje
                    trackCornerRadius = (4 * dp).toInt()
                    setIndicatorColor(ContextCompat.getColor(context, if (op.gana) R.color.consora_teal else R.color.consora_teal_light))
                })
            }
            val votaron = d.votos.size
            addView(texto(getString(R.string.votacion_participacion, votaron, d.habilitados,
                if (d.habilitados > 0) votaron * 100 / d.habilitados else 0), R.color.consora_gray).conMargen(top = 10))
        }

        // Votos
        tarjeta(R.string.votacion_votos).apply {
            if (d.votos.isEmpty()) addView(texto(getString(R.string.votacion_sin_votos), R.color.consora_gray))
            d.votos.forEach { addView(texto("${it.usuario}:  ${it.voto}", R.color.consora_navy).conMargen(top = 4)) }
        }

        // Documentos: tocando se abren, manteniendo apretado se borran
        tarjeta(R.string.votacion_documentos).apply {
            if (d.documentos.isEmpty()) addView(texto(getString(R.string.votacion_sin_docs), R.color.consora_gray))
            d.documentos.forEach { doc ->
                addView(texto("📄  ${doc.nombre}", R.color.consora_teal, bold = true).conMargen(top = 6).apply {
                    setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), doc.doc, doc.nombre) } }
                    if (gestiona) setOnLongClickListener {
                        confirmar(getString(R.string.votacion_borrar_doc, doc.nombre)) {
                            viewLifecycleOwner.lifecycleScope.launch { repo.borrarDoc(doc); cargar() }
                        }
                        true
                    }
                })
            }
            if (gestiona) addView(boton(R.string.votacion_subir_docs, outlined = true) { subirDocs.launch(Archivos.MIME_PERMITIDOS) })
        }

        // Asistente: ve la votación de sus edificios, pero no vota
        if (!gestiona && !d.puedoVotar) tarjeta().apply {
            addView(texto(getString(R.string.votacion_solo_lectura), R.color.consora_gray))
        }

        // Acciones (solo el administrador del consorcio)
        if (gestiona) LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            if (v.abierta) addView(boton(R.string.votacion_cerrar, outlined = true, peso = true) {
                confirmar(getString(R.string.votacion_cerrar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch { repo.cerrar(votacionId, main.usuario?.id); cargar() }
                }
            })
            addView(boton(R.string.votacion_eliminar, outlined = true, peso = true) {
                confirmar(getString(R.string.votacion_eliminar_confirmar)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repo.eliminar(votacionId)
                        parentFragmentManager.popBackStack()
                    }
                }
            })
            content.addView(this)
        }
    }

    /** Proveedor invitado: estado, propuesta y acciones (contratar al cerrar / cancelar si no respondió). */
    private fun filaPropuesta(p: Propuesta, abierta: Boolean, gestiona: Boolean) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        addView(texto(p.proveedor + if (p.contratado) "  ·  " + getString(R.string.votacion_contratado_chip) else "",
            R.color.consora_navy, bold = true, sp = 15f))
        val (estado, color) = when (p.aceptacion) {
            true -> R.string.votacion_acepto to R.color.consora_teal
            false -> R.string.votacion_rechazo to R.color.consora_error
            null -> R.string.votacion_esperando_respuesta to R.color.consora_pendiente
        }
        addView(texto(getString(estado), color, bold = true, sp = 13f))
        p.pdf?.let { pdf ->
            addView(texto("📕  " + getString(R.string.votacion_ver_propuesta), R.color.consora_teal, sp = 13f).apply {
                setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), pdf, "Propuesta ${p.proveedor}.pdf") } }
            })
        }
        if (gestiona && !abierta && p.aceptacion == true && !p.contratado) addView(boton(R.string.votacion_contratar, outlined = true) {
            confirmar(getString(R.string.votacion_contratar_confirmar, p.proveedor)) {
                viewLifecycleOwner.lifecycleScope.launch { repo.contratar(votacionId, p.id, main.usuario?.id); cargar() }
            }
        })
        if (gestiona && abierta && p.aceptacion == null) addView(boton(R.string.votacion_cancelar_invitacion, outlined = true) {
            confirmar(getString(R.string.votacion_cancelar_invitacion)) {
                viewLifecycleOwner.lifecycleScope.launch { repo.cancelarInvitacion(p.id); cargar() }
            }
        })
    }

    private fun invitarOtro(d: DetalleVotacion) {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val invitados = d.propuestas.map { it.proveedorId }.toSet()
            val disponibles = repo.proveedores().filter { it.id !in invitados }
            if (disponibles.isEmpty()) {
                Toast.makeText(requireContext(), R.string.votacion_todos_invitados, Toast.LENGTH_SHORT).show()
                return@launch
            }
            elegirProveedores(R.string.votacion_invitar_otro, disponibles, emptySet(), multiple = false) { ids ->
                val elegido = disponibles.first { it.id in ids }
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.invitar(votacionId, d.votacion.consorcio, d.votacion.detalle, usuario.id, elegido.id)
                    Toast.makeText(requireContext(), getString(R.string.votacion_invitacion_enviada, elegido.nombre), Toast.LENGTH_SHORT).show()
                    cargar()
                }
            }
        }
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
        private const val ARG_ID = "votacion_id"

        fun newInstance(votacionId: Long) = VotacionDetalleFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, votacionId) }
        }
    }
}
