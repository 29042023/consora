package com.example.consora_mobile

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.data.Archivos
import com.example.consora_mobile.data.Carpeta
import com.example.consora_mobile.data.ChatRepository
import com.example.consora_mobile.data.InvitacionChat
import com.example.consora_mobile.data.InvitacionReunionChat
import com.example.consora_mobile.data.ReunionRepository
import com.example.consora_mobile.data.Mensaje
import com.example.consora_mobile.data.VotacionRepository
import com.example.consora_mobile.data.SessionManager
import com.example.consora_mobile.ui.ConsorcioImagen
import com.example.consora_mobile.ui.Formatos
import com.example.consora_mobile.ui.mostrarAvatar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Conversación con otra persona: mensajes en burbujas y barra para escribir. */
class ChatActivity : AppCompatActivity() {

    private lateinit var repo: ChatRepository
    private lateinit var scroll: ScrollView
    private lateinit var lista: LinearLayout
    private lateinit var etMensaje: EditText

    private var usuarioId = -1L
    private var chatId = -1L
    /** Día (yyyy-MM-dd) del último mensaje agregado, para saber cuándo poner un separador. */
    private var ultimoDia: String? = null

    /** Invitación que el proveedor está aceptando (mientras elige el PDF de su propuesta). */
    private var invitacionAceptando: InvitacionChat? = null

