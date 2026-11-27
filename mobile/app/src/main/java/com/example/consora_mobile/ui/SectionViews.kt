package com.example.consora_mobile.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/**
 * Infla fragment_section.xml (encabezado azul + hoja blanca) con el título indicado.
 * El avatar del encabezado muestra las iniciales del usuario y abre "Cuenta".
 */
fun Fragment.inflateSection(
    inflater: LayoutInflater,
    container: ViewGroup?,
    title: CharSequence,
    subtitle: CharSequence,
): View = inflater.inflate(R.layout.fragment_section, container, false).apply {
    findViewById<TextView>(R.id.tvSectionTitle).text = title
    findViewById<TextView>(R.id.tvSectionSubtitle).text = subtitle

    val main = requireActivity() as MainActivity
    val tvAvatar = findViewById<TextView>(R.id.tvHeaderAvatar)
    tvAvatar.setOnClickListener { main.showCuenta() }
    main.usuario?.let { u ->
        mostrarAvatar(lifecycleScope, tvAvatar, findViewById(R.id.ivHeaderAvatar), u.nameSurname, u.img)
    }
}

/** Muestra el botón flotante de la sección con el texto indicado. */
fun View.showFab(@StringRes text: Int, onClick: () -> Unit) {
    findViewById<ExtendedFloatingActionButton>(R.id.fab).apply {
        setText(text)
        visibility = View.VISIBLE
        setOnClickListener { onClick() }
    }
}

/** Agrega al contenedor un estado vacío con ícono, título y mensaje. */
fun ViewGroup.addEmptyState(@DrawableRes icon: Int, title: CharSequence, message: CharSequence) {
    val view = LayoutInflater.from(context).inflate(R.layout.view_empty, this, false)
    view.findViewById<ImageView>(R.id.ivEmptyIcon).setImageResource(icon)
    view.findViewById<TextView>(R.id.tvEmptyTitle).text = title
    view.findViewById<TextView>(R.id.tvEmptyMessage).text = message
    addView(view)
}
