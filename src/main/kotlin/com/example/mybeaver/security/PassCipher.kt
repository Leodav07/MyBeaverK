package com.example.mybeaver.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class PasswordCipher(@Value("\${app.encryption-key}") base64Key: String) {

    private val key: SecretKey
    private val random = SecureRandom()

    init {
        val bytes = try {
            Base64.getDecoder().decode(base64Key.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("app.encryption-key no es Base64 válido.", e)
        }
        check(bytes.size == 32) { "app.encryption-key debe tener 32 bytes (256 bits) en Base64." }
        key = SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(stored: String): String {
        try {
            val data = Base64.getDecoder().decode(stored)
            val iv = data.copyOfRange(0, IV_SIZE)
            val encrypted = data.copyOfRange(IV_SIZE, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(UNREADABLE, e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(UNREADABLE, e)
        }
    }

    private companion object {
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val UNREADABLE =
            "No se pudo descifrar la clave guardada. ¿Cambió la llave de cifrado o el archivo está dañado?"
    }
}