    // Adjuntar: PDF, Word, Excel o fotos (se manda junto con el texto escrito, si hay)
    private val elegirAdjunto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        Archivos.validar(this, uri)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); return@registerForActivityResult }
        val texto = etMensaje.text.toString().trim()
        etMensaje.text.clear()
        lifecycleScope.launch {
            // Cada conversación tiene su carpeta: docs/chats/chat_<id>/
            val archivo = Archivos.guardar(this@ChatActivity, uri, Carpeta.DOCS_CHATS, "chat_$chatId")
            agregar(repo.enviar(chatId, usuarioId, texto, archivo))
            scrollAlFinal()
        }
    }

    // Propuesta en PDF del proveedor al aceptar una invitación
    private val elegirPropuesta = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val invitacion = invitacionAceptando ?: return@registerForActivityResult
        invitacionAceptando = null
        if (uri == null) return@registerForActivityResult
        Archivos.validar(this, uri, soloPdf = true)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); return@registerForActivityResult }
        lifecycleScope.launch {
            val pdf = Archivos.guardar(this@ChatActivity, uri, Carpeta.DOCS_VOTACIONES, "votacion_${invitacion.votacionId}")
            responder(invitacion, true, pdf)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        val root = findViewById<View>(R.id.main)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = false
        val inputBar = findViewById<View>(R.id.inputBar)
        val inputPadding = inputBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            // ime() = teclado: la barra para escribir queda siempre arriba del teclado
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            inputBar.setPadding(inputBar.paddingLeft, inputBar.paddingTop, inputBar.paddingRight, inputPadding + bars.bottom)
            scrollAlFinal()
            insets
        }

        usuarioId = SessionManager(this).usuarioId ?: return finish()
        chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1L)
        val nombre = intent.getStringExtra(EXTRA_NOMBRE).orEmpty()

        repo = ChatRepository(this)
        scroll = findViewById(R.id.scroll)
        lista = findViewById(R.id.mensajes)
        etMensaje = findViewById(R.id.etMensaje)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        mostrarAvatar(
            lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar),
            nombre, intent.getStringExtra(EXTRA_IMG),
        )
        findViewById<TextView>(R.id.tvNombre).text = nombre
        findViewById<TextView>(R.id.tvRol).text = intent.getStringExtra(EXTRA_SUBTITULO)
        findViewById<View>(R.id.btnEnviar).setOnClickListener { enviar() }
        findViewById<View>(R.id.btnAdjuntar).setOnClickListener { elegirAdjunto.launch(Archivos.MIME_PERMITIDOS) }

        lifecycleScope.launch { cargarMensajes() }
        lifecycleScope.launch { habilitarPerfil() }
    }

    /**
     * Si la otra persona es un proveedor, o un asistente del administrador logueado, tocar su
     * avatar o su nombre vuelve a la pantalla principal y abre su perfil.
     */
    private suspend fun habilitarPerfil() {
        val otro = repo.otroMiembro(chatId, usuarioId) ?: return
        val perfil = when {
            otro.rol == "Proveedor" -> MainActivity.Perfil.PROVEEDOR
            otro.rol == "Asistente" && otro.idAdmin == usuarioId -> MainActivity.Perfil.ASISTENTE
            else -> return
        }
        listOf(R.id.avatarChat, R.id.infoChat).forEach { id ->
            findViewById<View>(id).apply {
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    startActivity(MainActivity.abrirPerfil(this@ChatActivity, perfil, otro.id, otro.nombre, otro.mail, otro.img))
                }
            }
        }
    }

    private suspend fun cargarMensajes() {
        lista.removeAllViews()
        ultimoDia = null
        repo.mensajes(chatId).forEach(::agregar)
        scrollAlFinal()
    }

    /** El proveedor acepta (con o sin PDF) o rechaza la invitación; su respuesta queda en el chat. */
    private suspend fun responder(invitacion: InvitacionChat, acepta: Boolean, pdf: com.example.consora_mobile.data.ArchivoGuardado?) {
        VotacionRepository(this).responder(invitacion.id, chatId, usuarioId, acepta, pdf)
        Toast.makeText(this, if (acepta) R.string.invitacion_aceptada else R.string.invitacion_rechazada, Toast.LENGTH_SHORT).show()
        cargarMensajes()
    }

    private fun aceptarInvitacion(invitacion: InvitacionChat) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invitacion_aceptar_titulo)
            .setMessage(R.string.invitacion_aceptar_mensaje)
            .setPositiveButton(R.string.invitacion_adjuntar_pdf) { _, _ ->
                invitacionAceptando = invitacion
                elegirPropuesta.launch(arrayOf("application/pdf"))
            }
            .setNeutralButton(R.string.invitacion_sin_pdf) { _, _ -> lifecycleScope.launch { responder(invitacion, true, null) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun rechazarInvitacion(invitacion: InvitacionChat) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invitacion_rechazar_titulo)
            .setPositiveButton(R.string.invitacion_rechazar) { _, _ -> lifecycleScope.launch { responder(invitacion, false, null) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Foto (se ve en el chat) o tarjeta con ícono y nombre del archivo; al tocarlos se abren. */
    private fun vistaAdjunto(m: Mensaje, mio: Boolean): View {
        val ruta = m.archivo!!
        val nombre = m.nombreArchivo ?: Archivos.nombreVisible(ruta)
        val dp = resources.displayMetrics.density
        val abrir = View.OnClickListener { lifecycleScope.launch { Archivos.abrir(this@ChatActivity, ruta, nombre) } }
        if (Archivos.esImagen(nombre)) {
            return ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((220 * dp).toInt(), (220 * dp).toInt()).apply { bottomMargin = (4 * dp).toInt() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = nombre
                setOnClickListener(abrir)
                lifecycleScope.launch { ConsorcioImagen.cargar(this@ChatActivity, ruta, 600)?.let(::setImageBitmap) }
            }
        }
        val icono = when (Archivos.tipo(nombre)) { "pdf" -> "📕"; "word" -> "📘"; "excel" -> "📗"; else -> "📄" }
        return TextView(this).apply {
            text = "$icono  $nombre"
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.consora_navy))
            setBackgroundResource(R.drawable.bg_input)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            maxWidth = (240 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (4 * dp).toInt() }
            setOnClickListener(abrir)
        }
    }

    /** Tarjeta de invitación a una votación de contratación (con botones si me toca responder). */
    private fun vistaInvitacion(inv: InvitacionChat): View {
        val dp = resources.displayMetrics.density
        fun texto(t: String, color: Int, bold: Boolean = false, sp: Float = 13f) = TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(ContextCompat.getColor(context, color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_input)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((250 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * dp).toInt() }

            addView(texto(getString(R.string.invitacion_titulo), R.color.consora_teal, bold = true, sp = 14f))
            addView(texto(getString(R.string.invitacion_detalle, inv.consorcio, Formatos.fechaHora(inv.fechaLimite)), R.color.consora_gray))

            when {
                inv.aceptacion == null && inv.abierta && inv.proveedorId == usuarioId -> {
                    val botones = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .apply { topMargin = (8 * dp).toInt() }
                    }
                    fun boton(t: Int, estilo: Int, accion: () -> Unit) =
                        MaterialButton(context, null, estilo).apply {
                            setText(t)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                .apply { marginEnd = (4 * dp).toInt() }
                            setOnClickListener { accion() }
                        }
                    botones.addView(boton(R.string.invitacion_aceptar, com.google.android.material.R.attr.materialButtonStyle) { aceptarInvitacion(inv) })
                    botones.addView(boton(R.string.invitacion_rechazar, com.google.android.material.R.attr.materialButtonOutlinedStyle) { rechazarInvitacion(inv) })
                    addView(botones)
                }
                inv.aceptacion == true -> addView(texto(
                    getString(if (inv.contratado) R.string.invitacion_estado_contratado else R.string.invitacion_estado_aceptada),
                    R.color.consora_teal, bold = true))
                inv.aceptacion == false -> addView(texto(getString(R.string.invitacion_estado_rechazada), R.color.consora_error, bold = true))
                !inv.abierta -> addView(texto(getString(R.string.invitacion_estado_cerrada), R.color.consora_gray, bold = true))
                else -> addView(texto(getString(R.string.invitacion_estado_esperando), R.color.consora_pendiente, bold = true))
            }
        }
    }

    /** Tarjeta de invitación a una reunión (con Aceptar / Rechazar si me invitaron a mí). */
    private fun vistaInvitacionReunion(inv: InvitacionReunionChat): View {
        val dp = resources.displayMetrics.density
        fun texto(t: String, color: Int, bold: Boolean = false, sp: Float = 13f) = TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(ContextCompat.getColor(context, color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_input)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((250 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * dp).toInt() }

            addView(texto(getString(R.string.reunion_invitacion_titulo), R.color.consora_teal, bold = true, sp = 14f))
            addView(texto(inv.titulo, R.color.consora_navy, bold = true).apply {
                if (inv.modalidad == com.example.consora_mobile.data.Reunion.VIRTUAL) {
                    val logo = ContextCompat.getDrawable(context, R.drawable.ic_google_meet)!!.mutate()
                    val alto = (textSize * 1.1f).toInt()
                    logo.setBounds(0, 0, (alto * 87.5f / 72f).toInt(), alto)
                    setCompoundDrawablesRelative(logo, null, null, null)
                    compoundDrawablePadding = (6 * dp).toInt()
                }
            })
            addView(texto(getString(
                R.string.reunion_invitacion_detalle, inv.consorcio, Formatos.fechaHora(inv.fechaInicio),
                if (inv.modalidad == com.example.consora_mobile.data.Reunion.VIRTUAL) getString(R.string.reunion_google_meet) else inv.lugar.orEmpty(),
            ), R.color.consora_gray))

            when {
                inv.respuesta == "pendiente" && inv.activa && inv.usuarioId == usuarioId -> {
                    val botones = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .apply { topMargin = (8 * dp).toInt() }
                    }
                    fun boton(t: Int, estilo: Int, acepta: Boolean) =
                        MaterialButton(context, null, estilo).apply {
                            setText(t)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                .apply { marginEnd = (4 * dp).toInt() }
                            setOnClickListener { responderReunion(inv, acepta) }
                        }
                    botones.addView(boton(R.string.invitacion_aceptar, com.google.android.material.R.attr.materialButtonStyle, true))
                    botones.addView(boton(R.string.invitacion_rechazar, com.google.android.material.R.attr.materialButtonOutlinedStyle, false))
                    addView(botones)
                }
                inv.respuesta == "acepta" -> addView(texto(getString(R.string.reunion_invitacion_aceptada), R.color.consora_teal, bold = true))
                inv.respuesta == "rechaza" -> addView(texto(getString(R.string.invitacion_estado_rechazada), R.color.consora_error, bold = true))
                inv.cancelada -> addView(texto(getString(R.string.reunion_invitacion_cancelada), R.color.consora_gray, bold = true))
                !inv.activa -> addView(texto(getString(R.string.reunion_invitacion_terminada), R.color.consora_gray, bold = true))
                else -> addView(texto(getString(R.string.invitacion_estado_esperando), R.color.consora_pendiente, bold = true))
            }
        }
    }

    private fun responderReunion(inv: InvitacionReunionChat, acepta: Boolean) {
        val hacer = {
            lifecycleScope.launch {
                ReunionRepository(this@ChatActivity).responder(inv.id, usuarioId, acepta)
                Toast.makeText(
                    this@ChatActivity,
                    if (acepta) R.string.reunion_aceptaste else R.string.reunion_rechazaste, Toast.LENGTH_SHORT,
                ).show()
                cargarMensajes()
            }
        }
        if (acepta) hacer() else MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invitacion_rechazar_titulo)
            .setPositiveButton(R.string.invitacion_rechazar) { _, _ -> hacer() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun enviar() {
        val texto = etMensaje.text.toString().trim()
        if (texto.isEmpty()) return
        etMensaje.text.clear()
        lifecycleScope.launch {
            agregar(repo.enviar(chatId, usuarioId, texto))
            scrollAlFinal()
        }
    }

    /** Agrega la burbuja del mensaje (y un separador si empieza un día nuevo). */
    private fun agregar(m: Mensaje) {
        val dia = m.enviadoEn.take(10)
        if (dia != ultimoDia) {
            lista.addView(separadorDia(Formatos.diaChat(m.enviadoEn)))
            ultimoDia = dia
        }

        val mio = m.usuarioId == usuarioId
        val item = LayoutInflater.from(this).inflate(R.layout.item_mensaje, lista, false)
        val bubble = item.findViewById<LinearLayout>(R.id.bubble)
        (bubble.layoutParams as LinearLayout.LayoutParams).gravity = if (mio) Gravity.END else Gravity.START
        bubble.setBackgroundResource(if (mio) R.drawable.bg_bubble_me else R.drawable.bg_bubble_other)
        val tvTexto = item.findViewById<TextView>(R.id.tvTexto).apply {
            text = m.texto
            visibility = if (m.texto.isBlank()) View.GONE else View.VISIBLE
            setTextColor(ContextCompat.getColor(context, if (mio) R.color.white else R.color.consora_navy))
        }
        if (m.archivo != null) bubble.addView(vistaAdjunto(m, mio), bubble.indexOfChild(tvTexto))
        if (m.invitacion != null) bubble.addView(vistaInvitacion(m.invitacion), bubble.indexOfChild(tvTexto) + 1)
        if (m.invitacionReunion != null) bubble.addView(vistaInvitacionReunion(m.invitacionReunion), bubble.indexOfChild(tvTexto) + 1)
        item.findViewById<TextView>(R.id.tvHora).apply {
            text = Formatos.hora(m.enviadoEn)
            setTextColor(ContextCompat.getColor(context, if (mio) R.color.consora_teal_light else R.color.consora_gray))
        }
        lista.addView(item)
    }

    private fun separadorDia(texto: String) = TextView(this).apply {
        text = texto
        setTextColor(ContextCompat.getColor(context, R.color.consora_gray))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_day_chip)
        val dp = resources.displayMetrics.density
        setPadding((10 * dp).toInt(), (3 * dp).toInt(), (10 * dp).toInt(), (3 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (10 * dp).toInt()
            bottomMargin = (6 * dp).toInt()
        }
    }

    private fun scrollAlFinal() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_NOMBRE = "nombre"
        private const val EXTRA_SUBTITULO = "subtitulo"
        private const val EXTRA_IMG = "img"

        fun intent(context: Context, chatId: Long, nombre: String, subtitulo: String, img: String?) =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_NOMBRE, nombre)
                .putExtra(EXTRA_SUBTITULO, subtitulo)
                .putExtra(EXTRA_IMG, img)
    }
}
