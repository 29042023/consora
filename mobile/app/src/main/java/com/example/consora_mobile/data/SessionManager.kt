package com.example.consora_mobile.data

import android.content.Context

/** Recuerda en el dispositivo qué usuario inició sesión. */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("consora_session", Context.MODE_PRIVATE)

    val usuarioId: Long?
        get() = prefs.getLong(KEY_USUARIO_ID, -1L).takeIf { it != -1L }

    val isLoggedIn: Boolean
        get() = usuarioId != null

    fun save(usuarioId: Long) {
        prefs.edit().putLong(KEY_USUARIO_ID, usuarioId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_USUARIO_ID = "usuario_id"
    }
}
