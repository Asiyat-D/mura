package kz.mura.domain

import org.junit.Assert.*
import org.junit.Test

class PasswordCipherTest {
    private val cipher = PasswordCipher()
    private val password get() = "correct horse battery staple".toCharArray()
    @Test fun `portable round trip and randomized encryption`() {
        val text = "Сәлем, мир! Hello 👩🏽‍💻\n 123"
        val a = cipher.encrypt(text, password)
        val b = cipher.encrypt(text, password)
        assertNotEquals(a, b)
        assertEquals(text, PasswordCipher().decrypt(a, password))
    }
    @Test fun `wrong password and modified ciphertext fail authentication`() {
        val code = cipher.encrypt("private", password)
        rejected(Failure.AUTHENTICATION) { cipher.decrypt(code, "wrong password".toCharArray()) }
        val groups = code.removePrefix(PasswordCipher.PREFIX).split(" ").toMutableList()
        groups[30] = ((groups[30].toInt() + 1) % 256).toString().padStart(3, '0')
        rejected(Failure.AUTHENTICATION) { cipher.decrypt(PasswordCipher.PREFIX + groups.joinToString(" "), password) }
    }
    @Test fun `four digit passwords are accepted`() {
        val code = cipher.encrypt("Сәлем 👋", "6899".toCharArray())
        assertEquals("Сәлем 👋", cipher.decrypt(code, "6899".toCharArray()))
    }
    @Test fun `envelope and password boundaries`() {
        rejected(Failure.PASSWORD_SHORT) { cipher.encrypt("hello", "123".toCharArray()) }
        rejected(Failure.EMPTY) { cipher.encrypt("", password) }
        rejected(Failure.FORMAT) { cipher.decrypt("MURA2:000", password) }
        rejected(Failure.INCOMPLETE) { cipher.decrypt("MURA1:000", password) }
    }
    private fun rejected(reason: Failure, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (e: MuraException) { assertEquals(reason, e.reason) }
    }
}
