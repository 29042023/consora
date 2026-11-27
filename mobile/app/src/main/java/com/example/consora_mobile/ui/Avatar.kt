package com.example.consora_mobile.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

/**
 * Muestra el avatar de una persona: sus iniciales y, si tiene imagen de perfil
 * ("asset:..." o ruta de archivo), la imagen encima.
 */
fun mostrarAvatar(scope: LifecycleCoroutineScope, tv: TextView, iv: ImageView, nombre: String, img: String?) {
    tv.text = Formatos.iniciales(nombre)
    iv.visibility = View.GONE
    if (img == null) return
    scope.launch {
        val bitmap = ConsorcioImagen.cargar(iv.context, img, 256) ?: return@launch
        iv.setImageBitmap(bitmap)
        iv.visibility = View.VISIBLE
    }
}
