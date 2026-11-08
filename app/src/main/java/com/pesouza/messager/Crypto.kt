package com.pesouza.messager

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tudo que passa pelo servidor é cifrado com AES-256-GCM usando uma chave
 * derivada do código da família. O servidor só vê texto embaralhado.
 */
object Crypto {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    @Volatile private var cachedCode: String? = null
    @Volatile private var cachedKey: SecretKey? = null

    fun normalize(code: String): String =
        code.uppercase().filter { it.isLetterOrDigit() }

    fun generateCode(): String =
        (1..16).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }

    fun topicFor(code: String): String =
        "msgr_" + sha256Hex("messager-topic:" + normalize(code)).take(40)

    private fun keyFor(code: String): SecretKey {
        val n = normalize(code)
        cachedKey?.let { if (cachedCode == n) return it }
        val spec = PBEKeySpec(n.toCharArray(), "messager-salt-v1".toByteArray(), 20000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val key = SecretKeySpec(raw, "AES")
        cachedCode = n
        cachedKey = key
        return key
    }

    fun encrypt(code: String, plain: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(code), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "m1." + b64(iv) + "." + b64(ct)
    }

    /** Retorna null se a mensagem não foi cifrada com este código. */
    fun decrypt(code: String, data: String): String? = try {
        val parts = data.trim().split(".")
        if (parts.size != 3 || parts[0] != "m1") null
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyFor(code), GCMParameterSpec(128, unb64(parts[1])))
            String(cipher.doFinal(unb64(parts[2])), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
