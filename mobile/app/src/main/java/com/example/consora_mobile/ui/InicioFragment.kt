package com.example.consora_mobile.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.ChatActivity
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.AgendaRepository
import com.example.consora_mobile.data.Archivos
import com.example.consora_mobile.data.ChatRepository
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.FacturaRepository
import com.example.consora_mobile.data.InicioRepository
import com.example.consora_mobile.data.TareaAsistenteRepository
import com.example.consora_mobile.data.Usuario
import com.example.consora_mobile.data.VotacionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pestaña "Inicio" del asistente, el proveedor y el propietario (igual que en la web):
 * un resumen con lo que cada uno tiene que hacer.
 */
class InicioFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val dp get() = resources.displayMetrics.density
    private lateinit var content: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val u = main.usuario
        // A las personas se las saluda por el nombre; a las empresas (proveedores), con el nombre completo
        val nombre = if (u?.esProveedor == true) u.nameSurname else u?.nameSurname?.split(" ")?.firstOrNull().orEmpty()
        val subtitulo = when {
            u?.esProveedor == true -> getString(R.string.inicio_sub_proveedor)
            u?.esPropietario == true -> getString(R.string.inicio_sub_propietario)
            else -> getString(R.string.inicio_sub_asistente)
        }
        return inflateSection(inflater, container, getString(R.string.inicio_hola, nombre), subtitulo)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        content = view.findViewById(R.id.sectionContent)
    }

    // Se recarga al volver (por ejemplo, después de votar o responder una invitación)
    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val u = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            content.removeAllViews()
            when {
                u.esProveedor -> inicioProveedor(u)
                u.esPropietario -> inicioPropietario(u)
                else -> inicioAsistente(u)
            }
        }
    }

    // ---------- Asistente: tareas, edificios habilitados, votaciones y agenda ----------

    private suspend fun inicioAsistente(u: Usuario) {
        val repoTareas = TareaAsistenteRepository(requireContext())
        val tareas = repoTareas.listar(u.id)
        val consorcios = ConsorcioRepository(requireContext()).listarVisibles(u)
        val votaciones = VotacionRepository(requireContext()).listar(u).filter { it.abierta }
        val admin = InicioRepository(requireContext()).adminDeAsistente(u.id)

        tarjeta(R.string.inicio_mis_tareas).apply {
            if (tareas.isEmpty()) addView(texto(getString(R.string.inicio_sin_tareas), R.color.consora_gray))
            // Tocando la tarea se marca como completada (o vuelve a en proceso)
            tareas.forEach { t ->
                addView(texto((if (t.completada) "✅  " else "⭕  ") + t.descripcion,
                    if (t.completada) R.color.consora_gray else R.color.consora_navy).fila().apply {
                    if (t.completada) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch { repoTareas.cambiarEstado(t.id, !t.completada); cargar() }
                    }
                })
            }
            if (admin != null) addView(boton(getString(R.string.inicio_escribir_admin)) { abrirChat(u.id, admin.first, admin.second, admin.third) })
        }

        tarjeta(R.string.inicio_edificios_habilitados).apply {
            if (consorcios.isEmpty()) addView(texto(getString(R.string.inicio_sin_edificios), R.color.consora_gray))
            consorcios.forEach { c ->
                addView(texto("🏢  ${c.nombre}", R.color.consora_navy, bold = true).fila())
                addView(texto(c.direccion, R.color.consora_gray, sp = 13f))
                addView(LinearLayout(requireContext()).apply {
                    addView(boton(getString(R.string.inicio_facturacion), peso = true) {
                        main.showOverlay(FacturasFragment.newInstance(c.id, c.nombre))
                    })
                    addView(boton(getString(R.string.votaciones_titulo), peso = true) {
                        main.showOverlay(VotacionesFragment.newInstance(c.id, c.nombre))
                    })
                })
            }
        }

        tarjetaVotaciones(votaciones)
        tarjetaEventos(u)
    }

    // ---------- Proveedor: invitaciones, propuestas y perfil del Marketplace ----------

    private suspend fun inicioProveedor(u: Usuario) {
        val repo = InicioRepository(requireContext())
        val invitaciones = repo.invitaciones(u.id)
        val resumen = repo.resumenMarketplace(u.id)
        val pendientes = invitaciones.filter { it.aceptacion == null && it.abierta }
        val participando = invitaciones.filter { it.aceptacion == true }

        tarjeta(R.string.inicio_mi_marketplace).apply {
            val estrellas = resumen.promedio?.let { String.format(Locale("es", "AR"), "%.1f", it) } ?: "—"
            addView(texto(getString(R.string.inicio_marketplace_resumen, resumen.publicaciones, resumen.resenas, estrellas),
                R.color.consora_navy, bold = true))
            resumen.descripcion?.let { addView(texto(it, R.color.consora_gray, sp = 13f).fila()) }
            addView(boton(getString(R.string.inicio_ver_mi_perfil)) {
                main.showOverlay(ProveedorPerfilFragment.newInstance(u.id, u.nameSurname, u.img))
            })
        }

        // Contratos: cómo le van pagando los consorcios
        val contratos = com.example.consora_mobile.data.ContratoRepository(requireContext()).listar(u)
        tarjeta(R.string.contratos_mis).apply {
            val vigentes = contratos.filter { it.estado == com.example.consora_mobile.data.Contrato.VIGENTE }
            if (vigentes.isEmpty()) addView(texto(getString(R.string.contratos_vacio_proveedor), R.color.consora_gray))
            vigentes.take(3).forEach { c ->
                addView(texto("📄  ${c.descripcion}", R.color.consora_navy, bold = true).fila())
                addView(texto(c.consorcio + "  ·  " + getString(R.string.contrato_avance, c.cantPagas, c.cantCuotas,
                    Formatos.moneda(c.pagado), Formatos.moneda(c.precio)), R.color.consora_gray, sp = 13f))
            }
            if (contratos.isNotEmpty()) addView(boton(getString(R.string.contrato_ver_todos)) {
                main.showOverlay(ContratosFragment.newInstance())
            })
            if (contratos.isNotEmpty()) addView(boton(getString(R.string.trabajos_ver)) {
                main.showOverlay(TrabajosFragment())
            })
        }

        tarjeta(R.string.inicio_invitaciones).apply {
            if (pendientes.isEmpty()) addView(texto(getString(R.string.inicio_sin_invitaciones), R.color.consora_gray))
            pendientes.forEach { inv ->
                addView(texto("✉️  ${inv.detalle}", R.color.consora_navy, bold = true).fila())
                addView(texto(getString(R.string.inicio_invitacion_meta, inv.consorcio, Formatos.fechaHora(inv.fechaLimite)), R.color.consora_gray, sp = 13f))
                inv.chatId?.let { chat ->
                    addView(boton(getString(R.string.inicio_responder)) {
                        startActivity(ChatActivity.intent(requireContext(), chat, inv.admin, getString(R.string.inicio_administrador), inv.adminImg))
                    })
                }
            }
        }

        tarjeta(R.string.inicio_mis_propuestas).apply {
            if (participando.isEmpty()) addView(texto(getString(R.string.inicio_sin_propuestas), R.color.consora_gray))
            participando.forEach { inv ->
                addView(texto("📄  ${inv.detalle}", R.color.consora_navy, bold = true).fila())
                val (estado, color) = when {
                    inv.contratado -> R.string.inicio_te_contrataron to R.color.consora_teal
                    inv.abierta -> R.string.inicio_en_votacion to R.color.consora_pendiente
                    else -> R.string.inicio_votacion_cerrada to R.color.consora_gray
                }
                addView(texto(inv.consorcio + "  ·  " + getString(estado), color, sp = 13f))
                inv.pdf?.let { pdf ->
                    addView(texto("📕  " + getString(R.string.votacion_ver_propuesta), R.color.consora_teal, sp = 13f).apply {
                        setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), pdf, "Propuesta.pdf") } }
                    })
                }
            }
        }
    }

    // ---------- Propietario: su unidad, votaciones, gastos y agenda del edificio ----------

    private suspend fun inicioPropietario(u: Usuario) {
        val unidades = InicioRepository(requireContext()).unidades(u.id)
        val votaciones = VotacionRepository(requireContext()).listar(u).filter { it.abierta }

        if (unidades.isEmpty()) content.addEmptyState(R.drawable.ic_nav_consorcios,
            getString(R.string.inicio_sin_unidad_titulo), getString(R.string.inicio_sin_unidad_mensaje))
        unidades.forEach { un ->
            tarjeta().apply {
                addView(texto("🏢  ${un.consorcio}" + (un.unidad?.let { " · $it" } ?: ""), R.color.consora_navy, bold = true, sp = 17f))
                addView(texto(un.direccion, R.color.consora_gray, sp = 13f))
                addView(texto(getString(R.string.inicio_administra, un.admin), R.color.consora_gray, sp = 13f))
                addView(boton(getString(R.string.inicio_escribir_admin)) { abrirChat(u.id, un.adminId, un.admin, un.adminImg) })
            }
        }

        tarjetaVotaciones(votaciones, propietario = true)

        // Gastos: deudas pendientes y últimas facturas pagadas del consorcio
        val facturas = FacturaRepository(requireContext())
        tarjeta(R.string.inicio_gastos).apply {
            var hay = false
            unidades.forEach { un ->
                facturas.deudasPendientes(un.consorcioId).take(4).forEach { d ->
                    hay = true
                    addView(texto("⏳  ${d.descripcion}  ·  ${Formatos.moneda(d.importe)}", R.color.consora_pendiente).fila())
                }
                facturas.listByConsorcio(un.consorcioId).take(3).forEach { f ->
                    hay = true
                    addView(texto("🧾  ${f.deuda}  ·  ${Formatos.moneda(f.importe)} (" + getString(R.string.inicio_pagada) + ")", R.color.consora_navy).fila())
                }
            }
            if (!hay) addView(texto(getString(R.string.inicio_sin_gastos), R.color.consora_gray))
        }

        tarjetaEventos(u)
    }

    // ---------- Partes comunes ----------

    private fun tarjetaVotaciones(votaciones: List<com.example.consora_mobile.data.Votacion>, propietario: Boolean = false) {
        tarjeta(R.string.inicio_votaciones_abiertas).apply {
            if (votaciones.isEmpty()) addView(texto(getString(R.string.inicio_sin_votaciones), R.color.consora_gray))
            votaciones.forEach { v ->
                val estado = when {
                    !v.lanzada -> " · " + getString(R.string.votacion_estado_esperando)
                    propietario && v.yaVote -> " · " + getString(R.string.votacion_votaste)
                    propietario -> " · " + getString(R.string.inicio_falta_votar)
                    else -> ""
                }
                addView(texto("🗳  ${v.detalle}", R.color.consora_navy, bold = true).fila().apply {
                    setOnClickListener { main.showOverlay(VotacionDetalleFragment.newInstance(v.id)) }
                })
                addView(texto("${v.consorcio} · ${Formatos.fechaHora(v.fechaLimite)}$estado",
                    if (propietario && !v.yaVote && v.lanzada) R.color.consora_pendiente else R.color.consora_gray, sp = 13f))
            }
        }
    }

    private suspend fun tarjetaEventos(u: Usuario) {
        val eventos = AgendaRepository(requireContext()).proximos(u).take(5)
        tarjeta(R.string.inicio_proximos_eventos).apply {
            if (eventos.isEmpty()) addView(texto(getString(R.string.inicio_sin_eventos), R.color.consora_gray))
            eventos.forEach { e ->
                addView(texto("📅  ${Formatos.dia(e.fecha)}" + (e.hora?.let { " $it" } ?: "") + "  ·  ${e.titulo}", R.color.consora_navy).fila())
                e.consorcio?.let { addView(texto(it, R.color.consora_gray, sp = 13f)) }
            }
        }
    }

    private fun abrirChat(usuarioId: Long, otroId: Long, nombre: String, img: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val chat = ChatRepository(requireContext()).obtenerOCrearIndividual(usuarioId, otroId)
            startActivity(ChatActivity.intent(requireContext(), chat, nombre, getString(R.string.inicio_administrador), img))
        }
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

    private fun texto(t: String, color: Int, bold: Boolean = false, sp: Float = 15f) = TextView(requireContext()).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(ContextCompat.getColor(context, color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** Separación arriba de cada ítem de una lista. */
    private fun TextView.fila() = apply { setPadding(0, (10 * dp).toInt(), 0, 0) }

    private fun boton(texto: String, peso: Boolean = false, accion: () -> Unit) =
        MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = texto
            layoutParams = (if (peso) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                .apply { topMargin = (8 * dp).toInt(); if (peso) marginEnd = (6 * dp).toInt() }
            setOnClickListener { accion() }
        }
}
