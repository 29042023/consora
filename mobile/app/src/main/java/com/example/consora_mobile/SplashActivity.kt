package com.example.consora_mobile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.data.Carpeta
import com.example.consora_mobile.data.ConsoraDatabase
import com.example.consora_mobile.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de carga: muestra el logo animado mientras se prepara la base de datos
 * y después abre el login o, si ya hay sesión, la pantalla principal.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tvVersion).text = getString(
            R.string.splash_version,
            packageManager.getPackageInfo(packageName, 0).versionName,
        )

        animarEntrada()

        lifecycleScope.launch {
            // La primera vez se crea la base con los datos de ejemplo; se hace mientras se ve la animación
            val preparar = async(Dispatchers.IO) {
                Carpeta.crearTodas(this@SplashActivity)
                ConsoraDatabase.get(this@SplashActivity).readableDatabase
            }
            mostrarEstados()
            preparar.await()

            val destino = if (SessionManager(this@SplashActivity).isLoggedIn) MainActivity::class.java
            else LoginActivity::class.java
            startActivity(Intent(this@SplashActivity, destino))
            // Transición suave hacia la siguiente pantalla
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun animarEntrada() {
        val logo = findViewById<View>(R.id.ivLogo)
        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        logo.translationY = 40f
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // Barra, texto y pie aparecen después del logo
        listOf(R.id.progress, R.id.tvEstado, R.id.pie).forEachIndexed { i, id ->
            findViewById<View>(id).animate().alpha(1f).setStartDelay(500L + i * 120).setDuration(400).start()
        }

        // Los edificios suben desde abajo
        findViewById<View>(R.id.skyline).apply {
            translationY = 120f
            animate().alpha(1f).translationY(0f).setStartDelay(250).setDuration(900)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }

        // Los círculos de fondo "respiran" lentamente
        flotar(findViewById(R.id.circuloArriba), 18f)
        flotar(findViewById(R.id.circuloAbajo), -14f)
    }

    private fun flotar(view: View, distancia: Float) {
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, distancia).apply {
            duration = 2200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Va cambiando el texto de estado mientras carga (duración total ~2,4 s). */
    private suspend fun mostrarEstados() {
        val tv = findViewById<TextView>(R.id.tvEstado)
        delay(900)
        for (texto in listOf(R.string.splash_estado_2, R.string.splash_estado_3)) {
            tv.animate().alpha(0f).setStartDelay(0).setDuration(150).start()
            delay(150)
            tv.setText(texto)
            tv.animate().alpha(1f).setDuration(150).start()
            delay(600)
        }
        delay(300)
    }
}
