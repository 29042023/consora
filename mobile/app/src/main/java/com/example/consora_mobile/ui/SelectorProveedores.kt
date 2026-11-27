package com.example.consora_mobile.ui

import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.R
import com.example.consora_mobile.data.ProveedorInvitable
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lista de proveedores con su foto (o iniciales) y un buscador.
 * [multiple] = se marcan varios con casillas; si no, al tocar uno se elige y se cierra.
 */
fun Fragment.elegirProveedores(
    titulo: Int,
    proveedores: List<ProveedorInvitable>,
    elegidos: Set<Long>,
    multiple: Boolean,
    alElegir: (Set<Long>) -> Unit,
) {
    val dp = resources.displayMetrics.density
    val marcados = elegidos.toMutableSet()
    val contenedor = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
    }
    val buscador = layoutInflater.inflate(R.layout.view_buscador, contenedor, false)
    buscador.findViewById<View>(R.id.btnFiltros).visibility = View.GONE
    val etBuscar = buscador.findViewById<EditText>(R.id.etBuscar).apply { setHint(R.string.votacion_buscar_proveedor) }
    contenedor.addView(buscador)
    val lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    contenedor.addView(android.widget.ScrollView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (360 * dp).toInt())
        addView(lista)
    })

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setView(contenedor)
        .apply { if (multiple) setPositiveButton(R.string.ok) { _, _ -> alElegir(marcados) } }
        .setNegativeButton(R.string.cancel, null)
        .create()

    // Cada fila: foto/iniciales, nombre y casilla (reutiliza el ítem del Marketplace)
    val filas = proveedores.map { p ->
        layoutInflater.inflate(R.layout.item_proveedor, lista, false).apply {
            mostrarAvatar(viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar), p.nombre, p.img)
            findViewById<TextView>(R.id.tvNombre).text = p.nombre
            findViewById<View>(R.id.tvServicio)?.visibility = View.GONE
            listOf(R.id.ivEstrella, R.id.tvEstrellas).forEach { findViewById<View>(it)?.visibility = View.GONE }
            val check = CheckBox(context).apply {
                isChecked = p.id in marcados
                isClickable = false
                visibility = if (multiple) View.VISIBLE else View.GONE
            }
            (this as LinearLayout).addView(check)
            setOnClickListener {
                if (!multiple) { dialog.dismiss(); alElegir(setOf(p.id)); return@setOnClickListener }
                if (!marcados.remove(p.id)) marcados += p.id
                check.isChecked = p.id in marcados
            }
            lista.addView(this)
        } to p.nombre
    }
    etBuscar.doAfterTextChanged { q ->
        val texto = q?.toString()?.trim().orEmpty()
        filas.forEach { (fila, nombre) -> fila.visibility = if (nombre.contains(texto, ignoreCase = true)) View.VISIBLE else View.GONE }
    }
    dialog.show()
}
