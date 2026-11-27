package com.example.consora_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.data.SessionManager
import com.example.consora_mobile.data.Usuario
import com.example.consora_mobile.data.UsuarioRepository
import com.example.consora_mobile.ui.AgendaFragment
import com.example.consora_mobile.ui.AsistentePerfilFragment
import com.example.consora_mobile.ui.AsistentesFragment
import com.example.consora_mobile.ui.ChatsFragment
import com.example.consora_mobile.ui.ConsorciosFragment
import com.example.consora_mobile.ui.CuentaFragment
import com.example.consora_mobile.ui.MarketplaceFragment
import com.example.consora_mobile.ui.ProveedorPerfilFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/** Pantalla principal (con sesión iniciada): menú inferior + pestaña seleccionada. */
class MainActivity : AppCompatActivity() {

    /** Usuario logueado; las pestañas lo leen desde acá. */
    var usuario: Usuario? = null
        private set

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.main)
        // Íconos de la barra de estado en blanco sobre el fondo azul
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // El BottomNavigationView ya agrega el espacio de la barra de navegación
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        // Volver a tocar la pestaña actual no hace nada
        bottomNav.setOnItemReselectedListener { }

        val usuarioId = SessionManager(this).usuarioId
        if (usuarioId == null) {
            logout()
            return
        }

        lifecycleScope.launch {
            val u = UsuarioRepository(this@MainActivity).getById(usuarioId)
            if (u == null) {
                logout()
                return@launch
            }
            usuario = u
            armarMenu(u)
            // Asignar la pestaña inicial no dispara el listener: hay que cargarla a mano
            if (savedInstanceState == null) bottomNav.selectedItemId = bottomNav.menu.getItem(0).itemId
            // Al recrear la actividad, se vuelve a cargar la pestaña con el usuario ya disponible
            showTab(bottomNav.selectedItemId)
        }
    }

    /**
     * Pestañas según el rol (máximo 5, el límite del menú inferior):
     * administrador: Consorcios, Marketplace, Agenda, Chats, Asistentes;
     * asistente: Inicio, Edificios, Agenda, Chats, Marketplace;
     * propietario: Inicio, Mi edificio, Agenda, Chats, Marketplace;
     * proveedor: Inicio, Reuniones, Chats, Marketplace.
     */
    private fun armarMenu(u: Usuario) {
        data class Tab(val id: Int, val titulo: Int, val icono: Int)
        val inicio = Tab(R.id.nav_inicio, R.string.nav_inicio, R.drawable.ic_nav_inicio)
        val consorcios = Tab(R.id.nav_consorcios, when {
            u.esAsistente -> R.string.nav_edificios
            u.esPropietario -> R.string.nav_mi_edificio
            else -> R.string.nav_consorcios
        }, R.drawable.ic_nav_consorcios)
        val marketplace = Tab(R.id.nav_marketplace, R.string.nav_marketplace, R.drawable.ic_nav_marketplace)
        val agenda = Tab(R.id.nav_agenda, R.string.nav_agenda, R.drawable.ic_nav_agenda)
        val chats = Tab(R.id.nav_chats, R.string.nav_chats, R.drawable.ic_nav_chats)
        val asistentes = Tab(R.id.nav_asistentes, R.string.nav_asistentes, R.drawable.ic_nav_asistentes)
        val reuniones = Tab(R.id.nav_reuniones, R.string.nav_reuniones, R.drawable.ic_nav_agenda)

        val tabs = when {
            u.esProveedor -> listOf(inicio, reuniones, chats, marketplace)
            u.esAsistente || u.esPropietario -> listOf(inicio, consorcios, agenda, chats, marketplace)
            else -> listOf(consorcios, marketplace, agenda, chats, asistentes)
        }
        bottomNav.menu.clear()
        tabs.forEachIndexed { i, t -> bottomNav.menu.add(0, t.id, i, t.titulo).setIcon(t.icono) }
    }

    private fun showTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_inicio -> com.example.consora_mobile.ui.InicioFragment()
            R.id.nav_marketplace -> MarketplaceFragment()
            R.id.nav_agenda -> AgendaFragment()
            R.id.nav_chats -> ChatsFragment()
            R.id.nav_asistentes -> AsistentesFragment()
            R.id.nav_reuniones -> com.example.consora_mobile.ui.ReunionesFragment()
            else -> ConsorciosFragment()
        }
        // Al cambiar de pestaña se cierran las pantallas abiertas encima (Cuenta, facturas...)
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Vuelve a leer el usuario logueado (después de editar el perfil). */
    suspend fun refrescarUsuario(): Usuario? {
        val id = usuario?.id ?: return null
        usuario = UsuarioRepository(this).getById(id)
        return usuario
    }

    /** Abre "Cuenta" encima de la pestaña actual; con Atrás se vuelve. */
    fun showCuenta() {
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) is CuentaFragment) return
        showOverlay(CuentaFragment())
    }

    /** Abre una pantalla encima de la pestaña actual (Cuenta, facturas de un consorcio...). */
    fun showOverlay(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun logout() {
        SessionManager(this).clear()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Pedido de abrir un perfil desde otra pantalla (por ejemplo, tocando el nombre en un chat)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val perfil = intent.getStringExtra(EXTRA_PERFIL)?.let(Perfil::valueOf) ?: return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val nombre = intent.getStringExtra(EXTRA_NOMBRE).orEmpty()
        val img = intent.getStringExtra(EXTRA_IMG)
        showOverlay(
            when (perfil) {
                Perfil.PROVEEDOR -> ProveedorPerfilFragment.newInstance(id, nombre, img)
                Perfil.ASISTENTE -> AsistentePerfilFragment.newInstance(id, nombre, intent.getStringExtra(EXTRA_MAIL).orEmpty(), img)
            }
        )
    }

    enum class Perfil { PROVEEDOR, ASISTENTE }

    companion object {
        private const val EXTRA_PERFIL = "perfil"
        private const val EXTRA_ID = "perfil_id"
        private const val EXTRA_NOMBRE = "perfil_nombre"
        private const val EXTRA_MAIL = "perfil_mail"
        private const val EXTRA_IMG = "perfil_img"

        /** Vuelve a la pantalla principal (cerrando las de encima, como un chat) y abre el perfil. */
        fun abrirPerfil(context: Context, perfil: Perfil, id: Long, nombre: String, mail: String, img: String?) =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_PERFIL, perfil.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NOMBRE, nombre)
                .putExtra(EXTRA_MAIL, mail)
                .putExtra(EXTRA_IMG, img)
    }
}
