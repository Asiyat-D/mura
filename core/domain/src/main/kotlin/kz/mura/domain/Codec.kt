package kz.mura.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

/** Public, reversible UTF-8 representation. CRC detects accidents, not malicious edits. */
class DecimalCodec {
    fun encode(text: String): String {
        if (text.isEmpty()) throw MuraException(Failure.EMPTY)
        val bytes = strictEncode(text)
        if (bytes.size > MAX_BYTES) throw MuraException(Failure.TOO_LONG)
        val crc = CRC32().apply { update(bytes) }.value
        return ("010" + bytes.size.toString().padStart(8, '0') + digits(bytes) + crc.toString().padStart(10, '0')).chunked(3).joinToString(" ")
    }

    fun decode(code: String): String {
        val raw = compact(code, MAX_BYTES * 3 + 21)
        if (!raw.startsWith("010")) throw MuraException(Failure.FORMAT)
        if (raw.length < 24) throw MuraException(Failure.INCOMPLETE)
        val length = raw.substring(3, 11).toIntOrNull() ?: throw MuraException(Failure.FORMAT)
        if (length !in 1..MAX_BYTES || raw.length != 21 + length * 3) throw MuraException(Failure.INCOMPLETE)
        val payload = parseDigits(raw.substring(11, raw.length - 10))
        val expected = raw.takeLast(10).toLongOrNull() ?: throw MuraException(Failure.FORMAT)
        if (CRC32().apply { update(payload) }.value != expected) throw MuraException(Failure.CORRUPTED)
        return strictDecode(payload)
    }

    companion object { const val MAX_BYTES = 131_072 }
}

enum class Failure { EMPTY, FORMAT, INCOMPLETE, CORRUPTED, TOO_LONG, PASSWORD_SHORT, AUTHENTICATION, STORAGE, QR_TOO_LONG, BIOMETRIC }
class MuraException(val reason: Failure) : IllegalArgumentException(reason.name)

internal fun digits(bytes: ByteArray): String = buildString(bytes.size * 3) {
    bytes.forEach { append((it.toInt() and 255).toString().padStart(3, '0')) }
}
internal fun parseDigits(raw: String): ByteArray {
    if (raw.isEmpty() || raw.length % 3 != 0) throw MuraException(Failure.INCOMPLETE)
    return ByteArray(raw.length / 3) { i ->
        val n = raw.substring(i * 3, i * 3 + 3).toIntOrNull() ?: throw MuraException(Failure.FORMAT)
        if (n !in 0..255) throw MuraException(Failure.FORMAT)
        n.toByte()
    }
}
internal fun compact(code: String, limit: Int): String {
    if (code.length > limit * 2) throw MuraException(Failure.TOO_LONG)
    if (code.isBlank()) throw MuraException(Failure.EMPTY)
    val raw = buildString { code.forEach { if (it in '0'..'9') append(it) else if (!it.isWhitespace()) throw MuraException(Failure.FORMAT) } }
    if (raw.length > limit) throw MuraException(Failure.TOO_LONG)
    return raw
}
internal fun strictEncode(text: String): ByteArray = try {
    val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
    ByteArray(buffer.remaining()).also { buffer.get(it) }
} catch (_: java.nio.charset.CharacterCodingException) { throw MuraException(Failure.FORMAT) }
internal fun strictDecode(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: java.nio.charset.CharacterCodingException) { throw MuraException(Failure.CORRUPTED) }
