package kz.mura.domain

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned portable password envelope. JCA provides every cryptographic primitive. */
class PasswordCipher(private val random: SecureRandom = SecureRandom()) {
    fun encrypt(text: String, password: CharArray): String {
        if (text.isEmpty()) throw MuraException(Failure.EMPTY)
        if (password.size < 4) throw MuraException(Failure.PASSWORD_SHORT)
        val clear = strictEncode(text)
        if (clear.size > DecimalCodec.MAX_BYTES) { clear.fill(0); throw MuraException(Failure.TOO_LONG) }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(AAD)
            PREFIX + digits(salt + nonce + cipher.doFinal(clear)).chunked(3).joinToString(" ")
        } finally { key.fill(0); clear.fill(0) }
    }

    fun decrypt(envelope: String, password: CharArray): String {
        if (!envelope.startsWith(PREFIX)) throw MuraException(Failure.FORMAT)
        if (password.isEmpty()) throw MuraException(Failure.PASSWORD_SHORT)
        val bytes = parseDigits(compact(envelope.removePrefix(PREFIX), (DecimalCodec.MAX_BYTES + 44) * 3))
        if (bytes.size < 45) throw MuraException(Failure.INCOMPLETE)
        val key = derive(password, bytes.copyOfRange(0, 16))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(16, 28)))
            cipher.updateAAD(AAD)
            val clear = cipher.doFinal(bytes.copyOfRange(28, bytes.size))
            try { strictDecode(clear) } finally { clear.fill(0) }
        } catch (_: GeneralSecurityException) { throw MuraException(Failure.AUTHENTICATION) }
        finally { key.fill(0); bytes.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    companion object {
        const val PREFIX = "MURA1:"
        private val AAD = "Mura/password/v1;PBKDF2-SHA256/600000;AES256-GCM".toByteArray(Charsets.US_ASCII)
    }
}
