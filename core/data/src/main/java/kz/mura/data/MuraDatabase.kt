package kz.mura.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "entries")
data class EntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val mode: String, val source: String, val result: String, val createdAt: Long, val favorite: Boolean = false)
@Entity(tableName = "conversations")
data class ConversationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long, val peerCode: String? = null)
@Entity(tableName = "messages", foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)], indices = [Index("conversationId")])
data class MessageEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val conversationId: Long, val text: String, val incoming: Boolean, val createdAt: Long, val favorite: Boolean = false, val unread: Boolean = false, @ColumnInfo(defaultValue = "'local'") val delivery: String = "local", val envelope: String? = null)

@Entity(tableName = "network")
data class NetworkEntity(@PrimaryKey val id: Int = 1, val sealed: String)

@Dao
interface MuraDao {
    @Query("SELECT * FROM entries ORDER BY createdAt DESC, id DESC") fun entries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC, id DESC") fun conversations(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM messages ORDER BY createdAt, id") fun messages(): Flow<List<MessageEntity>>
    @Insert suspend fun insert(entry: EntryEntity): Long
    @Insert suspend fun insert(conversation: ConversationEntity): Long
    @Insert suspend fun insert(message: MessageEntity): Long
    @Query("SELECT * FROM conversations") suspend fun allConversations(): List<ConversationEntity>
    @Query("SELECT * FROM messages WHERE delivery IN ('queued','sent')") suspend fun outgoing(): List<MessageEntity>
    @Query("SELECT * FROM conversations WHERE id=:id") suspend fun conversation(id: Long): ConversationEntity?
    @Query("UPDATE messages SET delivery=:delivery WHERE id=:id") suspend fun delivery(id: Long, delivery: String)
    @Query("SELECT * FROM network WHERE id=1") suspend fun network(): NetworkEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveNetwork(value: NetworkEntity)
    @Query("DELETE FROM network") suspend fun clearNetwork()
    @Query("UPDATE entries SET favorite = :value WHERE id = :id") suspend fun favoriteEntry(id: Long, value: Boolean)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: Long)
    @Query("UPDATE messages SET favorite = :value WHERE id = :id") suspend fun favoriteMessage(id: Long, value: Boolean)
    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteMessage(id: Long)
    @Query("UPDATE messages SET unread = 0 WHERE conversationId = :id") suspend fun markRead(id: Long)
    @Query("DELETE FROM entries WHERE createdAt < :before AND favorite = 0") suspend fun pruneEntries(before: Long)
    @Query("DELETE FROM messages WHERE createdAt < :before AND favorite = 0 AND delivery NOT IN ('queued','sent')") suspend fun pruneMessages(before: Long)
    @Query("SELECT COALESCE(MAX(id), 0) FROM entries") suspend fun maxEntryId(): Long
    @Query("SELECT COALESCE(MAX(id), 0) FROM messages") suspend fun maxMessageId(): Long
    @Query("SELECT COALESCE(MAX(id), 0) FROM conversations") suspend fun maxConversationId(): Long
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM messages") suspend fun clearMessages()
    @Query("DELETE FROM conversations") suspend fun clearConversations()
}
@Database(entities = [EntryEntity::class, ConversationEntity::class, MessageEntity::class, NetworkEntity::class], version = 2, exportSchema = true)
abstract class MuraDatabase : RoomDatabase() {
    abstract fun dao(): MuraDao
    companion object {
        val MIGRATION_1_2 = object: Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN peerCode TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN delivery TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE messages ADD COLUMN envelope TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS network (id INTEGER NOT NULL PRIMARY KEY, sealed TEXT NOT NULL)")
            }
        }
    }
}
