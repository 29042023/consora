package com.example.consora_mobile.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.ChatActivity
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.ChatRepository
import kotlinx.coroutines.launch

// Piezas comunes de los perfiles estilo Instagram (fragment_perfil.xml)

/** Número + etiqueta, como "Posts" / "Followers" en Instagram. Sin datos todavía: se muestra "–". */
fun Fragment.statVacio(parent: ViewGroup, etiqueta: String) = stat(parent, null, etiqueta)

/** Número + etiqueta; si [valor] es null se muestra "–" en gris. */
fun Fragment.stat(parent: ViewGroup, valor: String?, etiqueta: String) = LinearLayout(parent.context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    addView(TextView(context).apply {
        text = valor ?: getString(R.string.perfil_sin_dato)
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, if (valor == null) R.color.consora_gray else R.color.consora_navy))
    })
    addView(TextView(context).apply {
        text = etiqueta
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, R.color.consora_navy))
    })
}

/** Abre (o crea) el chat individual entre el usuario logueado y otra persona. */
fun Fragment.abrirChatCon(otroId: Long, nombre: String, subtitulo: String, img: String?) {
    val usuario = (requireActivity() as MainActivity).usuario ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        val chatId = ChatRepository(requireContext()).obtenerOCrearIndividual(usuario.id, otroId)
        startActivity(ChatActivity.intent(requireContext(), chatId, nombre, subtitulo, img))
    }
}
