package kz.mura.domain

import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class RelayException(val status: Int, val code: String): Exception(code)
/** Blocking API; callers must use Dispatchers.IO. TLS is verified by the platform. */
class RelayClient(private val identity: ChatIdentity, server: String, allowLoopback: Boolean = false) {
    val server = relayAddress(server, allowLoopback)
    fun register() { request("POST", "/v1/register", identity.card(server).json()) }
    fun allow(peer: String) { request("POST", "/v1/contacts", jsonObject("peer" to jsonText(peer))) }
    fun revoke(peer: String) { request("POST", "/v1/revoke", jsonObject("peer" to jsonText(peer))) }
    fun send(message: ChatEnvelope) { request("POST", "/v1/messages", message.json()) }
    fun inbox(): List<ChatEnvelope> = request("GET", "/v1/inbox").jsonArray.map { ChatEnvelope.fromJSON(it.jsonObject) }
    fun acknowledge(messages: List<ChatEnvelope>) { if(messages.isNotEmpty()) request("POST", "/v1/ack", jsonObject("receipts" to JsonArray(messages.map(identity::receipt)))) }
    fun statuses(ids: List<String>): JsonObject = request("POST", "/v1/status", jsonObject("ids" to JsonArray(ids.map(::jsonText)))).jsonObject
    fun erase() { request("POST", "/v1/erase", jsonObject()) }
    private fun request(method: String, path: String, body: JsonObject? = null): JsonElement {
        val bytes = if(body == null) byteArrayOf() else canonicalJSON(body).toByteArray(Charsets.UTF_8)
        val timestamp = (System.currentTimeMillis()/1000).toString(); val nonce = UUID.randomUUID().toString()
        val signed = "MURA-RELAY/1\n$method\n$path\n${identity.id}\n$timestamp\n$nonce\n${chatHash(bytes)}".toByteArray()
        val connection = URI(server + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000; connection.useCaches = false
            connection.setRequestProperty("X-Mura-Device", identity.id)
            connection.setRequestProperty("X-Mura-Time", timestamp); connection.setRequestProperty("X-Mura-Nonce", nonce)
            connection.setRequestProperty("X-Mura-Signature", identity.sign(signed))
            connection.setRequestProperty("Content-Type", "application/json")
            if(method != "GET") { connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) } }
            val status = connection.responseCode
            val stream = if(status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) { val count = input.read(buffer); if(count < 0) break; require(output.size() + count <= 4_000_000); output.write(buffer,0,count) }
                output.toByteArray()
            } ?: byteArrayOf()
            require(raw.size <= 4_000_000)
            if(status !in 200..299) throw RelayException(status, runCatching { Json.parseToJsonElement(String(raw,Charsets.UTF_8)).jsonObject.string("error") }.getOrDefault("unavailable"))
            return Json.parseToJsonElement(String(raw,Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
