package kz.mura.domain

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChatCryptoTest {
    @Test fun authenticatedMessagesRoundTripAndRejectTampering() {
        val alice=ChatIdentity(); val bob=ChatIdentity(); val impostor=ChatIdentity()
        val text="Привет с Android! Сәлем 👋"
        val message=alice.encrypt(text,bob.card(DEFAULT_RELAY))
        assertEquals(text,bob.decrypt(ChatEnvelope.parse(canonicalJSON(message.json())),alice.card(DEFAULT_RELAY)))
        assertNotEquals(message.encapsulatedKey,alice.encrypt(text,bob.card(DEFAULT_RELAY)).encapsulatedKey)
        assertThrows(Exception::class.java) { bob.decrypt(message,impostor.card(DEFAULT_RELAY)) }
        assertThrows(Exception::class.java) { bob.decrypt(message.copy(sentAt=message.sentAt+1),alice.card(DEFAULT_RELAY)) }
        val changed=message.ciphertext.unbase64().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() }.b64()
        assertThrows(Exception::class.java) { bob.decrypt(message.copy(ciphertext=changed),alice.card(DEFAULT_RELAY)) }
        assertThrows(Exception::class.java) { impostor.decrypt(message,alice.card(DEFAULT_RELAY)) }
    }
    @Test fun contactsAndIdentityRestoreAndReceiptsAreBoundToExactMessage() {
        val alice=ChatIdentity(); val bob=ChatIdentity()
        assertEquals(alice.id,ChatIdentity.fromJSON(alice.json()).id)
        assertEquals(alice.card(DEFAULT_RELAY),ChatCard.parse(alice.card(DEFAULT_RELAY).code))
        val message=alice.encrypt("6899",bob.card(DEFAULT_RELAY)); val receipt=bob.receipt(message)
        assertTrue(ChatIdentity.verifyReceipt(receipt,message,bob.card(DEFAULT_RELAY)))
        assertFalse(ChatIdentity.verifyReceipt(receipt,message.copy(sentAt=message.sentAt+1),bob.card(DEFAULT_RELAY)))
        assertFalse(ChatIdentity.verifyReceipt(receipt,message,ChatIdentity().card(DEFAULT_RELAY)))
        assertThrows(Exception::class.java) { relayAddress("http://example.com") }
        assertThrows(Exception::class.java) { relayAddress("https://example.com/v1") }
        assertThrows(Exception::class.java) { relayAddress("https://user:password@example.com") }
    }
    @Test fun publicRelayDeliversBetweenIndependentAndroidClients() {
        val server=System.getenv("MURA_TEST_RELAY_URL") ?: return
        val alice=ChatIdentity(); val bob=ChatIdentity(); val a=RelayClient(alice,server); val b=RelayClient(bob,server)
        try {
            a.register(); b.register(); a.allow(bob.id)
            val first=alice.encrypt("Android → Android Сәлем 🌍",bob.card(server))
            assertThrows(RelayException::class.java) { a.send(first) }
            b.allow(alice.id); a.send(first); a.send(first)
            val received=b.inbox().single(); assertEquals("Android → Android Сәлем 🌍",bob.decrypt(received,alice.card(server)))
            b.acknowledge(listOf(received)); assertTrue(b.inbox().isEmpty())
            val receipt=a.statuses(listOf(first.id)).getValue(first.id).jsonObject.getValue("receipt").jsonObject
            assertTrue(ChatIdentity.verifyReceipt(receipt,first,bob.card(server)))
            val reply=bob.encrypt("Ответ Android",alice.card(server)); b.send(reply)
            assertEquals("Ответ Android",alice.decrypt(a.inbox().single(),bob.card(server)))
            a.acknowledge(listOf(reply))
        } finally { runCatching { a.erase() }; runCatching { b.erase() } }
    }
    @Test fun appleCryptoKitInteroperability() {
        val fixture=requireNotNull(javaClass.getResource("/chat-ios-fixture.json"))
        val v=Json.parseToJsonElement(fixture.readText()).jsonObject
        val android=ChatIdentity.fromJSON(v.getValue("androidIdentity").jsonObject)
        val ios=ChatCard.fromJSON(v.getValue("iosCard").jsonObject)
        val message=ChatEnvelope.fromJSON(v.getValue("iosEnvelope").jsonObject)
        assertEquals("iOS → Android: Привет, Сәлем! 👋",android.decrypt(message,ios))
        val reply=android.encrypt("Android → iOS: ответ 6899 🔐",ios)
        val iosIdentity=ChatIdentity.fromJSON(v.getValue("iosIdentity").jsonObject)
        assertTrue(ChatIdentity.verifyReceipt(iosIdentity.receipt(reply),reply,ios))
        System.getenv("MURA_ANDROID_INTEROP_OUT")?.let { path -> File(path).writeText(canonicalJSON(jsonObject("reply" to reply.json(),"receipt" to android.receipt(message),"androidCard" to android.card(DEFAULT_RELAY).json()))) }
    }
    @Test fun appleCryptoKitPublicRelay() {
        val ready=System.getenv("MURA_IOS_RELAY_CARD") ?: return
        val fixture=Json.parseToJsonElement(requireNotNull(javaClass.getResource("/chat-ios-fixture.json")).readText()).jsonObject
        val android=ChatIdentity.fromJSON(fixture.getValue("androidIdentity").jsonObject)
        val ios=ChatCard.fromJSON(Json.parseToJsonElement(File(ready).readText()).jsonObject)
        val client=RelayClient(android,ios.relay)
        try {
            client.register(); client.allow(ios.id)
            val message=android.encrypt("Android → iOS через интернет 👋",ios); client.send(message)
            var incoming: ChatEnvelope?=null
            repeat(60) { if(incoming == null) { incoming=client.inbox().firstOrNull(); if(incoming == null) Thread.sleep(1000) } }
            assertNotNull(incoming)
            assertEquals("iOS → Android через интернет 🔐",android.decrypt(incoming!!,ios))
            client.acknowledge(listOf(incoming!!))
            val receipt=client.statuses(listOf(message.id)).getValue(message.id).jsonObject.getValue("receipt").jsonObject
            assertTrue(ChatIdentity.verifyReceipt(receipt,message,ios))
            Thread.sleep(2000) // Let the Swift peer validate its receipt before cleanup.
        } finally { client.erase() }
    }
}
