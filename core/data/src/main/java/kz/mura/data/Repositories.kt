package kz.mura.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import kz.mura.domain.*
import kz.mura.domain.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalMessageRepository(private val db: MuraDatabase, private val vault: LocalVault) : MessageRepository {
    private val dao = db.dao()
    private val writes = Mutex()
    private var erasedEntryId = 0L
    private var erasedConversationId = 0L
    private var erasedMessageId = 0L
    override val entries = dao.entries().map { all -> writes.withLock { all.filter { it.id > erasedEntryId }.map {
        SavedEntry(it.id, ConversionMode.valueOf(it.mode), vault.open(it.source, "entry/source/${it.mode}"), vault.open(it.result, "entry/result/${it.mode}"), it.createdAt, it.favorite)
    } } }.flowOn(Dispatchers.IO)
    override val conversations = dao.conversations().map { all -> writes.withLock { all.filter { it.id > erasedConversationId }.map { Conversation(it.id, vault.open(it.name, "conversation/name"), it.createdAt, it.peerCode?.let { code -> vault.open(code,"contact/code") }) } } }.flowOn(Dispatchers.IO)
    override val messages = dao.messages().map { all -> writes.withLock { all.filter { it.id > erasedMessageId }.map { ChatMessage(it.id, it.conversationId, vault.open(it.text, "message/${it.conversationId}/${it.incoming}"), it.incoming, it.createdAt, it.favorite, it.unread, it.delivery, it.envelope?.let { value -> vault.open(value,"chat/envelope") }) } } }.flowOn(Dispatchers.IO)
    override suspend fun saveEntry(mode: ConversionMode, source: String, result: String) = write {
        dao.insert(EntryEntity(mode = mode.name, source = vault.seal(source, "entry/source/${mode.name}"), result = vault.seal(result, "entry/result/${mode.name}"), createdAt = System.currentTimeMillis())); Unit
    }
    override suspend fun createConversation(name: String): Long = write {
        require(name.isNotBlank() && name.length <= 100)
        dao.insert(ConversationEntity(name = vault.seal(name.trim(), "conversation/name"), createdAt = System.currentTimeMillis()))
    }
    override suspend fun saveMessage(conversationId: Long, text: String, incoming: Boolean) = write {
        require(text.isNotBlank() && text.toByteArray().size <= DecimalCodec.MAX_BYTES)
        dao.insert(MessageEntity(conversationId = conversationId, text = vault.seal(text, "message/$conversationId/$incoming"), incoming = incoming, createdAt = System.currentTimeMillis(), unread = incoming)); Unit
    }
    override suspend fun favoriteEntry(id: Long, value: Boolean) = write { dao.favoriteEntry(id, value) }
    override suspend fun deleteEntry(id: Long) = write { dao.deleteEntry(id) }
    override suspend fun deleteConversation(id: Long) = write { dao.deleteConversation(id) }
    override suspend fun favoriteMessage(id: Long, value: Boolean) = write { dao.favoriteMessage(id, value) }
    override suspend fun deleteMessage(id: Long) = write { dao.deleteMessage(id) }
    override suspend fun markRead(conversationId: Long) = write { dao.markRead(conversationId) }
    override suspend fun prune(before: Long) = write { db.withTransaction { dao.pruneEntries(before); dao.pruneMessages(before) } }
    override suspend fun clearHistory() = write { dao.clearEntries() }
    override suspend fun eraseAll() = write {
        val erased = db.withTransaction {
            val maxima = Triple(dao.maxEntryId(), dao.maxConversationId(), dao.maxMessageId())
            dao.clearEntries(); dao.clearMessages(); dao.clearConversations(); dao.clearNetwork()
            maxima
        }
        // Discard stale Flow snapshots before deleting their key; new Room ids stay monotonic.
        erasedEntryId = maxOf(erasedEntryId, erased.first)
        erasedConversationId = maxOf(erasedConversationId, erased.second)
        erasedMessageId = maxOf(erasedMessageId, erased.third)
        vault.rotate()
    }
    private suspend fun <T> write(block: suspend () -> T): T = withContext(Dispatchers.IO) { writes.withLock { block() } }
}
private val Context.muraStore by preferencesDataStore("mura.preferences")
class LocalPreferencesRepository(context: Context) : PreferencesRepository {
    private val store = context.applicationContext.muraStore
    override val preferences = store.data.map { p -> Preferences(
        language = (p[LANGUAGE] ?: "ru").takeIf { it in setOf("ru", "kk", "en") } ?: "ru",
        theme = runCatching { ThemeMode.valueOf(p[THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        retentionDays = p[RETENTION] ?: 30, biometric = p[BIOMETRIC] ?: false
    ) }
    override suspend fun update(value: Preferences) { store.edit { p -> p[LANGUAGE] = value.language; p[THEME] = value.theme.name; p[RETENTION] = value.retentionDays; p[BIOMETRIC] = value.biometric } }
    companion object {
        private val LANGUAGE = stringPreferencesKey("language")
        private val THEME = stringPreferencesKey("theme")
        private val RETENTION = intPreferencesKey("retentionDays")
        private val BIOMETRIC = booleanPreferencesKey("biometric")
    }
}
class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, MuraDatabase::class.java, "mura.db").addMigrations(MuraDatabase.MIGRATION_1_2).build()
    private val vault = LocalVault()
    val messages: MessageRepository = LocalMessageRepository(db, vault)
    val network = NetworkRepository(db, vault)
    val settings: PreferencesRepository = LocalPreferencesRepository(context)
}
