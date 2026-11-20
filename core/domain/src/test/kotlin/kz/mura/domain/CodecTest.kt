package kz.mura.domain

import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    private val codec = DecimalCodec()
    @Test fun `all languages whitespace and emoji round trip`() {
        listOf("ПРИВЕТ", "Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі", "Hello, 123!", "  \n\t🙂 👩🏽‍💻 𝄞\u0000", "a\u0301").forEach { assertEquals(it, codec.decode(codec.encode(it))) }
    }
    @Test fun `all unicode scalar values round trip in chunks`() {
        val points = (0..0x10FFFF).filter { it !in 0xD800..0xDFFF }
        points.chunked(8192).forEach { part ->
            val text = buildString { part.forEach { appendCodePoint(it) } }
            assertEquals(text, codec.decode(codec.encode(text)))
        }
    }
    @Test fun `grouping may be removed or changed`() {
        val code = codec.encode("Сәлем 🌍").replace(" ", "")
        assertEquals("Сәлем 🌍", codec.decode(code.chunked(7).joinToString("\n")))
    }
    @Test fun `long input boundary`() {
        val text = "x".repeat(DecimalCodec.MAX_BYTES)
        assertEquals(text, codec.decode(codec.encode(text)))
        failure(Failure.TOO_LONG) { codec.encode(text + "a") }
    }
    @Test fun `invalid inputs are rejected`() {
        failure(Failure.EMPTY) { codec.encode("") }
        failure(Failure.EMPTY) { codec.decode("  ") }
        failure(Failure.FORMAT) { codec.decode("123 abc") }
        failure(Failure.FORMAT) { codec.encode("\uD800") }
        val code = codec.encode("A").replace(" ", "")
        failure(Failure.INCOMPLETE) { codec.decode(code.dropLast(1)) }
        failure(Failure.CORRUPTED) { codec.decode(code.dropLast(1) + if (code.last() == '9') "8" else "9") }
        failure(Failure.FORMAT) { codec.decode(code.substring(0, 11) + "999" + code.takeLast(10)) }
    }
    private fun failure(reason: Failure, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (e: MuraException) { assertEquals(reason, e.reason) }
    }
}
