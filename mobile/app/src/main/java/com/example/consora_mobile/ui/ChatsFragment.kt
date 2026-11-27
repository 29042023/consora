package com.example.consora_mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.ChatActivity
import com.example.consora_mobile.MainActivity
import com.example.consora_mobile.R
import com.example.consora_mobile.data.ChatRepository
import com.example.consora_mobile.data.ChatResumen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Pestaña Chats: conversaciones del usuario, la más reciente primero. */
class ChatsFragment : Fragment() {

    private val main get() = requireActivity() as MainActivity
    private val repo get() = ChatRepository(requireContext())

    private var chats: List<ChatResumen> = emptyList()
    private lateinit var lista: LinearLayout
    private lateinit var etBuscar: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateSection(inflater, container, getString(R.string.nav_chats), getString(R.string.chats_subtitulo))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.sectionContent)
        val buscador = layoutInflater.inflate(R.layout.view_buscador, content, false)
        content.addView(buscador)
        lista = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lista)

        etBuscar = buscador.findViewById(R.id.etBuscar)
        etBuscar.setHint(R.string.chats_buscar)
        etBuscar.doAfterTextChanged { mostrar(it?.toString().orEmpty()) }
        // En Chats no hay filtros, solo la búsqueda
        buscador.findViewById<View>(R.id.btnFiltros).visibility = View.GONE

        view.showFab(R.string.chats_nuevo) { nuevoChat() }
    }

    // Se recarga en onResume para que al volver de una conversación se vea el último mensaje
    override fun onResume() {
        super.onResume()
        val usuario = main.usuario ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            chats = repo.listByUsuario(usuario.id)
            mostrar(etBuscar.text.toString())
        }
    }

    private fun mostrar(busqueda: String) {
        val q = busqueda.trim()
        val filtrados = chats.filter {
            q.isEmpty() || it.otroNombre.contains(q, ignoreCase = true) ||
                it.ultimoMensaje?.contains(q, ignoreCase = true) == true
        }
        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            lista.addEmptyState(
                R.drawable.ic_nav_chats,
                getString(R.string.chats_vacio_titulo),
                getString(if (q.isEmpty()) R.string.chats_vacio_mensaje else R.string.chats_sin_resultados),
            )
            return
        }
        filtrados.forEach { lista.addView(itemChat(lista, it)) }
    }

    private fun itemChat(parent: ViewGroup, chat: ChatResumen): View =
        layoutInflater.inflate(R.layout.item_chat, parent, false).apply {
            mostrarAvatar(
                viewLifecycleOwner.lifecycleScope, findViewById(R.id.tvAvatar), findViewById(R.id.ivAvatar),
                chat.otroNombre, chat.otroImg,
            )
            findViewById<TextView>(R.id.tvNombre).text = chat.otroNombre
            findViewById<TextView>(R.id.tvRol).text = chat.otroSubtitulo
            findViewById<TextView>(R.id.tvHora).text = chat.ultimoEnviadoEn?.let(Formatos::horaChat).orEmpty()
            findViewById<TextView>(R.id.tvUltimo).text = when {
                chat.ultimoMensaje == null -> getString(R.string.chats_sin_mensajes)
                chat.ultimoEsMio -> getString(R.string.chats_vos, chat.ultimoMensaje)
                else -> chat.ultimoMensaje
            }
            setOnClickListener {
                startActivity(ChatActivity.intent(requireContext(), chat.id, chat.otroNombre, chat.otroSubtitulo, chat.otroImg))
            }
        }

    /** Elige un contacto y abre el chat con esa persona (lo crea si no existía). */
    private fun nuevoChat() {
        val usuario = main.usuario ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val contactos = repo.contactos(usuario.id)
            val etiquetas = contactos.map { "${it.nombre}\n${it.subtitulo}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chats_elegir_contacto)
                .setItems(etiquetas) { _, i ->
                    val contacto = contactos[i]
                    viewLifecycleOwner.lifecycleScope.launch {
                        val chatId = repo.obtenerOCrearIndividual(usuario.id, contacto.id)
                        startActivity(ChatActivity.intent(requireContext(), chatId, contacto.nombre, contacto.subtitulo, contacto.img))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
