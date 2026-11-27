package com.example.consora_mobile.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.consora_mobile.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Tarjetas, textos y botones para las pantallas de detalle que se arman por código. */
class Tarjetas(private val context: Context, private val content: LinearLayout) {

    private val dp = context.resources.displayMetrics.density

    /** Tarjeta con título opcional; devuelve el contenedor donde agregar filas. */
    fun tarjeta(titulo: Int? = null): LinearLayout {
        val cuerpo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            if (titulo != null) addView(TextView(context, null, 0, R.style.ConsoraCardTitle).apply { setText(titulo) })
        }
        content.addView(MaterialCardView(context, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle).apply {
            radius = 16 * dp
            strokeColor = ContextCompat.getColor(context, R.color.consora_divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (12 * dp).toInt() }
            addView(cuerpo)
        })
        return cuerpo
    }

    fun texto(t: String, color: Int, bold: Boolean = false, sp: Float = 14f) = TextView(context).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(ContextCompat.getColor(context, color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun margen(v: TextView, top: Int) = v.apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (top * dp).toInt() }
    }

    /** Fila "etiqueta ........ valor". */
    fun fila(etiqueta: String, valor: String, colorValor: Int = R.color.consora_navy) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        addView(texto(etiqueta, R.color.consora_gray).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(texto(valor, colorValor, bold = true))
    }

    fun boton(texto: Int, outlined: Boolean = false, peso: Boolean = false, accion: () -> Unit) =
        MaterialButton(context, null,
            if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle,
        ).apply {
            setText(texto)
            layoutParams = (if (peso) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                .apply { topMargin = (6 * dp).toInt(); if (peso) marginEnd = (6 * dp).toInt() }
            setOnClickListener { accion() }
        }
}
