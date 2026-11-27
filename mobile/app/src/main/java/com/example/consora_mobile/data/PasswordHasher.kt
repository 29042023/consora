package com.example.consora_mobile.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de contraseñas con PBKDF2 (mismo algoritmo que usa Django por defecto).
 * Formato guardado: "pbkdf2_sha256$iteraciones$salt$hash" — nunca texto plano.
 */
object PasswordHasher {

    private const val ALGORITHM = "pbkdf2_sha256"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        return "$ALGORITHM\$$ITERATIONS\$$saltB64\$${derive(password, salt, ITERATIONS)}"
    }

    fun check(password: String, encoded: String): Boolean {
        val parts = encoded.split("$")
        if (parts.size != 4 || parts[0] != ALGORITHM) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = Base64.decode(parts[2], Base64.NO_WRAP)
        val expected = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected.toByteArray(), parts[3].toByteArray())
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }
}
