package kz.mura.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kz.mura.domain.*

data class NetworkProfile(val relay: String = DEFAULT_RELAY, val code: String = "", val fingerprint: String = "", val status: String = "Подключение…", val error: String? = null)

/** The identity, public contacts, deduplication state and durable outbox use the same encrypted Room vault. */
class NetworkRepository(private val db: MuraDatabase, private val vault: LocalVault) {
    private val dao = db.dao()
    private val mutex = Mutex()
    val profile = MutableStateFlow(NetworkProfile())
    private var registered: String? = null
    private val allowed = mutableSetOf<String>()
    private data class Settings(val identity: ChatIdentity, val relay: String, val seen: Map<String,Long> = emptyMap(), val revoked: Set<String> = emptySet()) {
        fun json(): JsonObject = jsonObject("identity" to identity.json(), "relay" to jsonText(relay), "seen" to JsonObject(seen.mapValues { JsonPrimitive(it.value) }), "revoked" to JsonArray(revoked.map(::jsonText)))
    }
    private suspend fun load(): Settings {
        val existing = dao.network()
        if(existing == null) {
            check(dao.allConversations().none { it.peerCode != null }) { "Identity unavailable" }
            return Settings(ChatIdentity(),DEFAULT_RELAY).also { save(it) }
        }
        val value = Json.parseToJsonElement(vault.open(existing.sealed,"network/settings")).jsonObject
        return Settings(ChatIdentity.fromJSON(value.getValue("identity").jsonObject), relayAddress(value.string("relay")), value.getValue("seen").jsonObject.mapValues { it.value.jsonPrimitive.long }, value.getValue("revoked").jsonArray.map { it.jsonPrimitive.content }.toSet())
    }
    private suspend fun save(value: Settings) { dao.saveNetwork(NetworkEntity(sealed = vault.seal(canonicalJSON(value.json()),"network/settings"))) }
    private fun publish(value: Settings, status: String = profile.value.status, error: String? = null) {
        val card = value.identity.card(value.relay)
        profile.value = NetworkProfile(value.relay,card.code,card.fingerprint,status,error)
    }
    suspend fun initialize() = access { publish(load()) }
    suspend fun configure(server: String) = access {
        val value = load().copy(relay = relayAddress(server))
        save(value); registered = null; allowed.clear(); publish(value,"Подключение…")
    }
    suspend fun addContact(name: String, code: String): Long = access {
        var value = load(); val peer = ChatCard.parse(code)
        require(peer.id != value.identity.id) { "own_contact" }
        val contacts = dao.allConversations()
        if(peer.relay != value.relay) {
            require(contacts.none { it.peerCode != null }) { "different_server" }
            value = value.copy(relay = peer.relay); registered = null; allowed.clear()
        }
        contacts.firstOrNull { it.peerCode?.let { sealed -> ChatCard.parse(vault.open(sealed,"contact/code")).id == peer.id } == true }?.let { return@access it.id }
        value = value.copy(revoked = value.revoked - peer.id)
        val id = db.withTransaction {
            save(value)
            dao.insert(ConversationEntity(name = vault.seal(name.trim().take(100).ifBlank { peer.id.take(8) },"conversation/name"),createdAt=System.currentTimeMillis(),peerCode=vault.seal(peer.code,"contact/code")))
        }
        publish(value); id
    }
    suspend fun send(id: Long, text: String) = access {
        val contact = requireNotNull(dao.conversation(id))
        val peer = ChatCard.parse(vault.open(requireNotNull(contact.peerCode),"contact/code"))
        val envelope = load().identity.encrypt(text,peer)
        dao.insert(MessageEntity(conversationId=id,text=vault.seal(text,"message/$id/false"),incoming=false,createdAt=System.currentTimeMillis(),delivery="queued",envelope=vault.seal(canonicalJSON(envelope.json()),"chat/envelope")))
        Unit
    }
    suspend fun deleteContact(id: Long) = access {
        val contact = dao.conversation(id)
        val peer = contact?.peerCode?.let { ChatCard.parse(vault.open(it,"contact/code")) }
        if(peer != null) { val value = load(); save(value.copy(revoked=value.revoked+peer.id)); allowed.remove(peer.id) }
        dao.deleteConversation(id)
    }
    suspend fun deleteMessage(id: Long) = access { dao.deleteMessage(id) }
    suspend fun eraseAll(localErase: suspend () -> Unit) = access {
        val old = load()
        runCatching { RelayClient(old.identity,old.relay).apply { register(); erase() } }
        localErase(); registered = null; allowed.clear()
        val fresh = Settings(ChatIdentity(),old.relay); save(fresh); publish(fresh,"Подключение…")
    }
    suspend fun sync() = access {
        var value = load()
        val client = RelayClient(value.identity,value.relay)
        try {
            val registration = value.relay+value.identity.id
            if(registered != registration) { client.register(); registered = registration; allowed.clear() }
            for(id in value.revoked) { client.revoke(id); value=value.copy(revoked=value.revoked-id); save(value) }
            val contacts = dao.allConversations().mapNotNull { c -> c.peerCode?.let { c to ChatCard.parse(vault.open(it,"contact/code")) } }
            for((_,peer) in contacts) if(peer.id !in allowed) { client.allow(peer.id); allowed.add(peer.id) }
            var warning: String? = null
            val now = System.currentTimeMillis()
            for(record in dao.outgoing().filter { it.delivery == "queued" }.take(20)) {
                if(now-record.createdAt > 7*86_400_000L) { dao.delivery(record.id,"expired"); continue }
                val envelope=ChatEnvelope.parse(vault.open(requireNotNull(record.envelope),"chat/envelope"))
                try { client.send(envelope); dao.delivery(record.id,"sent") }
                catch(e: RelayException) { if(e.code in listOf("peer_not_ready","recipient_missing","queue_full")) warning=e.code else throw e }
            }
            val received = mutableListOf<ChatEnvelope>()
            for(envelope in client.inbox()) {
                val (contact,peer)=contacts.firstOrNull { it.second.id == envelope.sender } ?: continue
                val clear = value.identity.decrypt(envelope,peer)
                if(envelope.id !in value.seen) {
                    val updated=value.copy(seen=value.seen.filterValues { now-it < 8*86_400_000L }+(envelope.id to now))
                    db.withTransaction {
                        dao.insert(MessageEntity(conversationId=contact.id,text=vault.seal(clear,"message/${contact.id}/true"),incoming=true,createdAt=now,unread=true,delivery="received"))
                        save(updated)
                    }
                    value=updated
                }
                received.add(envelope)
            }
            client.acknowledge(received)
            val outgoing=dao.outgoing().filter { it.delivery == "sent" }.take(100)
            if(outgoing.isNotEmpty()) {
                val envelopes=outgoing.associateWith { ChatEnvelope.parse(vault.open(requireNotNull(it.envelope),"chat/envelope")) }
                val statuses=client.statuses(envelopes.values.map { it.id })
                for((record,envelope) in envelopes) {
                    val delivery=statuses[envelope.id]?.jsonObject ?: continue
                    val receipt=delivery["receipt"] as? JsonObject
                    val peer=contacts.firstOrNull { it.second.id == envelope.recipient }?.second ?: continue
                    if(delivery.string("state") == "delivered" && receipt != null && ChatIdentity.verifyReceipt(receipt,envelope,peer)) dao.delivery(record.id,"delivered")
                    else if(delivery.string("state") == "missing" && now-record.createdAt > 7*86_400_000L) dao.delivery(record.id,"expired")
                }
            }
            publish(value,"Подключено · сквозное шифрование",warning)
        } catch(e: Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            if(e is RelayException && e.status == 401) registered=null
            publish(value,"Нет связи","offline")
        }
    }
    private suspend fun <T> access(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
