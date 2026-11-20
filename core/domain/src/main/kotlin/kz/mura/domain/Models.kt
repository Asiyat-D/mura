package kz.mura.domain

import kotlinx.coroutines.flow.Flow

enum class ConversionMode { ENCODE, DECODE, ENCRYPT, DECRYPT }
data class SavedEntry(val id: Long, val mode: ConversionMode, val source: String, val result: String, val createdAt: Long, val favorite: Boolean)
data class Conversation(val id: Long, val name: String, val createdAt: Long, val peerCode: String? = null)
data class ChatMessage(val id: Long, val conversationId: Long, val text: String, val incoming: Boolean, val createdAt: Long, val favorite: Boolean, val unread: Boolean, val delivery: String = "local", val envelope: String? = null)
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class Preferences(val language: String = "ru", val theme: ThemeMode = ThemeMode.SYSTEM, val retentionDays: Int = 30, val biometric: Boolean = false)
interface MessageRepository {
    val entries: Flow<List<SavedEntry>>
    val conversations: Flow<List<Conversation>>
    val messages: Flow<List<ChatMessage>>
    suspend fun saveEntry(mode: ConversionMode, source: String, result: String)
    suspend fun favoriteEntry(id: Long, value: Boolean)
    suspend fun deleteEntry(id: Long)
    suspend fun createConversation(name: String): Long
    suspend fun deleteConversation(id: Long)
    suspend fun saveMessage(conversationId: Long, text: String, incoming: Boolean)
    suspend fun favoriteMessage(id: Long, value: Boolean)
    suspend fun deleteMessage(id: Long)
    suspend fun markRead(conversationId: Long)
    suspend fun prune(before: Long)
    suspend fun clearHistory()
    /** Removes all encrypted content and rotates the device key only after the transaction. */
    suspend fun eraseAll()
}
interface PreferencesRepository {
    val preferences: Flow<Preferences>
    suspend fun update(value: Preferences)
}
/** A future transport must report verified server/device acknowledgements; never infer delivery. */
interface SecureTransport {
    val events: Flow<TransportEvent>
    suspend fun send(recipientIdentity: String, authenticatedCiphertext: ByteArray): String
}
sealed interface TransportEvent {
    data class ServerAccepted(val messageId: String) : TransportEvent
    data class DeviceDelivered(val messageId: String) : TransportEvent
}
