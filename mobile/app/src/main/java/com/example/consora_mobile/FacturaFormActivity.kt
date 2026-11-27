package com.example.consora_mobile

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.data.Carpeta
import com.example.consora_mobile.data.Consorcio
import com.example.consora_mobile.data.ConsorcioRepository
import com.example.consora_mobile.data.Deuda
import com.example.consora_mobile.data.FacturaRepository
import com.example.consora_mobile.data.SessionManager
import com.example.consora_mobile.ui.Formatos
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formulario para cargar una factura o comprobante de un consorcio.
 * Más adelante, al adjuntar el PDF se completarán los campos automáticamente.
 */
class FacturaFormActivity : AppCompatActivity() {

    private var consorcios: List<Consorcio> = emptyList()
    private var consorcioSeleccionado: Consorcio? = null
    private var deudaSeleccionada: Deuda? = null
    private var fecha: LocalDate = LocalDate.now()
    private var archivoUri: Uri? = null

    private lateinit var tilConsorcio: TextInputLayout
    private lateinit var acConsorcio: MaterialAutoCompleteTextView
    private lateinit var tilDeuda: TextInputLayout
    private lateinit var acDeuda: MaterialAutoCompleteTextView
    private lateinit var tilProveedor: TextInputLayout
    private lateinit var tilNro: TextInputLayout
    private lateinit var tilImporte: TextInputLayout
    private lateinit var etFecha: TextInputEditText
    private lateinit var tvArchivo: TextView
    private lateinit var btnGuardar: Button

