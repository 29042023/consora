package com.example.consora_mobile.ui

import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.Archivos
import com.example.consora_mobile.data.Carpeta
import com.example.consora_mobile.data.DatosPerfil
import com.example.consora_mobile.data.PerfilRepository
import com.example.consora_mobile.data.Suscripcion
import com.example.consora_mobile.data.SuscripcionRepository
import com.example.consora_mobile.data.Usuario
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pestaña "Cuenta": datos del usuario logueado, edición del perfil y cierre de sesión. */
class CuentaFragment : Fragment(R.layout.fragment_cuenta) {

    private val main get() = requireActivity() as MainActivity
    private val perfil by lazy { PerfilRepository(requireContext()) }
    private val dp get() = resources.displayMetrics.density

    /** Tarjeta "Mi perfil" (se arma por código, arriba del botón de cerrar sesión). */
    private var cardPerfil: LinearLayout? = null

    // Foto de perfil: selector de fotos del sistema (no necesita permisos)
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val u = main.usuario ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val foto = Archivos.guardar(requireContext(), uri, Carpeta.IMG_USUARIOS)
            perfil.cambiarFoto(u.id, foto.ruta, u.img)
            refrescar(R.string.perfil_foto_ok)
        }
    }

    // Título del administrador (PDF) en docs/titulos
    private val elegirTitulo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val u = main.usuario ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        Archivos.validar(requireContext(), uri, soloPdf = true)?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show(); return@registerForActivityResult
        }
        viewLifecycleOwner.lifecycleScope.launch {
            perfil.agregarTitulo(u.id, Archivos.guardar(requireContext(), uri, Carpeta.DOCS_TITULOS).ruta)
            refrescar(R.string.perfil_titulo_ok)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        main.usuario?.let { render(view, it) }
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener { confirmLogout() }
        // Tocando la foto (o las iniciales) se elige una nueva
        listOf(R.id.tvIniciales, R.id.ivFotoPerfil).forEach { id ->
            view.findViewById<View>(id).setOnClickListener {
                elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    /** Relee el usuario, vuelve a dibujar la pantalla y muestra el aviso. */
    private suspend fun refrescar(aviso: Int) {
        val u = main.refrescarUsuario() ?: return
        render(requireView(), u)
        Toast.makeText(requireContext(), aviso, Toast.LENGTH_SHORT).show()
    }

    /** Tarjeta "Mi perfil": editar datos, foto, contraseña, Marketplace, título y baja. */
    private fun renderPerfil(view: View, u: Usuario) {
        val logout = view.findViewById<View>(R.id.btnLogout)
        val contenedor = logout.parent as LinearLayout
        cardPerfil?.let { contenedor.removeView(it.parent as View) }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            addView(TextView(context, null, 0, R.style.ConsoraCardTitle).apply { setText(R.string.perfil_titulo) })
        }
        cardPerfil = card
        contenedor.addView(
            com.google.android.material.card.MaterialCardView(requireContext(), null, com.google.android.material.R.attr.materialCardViewOutlinedStyle).apply {
                radius = 16 * dp
                strokeColor = ContextCompat.getColor(context, R.color.consora_divider)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = (12 * dp).toInt() }
                addView(card)
            },
            contenedor.indexOfChild(logout),
        )

        fun accion(texto: String, color: Int = R.color.consora_navy, onClick: () -> Unit) = card.addView(TextView(requireContext()).apply {
            text = texto
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, color))
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick() }
        })

        accion("✏️  " + getString(R.string.perfil_editar)) { editarDatos(u) }
        accion("📷  " + getString(R.string.perfil_cambiar_foto)) {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (u.img != null) accion("🗑  " + getString(R.string.perfil_quitar_foto)) {
            viewLifecycleOwner.lifecycleScope.launch { perfil.cambiarFoto(u.id, null, u.img); refrescar(R.string.perfil_foto_quitada) }
        }
        accion("🔑  " + getString(R.string.perfil_cambiar_password)) { cambiarPassword(u) }
        accion("🏪  " + getString(R.string.perfil_marketplace)) { editarMarketplace(u) }

        // Título del administrador: verlo (para verificarlo), subir otro o eliminarlo
        if (u.administrador != null) viewLifecycleOwner.lifecycleScope.launch {
            val titulos = perfil.titulos(u.id)
            val actual = titulos.firstOrNull()
            if (actual != null) {
                accion("📕  " + getString(R.string.perfil_ver_titulo), R.color.consora_teal) {
                    viewLifecycleOwner.lifecycleScope.launch { Archivos.abrir(requireContext(), actual.titulo, "Titulo.pdf") }
                }
                accion("🗑  " + getString(R.string.perfil_borrar_titulo)) {
                    confirmar(getString(R.string.perfil_borrar_titulo_confirmar)) {
                        viewLifecycleOwner.lifecycleScope.launch { perfil.borrarTitulo(actual); refrescar(R.string.perfil_titulo_borrado) }
                    }
                }
            }
            accion("⬆️  " + getString(if (actual == null) R.string.perfil_subir_titulo else R.string.perfil_reemplazar_titulo)) {
                elegirTitulo.launch(arrayOf("application/pdf"))
            }
            accion("⛔  " + getString(R.string.perfil_baja), R.color.consora_error) { darDeBaja(u) }
        } else accion("⛔  " + getString(R.string.perfil_baja), R.color.consora_error) { darDeBaja(u) }
    }

    /** Campo de texto para los formularios del perfil. */
    private fun campo(hint: Int, valor: String?, tipo: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) =
        TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            setHint(hint)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * dp).toInt() }
            addView(TextInputEditText(context).apply { setText(valor); inputType = tipo })
        }

    private fun TextInputLayout.valor() = editText?.text?.toString()?.trim().orEmpty()

    private fun formulario(vararg campos: View) = android.widget.ScrollView(requireContext()).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            campos.forEach { addView(it) }
        })
    }

    /** Diálogo con formulario que no se cierra mientras [guardar] devuelva un error. */
    private fun dialogo(titulo: Int, vista: View, guardar: suspend () -> Int?) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton(R.string.evento_guardar, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val error = guardar()
                    if (error != null) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show() else dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun editarDatos(u: Usuario) {
        val nombre = campo(R.string.perfil_nombre, u.nameSurname)
        val mail = campo(R.string.account_mail, u.mail, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val phone = campo(R.string.account_phone, u.phone, InputType.TYPE_CLASS_PHONE)
        val dni = campo(R.string.account_dni, u.dni, InputType.TYPE_CLASS_NUMBER)
        val nacimiento = campo(R.string.perfil_nacimiento_formato, u.dateNacimiento?.let(::formatDate), InputType.TYPE_CLASS_DATETIME)
        val admin = u.administrador
        val matricula = admin?.let { campo(R.string.account_matricula, it.matricula, InputType.TYPE_CLASS_TEXT) }
        val alta = admin?.let { campo(R.string.perfil_alta_formato, it.fechaAltaMatricula?.let(::formatDate), InputType.TYPE_CLASS_DATETIME) }
        val ente = admin?.let { campo(R.string.account_ente, it.enteOtorgante) }

        /** "dd/MM/yyyy" -> "yyyy-MM-dd"; vacío -> null; inválida -> "" (error) */
        fun iso(texto: String): String? = if (texto.isBlank()) null
            else runCatching { LocalDate.parse(texto, DATE_FORMAT).toString() }.getOrDefault("")

        dialogo(R.string.perfil_editar, formulario(*listOfNotNull(nombre, mail, phone, dni, nacimiento, matricula, alta, ente).toTypedArray())) {
            val fechaNac = iso(nacimiento.valor())
            val fechaAlta = alta?.let { iso(it.valor()) }
            when {
                fechaNac == "" || fechaAlta == "" -> R.string.perfil_error_fecha
                fechaNac != null && LocalDate.parse(fechaNac) >= LocalDate.now() -> R.string.perfil_error_fecha
                matricula != null && matricula.valor().isEmpty() -> R.string.perfil_error_matricula_vacia
                else -> {
                    val error = perfil.guardarDatos(u.id, DatosPerfil(
                        nombre.valor(), mail.valor(), phone.valor().ifEmpty { null }, dni.valor().ifEmpty { null }, fechaNac,
                        matricula?.valor(), fechaAlta, ente?.valor()?.ifEmpty { null },
                    ))
                    if (error == null) refrescar(R.string.perfil_datos_ok)
                    error
                }
            }
        }
    }

    private fun cambiarPassword(u: Usuario) {
        val tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val actual = campo(R.string.perfil_password_actual, null, tipo)
        val nueva = campo(R.string.perfil_password_nueva, null, tipo)
        val repetida = campo(R.string.perfil_password_repetir, null, tipo)
        dialogo(R.string.perfil_cambiar_password, formulario(actual, nueva, repetida)) {
            val error = perfil.cambiarPassword(u.id, actual.valor(), nueva.valor(), repetida.valor())
            if (error == null) Toast.makeText(requireContext(), R.string.perfil_password_ok, Toast.LENGTH_SHORT).show()
            error
        }
    }

    private fun editarMarketplace(u: Usuario) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (descripcion, link) = perfil.marketplace(u.id) ?: (null to null)
            val tDescripcion = campo(R.string.perfil_marketplace_descripcion, descripcion,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val tLink = campo(R.string.perfil_marketplace_link, link, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            dialogo(R.string.perfil_marketplace, formulario(tDescripcion, tLink)) {
                val web = tLink.valor().ifEmpty { null }
                if (web != null && !android.util.Patterns.WEB_URL.matcher(web).matches()) R.string.perfil_error_link
                else {
                    perfil.guardarMarketplace(u.id, tDescripcion.valor().ifEmpty { null }, web)
                    Toast.makeText(requireContext(), R.string.perfil_marketplace_ok, Toast.LENGTH_SHORT).show()
                    null
                }
            }
        }
    }

    /** Con consorcios a cargo no se puede: primero tiene que desasociarse (igual que en la web). */
    private fun darDeBaja(u: Usuario) {
        viewLifecycleOwner.lifecycleScope.launch {
            val consorcios = perfil.consorciosACargo(u.id)
            if (consorcios.isNotEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.perfil_baja_no_titulo)
                    .setMessage(getString(R.string.perfil_baja_no_mensaje) + "\n\n" + consorcios.joinToString("\n") { "• $it" })
                    .setPositiveButton(R.string.perfil_entendido, null)
                    .show()
                return@launch
            }
            val password = campo(R.string.perfil_baja_password, null, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val aviso = TextView(requireContext()).apply { setText(R.string.perfil_baja_aviso) }
            dialogo(R.string.perfil_baja, formulario(aviso, password)) {
                if (perfil.darDeBaja(u.id, password.valor())) {
                    main.logout()
                    null
                } else R.string.perfil_error_password_actual
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

    private fun render(view: View, u: Usuario) {
        mostrarAvatar(
            viewLifecycleOwner.lifecycleScope, view.findViewById(R.id.tvIniciales), view.findViewById(R.id.ivFotoPerfil),
            u.nameSurname, u.img,
        )
        view.findViewById<TextView>(R.id.tvNombre).text = u.nameSurname
        view.findViewById<TextView>(R.id.tvRol).text = u.rol

        view.fillCard(
            R.id.cardPersonal,
            R.string.account_mail to u.mail,
            R.string.account_dni to u.dni,
            R.string.account_phone to u.phone,
            R.string.account_birth to u.dateNacimiento?.let(::formatDate),
        )
        view.fillCard(
            R.id.cardCuenta,
            R.string.account_status to u.estadoCuenta,
            R.string.account_consorcios to u.cantidadConsorcios.toString(),
            R.string.account_member_since to formatDateTime(u.createdAt),
        )

        renderFacturacion(view, u)
        renderPerfil(view, u)

        val admin = u.administrador
        view.findViewById<View>(R.id.cardAdminContainer).visibility = if (admin != null) View.VISIBLE else View.GONE
        if (admin != null) {
            view.fillCard(
                R.id.cardAdmin,
                R.string.account_matricula to admin.matricula,
                R.string.account_matricula_alta to admin.fechaAltaMatricula?.let(::formatDate),
                R.string.account_ente to admin.enteOtorgante,
            )
        }
    }

    /** Tarjeta "Facturación": plan, asistentes, IVA y total a pagar a CONSORA. */
    private fun renderFacturacion(view: View, u: Usuario) {
        val suscripcion = SuscripcionRepository.para(u)
        val card = view.findViewById<View>(R.id.cardFacturacionContainer)
        card.visibility = if (suscripcion == null) View.GONE else View.VISIBLE
        if (suscripcion == null) return

        view.findViewById<TextView>(R.id.tvFactPlan).text = getString(R.string.facturacion_plan, suscripcion.plan)
        view.findViewById<TextView>(R.id.tvFactEstado).apply {
            setText(if (suscripcion.pagada) R.string.facturacion_pagada else R.string.facturacion_pendiente)
            setBackgroundResource(if (suscripcion.pagada) R.drawable.bg_chip_pagada else R.drawable.bg_chip_pendiente)
            setTextColor(ContextCompat.getColor(context, if (suscripcion.pagada) R.color.consora_teal else R.color.consora_pendiente))
        }

        val lineas = view.findViewById<LinearLayout>(R.id.llFactLineas)
        lineas.removeAllViews()
        for (l in suscripcion.lineas) {
            val row = layoutInflater.inflate(R.layout.item_linea_factura, lineas, false)
            row.findViewById<TextView>(R.id.tvConcepto).text = l.concepto
            row.findViewById<TextView>(R.id.tvDetalle).apply {
                text = l.detalle
                visibility = if (l.detalle == null) View.GONE else View.VISIBLE
            }
            row.findViewById<TextView>(R.id.tvImporte).text = Formatos.moneda(l.importe)
            lineas.addView(row)
        }

        view.findViewById<TextView>(R.id.tvFactTotal).text = Formatos.moneda(suscripcion.total)
        val vence = Formatos.fecha(suscripcion.proximoVencimiento)
        view.findViewById<TextView>(R.id.tvFactVencimiento).text = getString(
            if (suscripcion.pagada) R.string.facturacion_pagada_el else R.string.facturacion_vence, vence,
        )

        // Botón Pagar: solo si el mes está pendiente
        view.findViewById<Button>(R.id.btnPagar).apply {
            visibility = if (suscripcion.pagada) View.GONE else View.VISIBLE
            text = getString(R.string.facturacion_pagar, Formatos.moneda(suscripcion.total))
            isEnabled = true
            setOnClickListener { confirmarPago(view, u, suscripcion, this) }
        }
    }

    /** Hoja para elegir el medio de pago (Mercado Pago o MODO). El pago es simulado. */
    private fun confirmarPago(view: View, u: Usuario, s: Suscripcion, boton: Button) {
        val sheet = BottomSheetDialog(requireContext())
        val contenido = layoutInflater.inflate(R.layout.sheet_pago, null)
        contenido.findViewById<TextView>(R.id.tvPagoResumen).text = getString(R.string.pago_resumen, s.periodo)
        contenido.findViewById<TextView>(R.id.tvPagoTotal).text = Formatos.moneda(s.total)

        fun pagarCon(medio: Int) {
            sheet.dismiss()
            val nombre = getString(medio)
            boton.isEnabled = false
            boton.text = getString(R.string.pago_procesando, nombre)
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1500)
                SuscripcionRepository.registrarPago(u)
                Toast.makeText(requireContext(), getString(R.string.pago_ok, nombre), Toast.LENGTH_SHORT).show()
                renderFacturacion(view, u)
        renderPerfil(view, u)
            }
        }
        contenido.findViewById<View>(R.id.opcionMercadoPago).setOnClickListener { pagarCon(R.string.pago_mercado_pago) }
        contenido.findViewById<View>(R.id.opcionModo).setOnClickListener { pagarCon(R.string.pago_modo) }

        sheet.setContentView(contenido)
        sheet.show()
    }

    /** Reemplaza las filas de la tarjeta (dejando el título, que es el primer hijo). */
    private fun View.fillCard(cardId: Int, vararg rows: Pair<Int, String?>) {
        val card = findViewById<LinearLayout>(cardId)
        card.removeViews(1, card.childCount - 1)
        val inflater = LayoutInflater.from(context)
        for ((label, value) in rows) {
            val row = inflater.inflate(R.layout.item_dato, card, false)
            row.findViewById<TextView>(R.id.tvLabel).setText(label)
            row.findViewById<TextView>(R.id.tvValue).text = value ?: getString(R.string.account_empty)
            card.addView(row)
        }
    }

    private fun formatDate(iso: String): String =
        runCatching { LocalDate.parse(iso).format(DATE_FORMAT) }.getOrDefault(iso)

    /** SQLite guarda "yyyy-MM-dd HH:mm:ss"; se muestra solo la fecha. */
    private fun formatDateTime(value: String): String = formatDate(value.take(10))

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_logout)
            .setMessage(R.string.account_logout_confirm)
            .setPositiveButton(R.string.account_logout) { _, _ -> main.logout() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
