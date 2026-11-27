package com.example.consora_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.consora_mobile.data.LoginException
import com.example.consora_mobile.data.SessionManager
import com.example.consora_mobile.data.UsuarioRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // Si ya hay una sesión guardada, se entra directo a la cuenta
        if (session.isLoggedIn) {
            openHome()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)

        btnLogin = findViewById(R.id.btnLogin)
        btnLogin.setOnClickListener { attemptLogin() }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        findViewById<Button>(R.id.btnForgotPassword).setOnClickListener { showForgotPasswordDialog() }

        // TODO: integrar Sign in with Google (Credential Manager) y validar el token en Django
        findViewById<Button>(R.id.btnGoogle).setOnClickListener { showTodo() }

        // TODO: navegar a la pantalla de registro
        findViewById<Button>(R.id.btnRegister).setOnClickListener { showTodo() }
    }

    private fun attemptLogin() {
        tilEmail.error = null
        tilPassword.error = null

        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()

        val emailError = validateEmail(email)
        if (emailError != null) {
            tilEmail.error = getString(emailError)
            etEmail.requestFocus()
            return
        }
        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_password_required)
            etPassword.requestFocus()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val usuario = UsuarioRepository(this@LoginActivity).login(email, password)
                session.save(usuario.id)
                openHome()
            } catch (e: LoginException) {
                setLoading(false)
                if (e.credencialesInvalidas) {
                    tilPassword.error = e.message
                } else {
                    Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.setText(if (loading) R.string.login_loading else R.string.login_button)
        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showForgotPasswordDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val til = view.findViewById<TextInputLayout>(R.id.tilForgotEmail)
        val et = view.findViewById<TextInputEditText>(R.id.etForgotEmail)
        et.setText(etEmail.text)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_title)
            .setView(view)
            .setPositiveButton(R.string.forgot_send, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Se reemplaza el listener para que el diálogo no se cierre si el correo es inválido
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = validateEmail(et.text?.toString()?.trim().orEmpty())
                if (error != null) {
                    til.error = getString(error)
                } else {
                    // TODO: llamar al endpoint de recuperación de contraseña
                    Toast.makeText(this, R.string.forgot_sent, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun validateEmail(email: String): Int? = when {
        email.isEmpty() -> R.string.error_email_required
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.error_email_invalid
        else -> null
    }

    private fun showTodo() {
        Toast.makeText(this, R.string.todo_backend, Toast.LENGTH_SHORT).show()
    }
}
