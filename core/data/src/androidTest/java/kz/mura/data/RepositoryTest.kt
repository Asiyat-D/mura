package kz.mura.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kz.mura.domain.ConversionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun versionOneMigrationPreservesEncryptedHistoryAndLocalChats() = runBlocking {
        val name="migration-${System.nanoTime()}.db"
        val path=context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val vault=LocalVault()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            old.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL, source TEXT NOT NULL, result TEXT NOT NULL, createdAt INTEGER NOT NULL, favorite INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId INTEGER NOT NULL, text TEXT NOT NULL, incoming INTEGER NOT NULL, createdAt INTEGER NOT NULL, favorite INTEGER NOT NULL, unread INTEGER NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_messages_conversationId ON messages(conversationId)")
            old.execSQL("INSERT INTO entries VALUES (1,'ENCODE',?,?,1,0)",arrayOf(vault.seal("Старая история","entry/source/ENCODE"),vault.seal("010","entry/result/ENCODE")))
            old.execSQL("INSERT INTO conversations VALUES (1,?,1)",arrayOf(vault.seal("Старый диалог","conversation/name")))
            old.execSQL("INSERT INTO messages VALUES (1,1,?,0,1,0,0)",arrayOf(vault.seal("Локальное сообщение","message/1/false")))
            old.version=1
        }
        val db=Room.databaseBuilder(context,MuraDatabase::class.java,name).addMigrations(MuraDatabase.MIGRATION_1_2).build()
        try {
            val repo=LocalMessageRepository(db,vault)
            assertEquals("Старая история",repo.entries.first().single().source)
            assertEquals("Старый диалог",repo.conversations.first().single().name)
            assertNull(repo.conversations.first().single().peerCode)
            assertEquals("local",repo.messages.first().single().delivery)
            assertEquals("Локальное сообщение",repo.messages.first().single().text)
            val network=NetworkRepository(db,vault); network.initialize()
            val code=network.profile.value.code
            NetworkRepository(db,vault).also { it.initialize(); assertEquals(code,it.profile.value.code) }
            assertFalse(db.dao().network()!!.sealed.contains("signing"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun encryptedContentSurvivesDatabaseReopen() = runBlocking {
        val name = "test-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, MuraDatabase::class.java, name).build()
        val vault = LocalVault()
        var repo = LocalMessageRepository(db, vault)
        try {
            repo.saveEntry(ConversionMode.ENCODE, "Сәлем, мир! 🌍", "010 001")
            val id = repo.createConversation("Aigerim")
            repo.saveMessage(id, "private message", incoming = true)
            val raw = db.dao().entries().first().single()
            assertFalse(raw.source.contains("Сәлем")); assertFalse(raw.result.contains("010 001"))
            assertFalse(db.dao().messages().first().single().text.contains("private message"))
            db.close()
            db = Room.databaseBuilder(context, MuraDatabase::class.java, name).build()
            repo = LocalMessageRepository(db, LocalVault())
            assertEquals("Сәлем, мир! 🌍", repo.entries.first().single().source)
            assertEquals("private message", repo.messages.first().single().text)
            assertTrue(repo.messages.first().single().unread)
            repo.markRead(id)
            assertFalse(repo.messages.first().single().unread)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun pruningPreservesFavoritesAndDeleteCascades() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MuraDatabase::class.java).build()
        try {
            val repo = LocalMessageRepository(db, LocalVault())
            repo.saveEntry(ConversionMode.ENCODE, "keep", "result")
            val entry = repo.entries.first().single(); repo.favoriteEntry(entry.id, true)
            repo.saveEntry(ConversionMode.ENCODE, "delete", "result")
            val id = repo.createConversation("Contact")
            repo.saveMessage(id, "keep", false)
            val message = repo.messages.first().single(); repo.favoriteMessage(message.id, true)
            repo.saveMessage(id, "delete", true)
            repo.prune(System.currentTimeMillis() + 1000)
            assertEquals("keep", repo.entries.first().single().source)
            assertEquals("keep", repo.messages.first().single().text)
            repo.deleteConversation(id)
            assertTrue(repo.messages.first().isEmpty())
            assertTrue(repo.conversations.first().isEmpty())
        } finally { db.close() }
    }
    @Test fun fullEraseRotatesKeyWithoutBreakingActiveFlows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MuraDatabase::class.java).build()
        val vault = LocalVault()
        val repo = LocalMessageRepository(db, vault)
        val snapshots = mutableListOf<List<kz.mura.domain.SavedEntry>>()
        val collector = launch { repo.entries.collect { snapshots.add(it) } }
        try {
            repo.saveEntry(ConversionMode.ENCODE, "old", "result")
            val oldCiphertext = vault.seal("old secret", "test")
            repo.eraseAll()
            assertTrue(repo.entries.first().isEmpty())
            try { vault.open(oldCiphertext, "test"); fail("Previous key must be retired") } catch (_: java.security.GeneralSecurityException) { }
            repo.saveEntry(ConversionMode.ENCODE, "new", "result")
            assertEquals("new", repo.entries.first().single().source)
            delay(50)
        } finally { collector.cancelAndJoin(); db.close() }
    }
    @Test fun vaultUsesFreshNoncesAndAuthenticatesRecordContext() {
        val vault = LocalVault()
        val a = vault.seal("secret", "one")
        val b = vault.seal("secret", "one")
        assertNotEquals(a, b)
        assertEquals("secret", vault.open(a, "one"))
        try { vault.open(a, "two"); fail("AAD mismatch must fail") } catch (_: javax.crypto.AEADBadTagException) { }
        val changed = android.util.Base64.decode(a, android.util.Base64.NO_WRAP).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        try { vault.open(android.util.Base64.encodeToString(changed, android.util.Base64.NO_WRAP), "one"); fail("Tampering must fail") } catch (_: javax.crypto.AEADBadTagException) { }
    }
}
