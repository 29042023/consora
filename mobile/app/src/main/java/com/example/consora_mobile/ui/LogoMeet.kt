package com.example.consora_mobile.ui

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.consora_mobile.R

/** Pone (o saca) el logo de Google Meet a la izquierda del texto, del alto de la letra. */
fun TextView.logoMeet(mostrar: Boolean = true) {
    if (!mostrar) {
        setCompoundDrawablesRelative(null, null, null, null)
        return
    }
    val logo = ContextCompat.getDrawable(context, R.drawable.ic_google_meet)!!.mutate()
    val alto = (textSize * 1.1f).toInt()
    logo.setBounds(0, 0, (alto * 87.5f / 72f).toInt(), alto)
    setCompoundDrawablesRelative(logo, null, null, null)
    compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
}
