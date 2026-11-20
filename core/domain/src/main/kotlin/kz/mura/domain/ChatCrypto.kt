package kz.mura.domain

import kotlinx.serialization.json.*
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

const val DEFAULT_RELAY = "https://defense-arthritis-maritime-salvador.trycloudflare.com"
fun chatHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
fun String.unbase64(): ByteArray = Base64.getDecoder().decode(this)
fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
fun jsonObject(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(sortedMapOf(*pairs))
fun jsonText(value: String): JsonPrimitive = JsonPrimitive(value)
fun canonicalJSON(value: JsonElement): String = when(value) {
    is JsonObject -> JsonObject(value.toSortedMap().mapValues { (_, v) -> canonicalElement(v) }).toString()
    else -> canonicalElement(value).toString()
}
private fun canonicalElement(value: JsonElement): JsonElement = when(value) {
    is JsonObject -> JsonObject(value.toSortedMap().mapValues { (_, v) -> canonicalElement(v) })
    is JsonArray -> JsonArray(value.map(::canonicalElement))
    else -> value
}
fun relayAddress(value: String, allowLoopback: Boolean = false): String {
    val clean = value.trim().trimEnd('/')
    val uri = URI(clean)
    require(clean.length <= 240 && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty()) { "invalid_server" }
    require(uri.scheme == "https" || (allowLoopback && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost"))) { "invalid_server" }
    return clean
}

data class ChatCard(val relay: String, val signingKey: String, val agreementKey: String) {
    val id: String get() = chatHash(signingKey.unbase64() + agreementKey.unbase64())
    val fingerprint: String get() = id.chunked(4).joinToString(" ")
    fun json(): JsonObject = jsonObject("version" to JsonPrimitive(1), "relay" to jsonText(relay), "signingKey" to jsonText(signingKey), "agreementKey" to jsonText(agreementKey))
    val code: String get() = "MURA-CONTACT1:" + canonicalJSON(json()).toByteArray().b64()
    companion object {
        fun parse(code: String): ChatCard {
            val clean = code.trim()
            require(clean.startsWith("MURA-CONTACT1:") && clean.length < 3000) { "invalid_contact" }
            return fromJSON(Json.parseToJsonElement(String(clean.drop(14).unbase64(), Charsets.UTF_8)).jsonObject)
        }
        fun fromJSON(value: JsonObject): ChatCard {
            require(value["version"]?.jsonPrimitive?.int == 1) { "invalid_contact" }
            val card = ChatCard(relayAddress(value.string("relay")), value.string("signingKey"), value.string("agreementKey"))
            require(card.signingKey.unbase64().size == 32 && card.agreementKey.unbase64().size == 32) { "invalid_contact" }
            return card
        }
    }
}

data class ChatEnvelope(val id: String, val sender: String, val recipient: String, val sentAt: Long, val encapsulatedKey: String, val ciphertext: String) {
    val aad: ByteArray get() = "MURA-CHAT/1\n$id\n$sender\n$recipient\n$sentAt".toByteArray()
    fun json(): JsonObject = jsonObject("version" to JsonPrimitive(1), "id" to jsonText(id), "sender" to jsonText(sender), "recipient" to jsonText(recipient), "sentAt" to JsonPrimitive(sentAt), "encapsulatedKey" to jsonText(encapsulatedKey), "ciphertext" to jsonText(ciphertext))
    companion object {
        fun parse(text: String) = fromJSON(Json.parseToJsonElement(text).jsonObject)
        fun fromJSON(v: JsonObject): ChatEnvelope {
            require(v["version"]?.jsonPrimitive?.int == 1)
            val result = ChatEnvelope(v.string("id"), v.string("sender"), v.string("recipient"), v.getValue("sentAt").jsonPrimitive.long, v.string("encapsulatedKey"), v.string("ciphertext"))
            require(UUID.fromString(result.id).toString() == result.id && result.sentAt >= 0)
            require(result.sender.matches(Regex("[0-9a-f]{64}")) && result.recipient.matches(Regex("[0-9a-f]{64}")))
            require(result.encapsulatedKey.unbase64().size == 32 && result.ciphertext.unbase64().size in 17..(DecimalCodec.MAX_BYTES+16))
            return result
        }
    }
}

class ChatIdentity private constructor(private val signing: ByteArray, private val agreement: ByteArray) {
    constructor(): this(Ed25519PrivateKeyParameters(SecureRandom()).encoded, X25519PrivateKeyParameters(SecureRandom()).encoded)
    private val signingPrivate get() = Ed25519PrivateKeyParameters(signing, 0)
    private val agreementPrivate get() = X25519PrivateKeyParameters(agreement, 0)
    private val agreementPair get() = AsymmetricCipherKeyPair(agreementPrivate.generatePublicKey(), agreementPrivate)
    fun card(relay: String) = ChatCard(relay, signingPrivate.generatePublicKey().encoded.b64(), agreementPrivate.generatePublicKey().encoded.b64())
    val id get() = card("https://unused.invalid").id
    fun json(): JsonObject = jsonObject("signing" to jsonText(signing.b64()), "agreement" to jsonText(agreement.b64()))
    fun sign(bytes: ByteArray): String = Ed25519Signer().run { init(true, signingPrivate); update(bytes, 0, bytes.size); generateSignature().b64() }
    fun encrypt(text: String, peer: ChatCard): ChatEnvelope {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= DecimalCodec.MAX_BYTES)
        val envelope = ChatEnvelope(UUID.randomUUID().toString(), id, peer.id, System.currentTimeMillis()/1000, "", "")
        val context = hpke().setupAuthS(X25519PublicKeyParameters(peer.agreementKey.unbase64(), 0), INFO, agreementPair)
        return envelope.copy(encapsulatedKey = context.encapsulation.b64(), ciphertext = context.seal(envelope.aad, bytes).b64())
    }
    fun decrypt(envelope: ChatEnvelope, peer: ChatCard): String {
        ChatEnvelope.fromJSON(envelope.json())
        require(envelope.sender == peer.id && envelope.recipient == id)
        val context = hpke().setupAuthR(envelope.encapsulatedKey.unbase64(), agreementPair, INFO, X25519PublicKeyParameters(peer.agreementKey.unbase64(), 0))
        val clear = context.open(envelope.aad, envelope.ciphertext.unbase64())
        return try { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(clear)).toString().also { require(it.isNotEmpty()) } } finally { clear.fill(0) }
    }
    fun receipt(envelope: ChatEnvelope): JsonObject {
        require(envelope.recipient == id)
        val hash = chatHash(canonicalJSON(envelope.json()).toByteArray())
        val signed = "MURA-RECEIPT/1\n${envelope.id}\n${envelope.sender}\n${envelope.recipient}\n$hash".toByteArray()
        return jsonObject("id" to jsonText(envelope.id), "sender" to jsonText(envelope.sender), "recipient" to jsonText(envelope.recipient), "messageHash" to jsonText(hash), "signature" to jsonText(sign(signed)))
    }
    companion object {
        private val INFO = "Mura/chat/1;HPKE-auth;X25519-HKDF-SHA256-ChaCha20Poly1305".toByteArray()
        private fun hpke() = HPKE(HPKE.mode_auth, HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_CHACHA20_POLY1305)
        fun fromJSON(v: JsonObject): ChatIdentity {
            val signing = v.string("signing").unbase64(); val agreement = v.string("agreement").unbase64()
            require(signing.size == 32 && agreement.size == 32)
            return ChatIdentity(signing, agreement)
        }
        fun verifyReceipt(receipt: JsonObject, envelope: ChatEnvelope, peer: ChatCard): Boolean = runCatching {
            require(receipt.string("id") == envelope.id && receipt.string("sender") == envelope.sender && receipt.string("recipient") == envelope.recipient && peer.id == envelope.recipient)
            val hash = chatHash(canonicalJSON(envelope.json()).toByteArray())
            require(receipt.string("messageHash") == hash)
            val bytes = "MURA-RECEIPT/1\n${envelope.id}\n${envelope.sender}\n${envelope.recipient}\n$hash".toByteArray()
            Ed25519Signer().run { init(false, Ed25519PublicKeyParameters(peer.signingKey.unbase64(), 0)); update(bytes,0,bytes.size); verifySignature(receipt.string("signature").unbase64()) }
        }.getOrDefault(false)
    }
}