    private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            archivoUri = uri
            tvArchivo.text = nombreArchivo(uri)
            tvArchivo.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_factura_form)
        val root = findViewById<View>(R.id.main)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = false
        val form = findViewById<View>(R.id.formContent)
        val padding = form.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            // ime() = teclado: así el botón Guardar no queda tapado al escribir
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            form.setPadding(form.paddingLeft, form.paddingTop, form.paddingRight, padding + bars.bottom)
            insets
        }

        tilConsorcio = findViewById(R.id.tilConsorcio)
        acConsorcio = findViewById(R.id.acConsorcio)
        tilDeuda = findViewById(R.id.tilDeuda)
        acDeuda = findViewById(R.id.acDeuda)
        tilProveedor = findViewById(R.id.tilProveedor)
        tilNro = findViewById(R.id.tilNro)
        tilImporte = findViewById(R.id.tilImporte)
        etFecha = findViewById(R.id.etFecha)
        tvArchivo = findViewById(R.id.tvArchivo)
        btnGuardar = findViewById(R.id.btnGuardar)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        etFecha.setText(Formatos.fecha(fecha))
        etFecha.setOnClickListener { elegirFecha() }
        findViewById<View>(R.id.btnAdjuntar).setOnClickListener {
            elegirArchivo.launch(arrayOf("application/pdf", "image/*"))
        }
        btnGuardar.setOnClickListener { guardar() }

        cargarConsorcios(intent.getLongExtra(EXTRA_CONSORCIO_ID, -1L))
    }

    /** Carga las deudas pendientes (sin factura) del consorcio elegido. */
    private fun cargarDeudas(consorcio: Consorcio) {
        deudaSeleccionada = null
        acDeuda.setText("", false)
        lifecycleScope.launch {
            val deudas = FacturaRepository(this@FacturaFormActivity).deudasPendientes(consorcio.id)
            tilDeuda.isEnabled = deudas.isNotEmpty()
            tilDeuda.helperText = if (deudas.isEmpty()) getString(R.string.factura_sin_deudas) else null
            acDeuda.setAdapter(
                ArrayAdapter(this@FacturaFormActivity, android.R.layout.simple_list_item_1, deudas.map(::textoDeuda))
            )
            acDeuda.setOnItemClickListener { _, _, position, _ ->
                val deuda = deudas[position]
                deudaSeleccionada = deuda
                tilDeuda.error = null
                // En el campo se muestra solo la primera línea (la lista muestra también importe y vencimiento)
                acDeuda.setText("${deuda.tipo} · ${deuda.descripcion}", false)
                // Se completan proveedor e importe con los datos de la deuda, si están vacíos
                tilProveedor.editText?.let { if (it.text.isNullOrBlank()) it.setText(deuda.proveedor) }
                tilImporte.editText?.let { if (it.text.isNullOrBlank()) it.setText(formatoImporte(deuda.importe)) }
            }
        }
    }

    private fun textoDeuda(d: Deuda): String = buildString {
        append("${d.tipo} · ${d.descripcion}\n${Formatos.moneda(d.importe)}")
        d.vencimiento?.let { append(" · ").append(getString(R.string.factura_vence, Formatos.fecha(it))) }
    }

    /** 54200.0 -> "54200"; 96350.5 -> "96350.5" */
    private fun formatoImporte(importe: Double): String =
        if (importe % 1.0 == 0.0) importe.toLong().toString() else importe.toString()

    private fun cargarConsorcios(preseleccionado: Long) {
        val usuarioId = SessionManager(this).usuarioId ?: return finish()
        lifecycleScope.launch {
            // Consorcios del administrador, o los habilitados si es asistente
            val usuario = com.example.consora_mobile.data.UsuarioRepository(this@FacturaFormActivity).getById(usuarioId)
            consorcios = if (usuario != null) ConsorcioRepository(this@FacturaFormActivity).listarVisibles(usuario) else emptyList()
            if (consorcios.isEmpty()) {
                Toast.makeText(this@FacturaFormActivity, R.string.factura_sin_consorcios, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            acConsorcio.setAdapter(
                ArrayAdapter(this@FacturaFormActivity, android.R.layout.simple_list_item_1, consorcios.map { it.nombre })
            )
            acConsorcio.setOnItemClickListener { _, _, position, _ ->
                consorcioSeleccionado = consorcios[position]
                tilConsorcio.error = null
                cargarDeudas(consorcios[position])
            }
            val inicial = consorcios.firstOrNull { it.id == preseleccionado } ?: consorcios.singleOrNull()
            if (inicial != null) {
                consorcioSeleccionado = inicial
                acConsorcio.setText(inicial.nombre, false)
                cargarDeudas(inicial)
            } else {
                // Hasta elegir un consorcio no se puede elegir la deuda
                tilDeuda.isEnabled = false
            }
        }
    }

    private fun elegirFecha() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.factura_fecha)
            .setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            etFecha.setText(Formatos.fecha(fecha))
        }
        picker.show(supportFragmentManager, "fecha")
    }

    private fun guardar() {
        listOf(tilConsorcio, tilDeuda, tilProveedor, tilNro, tilImporte).forEach { it.error = null }

        val consorcio = consorcioSeleccionado
        val deuda = deudaSeleccionada
        val proveedor = tilProveedor.editText?.text?.toString()?.trim().orEmpty()
        val nro = tilNro.editText?.text?.toString()?.trim().orEmpty()
        val importe = tilImporte.editText?.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

        var ok = true
        if (consorcio == null) { tilConsorcio.error = getString(R.string.error_requerido); ok = false }
        if (deuda == null) { tilDeuda.error = getString(R.string.error_requerido); ok = false }
        if (proveedor.isEmpty()) { tilProveedor.error = getString(R.string.error_requerido); ok = false }
        if (nro.isEmpty()) { tilNro.error = getString(R.string.error_requerido); ok = false }
        if (importe == null || importe <= 0) { tilImporte.error = getString(R.string.error_importe); ok = false }
        if (!ok || consorcio == null || deuda == null || importe == null) return

        val usuarioId = SessionManager(this).usuarioId ?: return
        btnGuardar.isEnabled = false
        lifecycleScope.launch {
            val archivo = archivoUri?.let { copiarArchivo(it) }
            FacturaRepository(this@FacturaFormActivity).insert(
                consorcio.id, deuda.id, usuarioId, proveedor, nro, fecha.toString(), importe, archivo,
            )
            Toast.makeText(this@FacturaFormActivity, R.string.factura_guardada, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    /** Copia el archivo elegido al almacenamiento interno de la app y devuelve su ruta. */
    private suspend fun copiarArchivo(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = Carpeta.DOCS_FACTURAS.dir(this@FacturaFormActivity)
            val destino = File(dir, "${System.currentTimeMillis()}_${nombreArchivo(uri)}")
            contentResolver.openInputStream(uri)!!.use { input ->
                destino.outputStream().use { input.copyTo(it) }
            }
            destino.absolutePath
        }.getOrNull()
    }

    private fun nombreArchivo(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "comprobante"

    companion object {
        const val EXTRA_CONSORCIO_ID = "consorcio_id"
    }
}
