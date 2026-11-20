package kz.mura.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kz.mura.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineChatTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun addContactReceiveSendVerifyReceiptAndRestore()=runBlocking {
        val app=compose.activity.application as MuraApplication
        app.container.settings.update(Preferences(retentionDays=0))
        val peer=ChatIdentity(); val client=RelayClient(peer,DEFAULT_RELAY)
        var contact: Long?=null
        try {
            withContext(Dispatchers.IO) { client.register(); app.container.network.initialize()
                val local=ChatCard.parse(app.container.network.profile.value.code)
                client.allow(local.id)
            }
            compose.onNodeWithTag("nav_chats").performClick()
            compose.onNodeWithTag("new_chat").performClick()
            compose.onNodeWithTag("contact_name").performTextInput("Internet check")
            compose.onNodeWithTag("contact_code").performTextInput(peer.card(DEFAULT_RELAY).code)
            compose.onNodeWithTag("create_chat").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("chat_input").fetchSemanticsNodes().isNotEmpty() }
            contact=app.container.messages.conversations.first { all-> all.any { it.name == "Internet check" } }.first { it.name == "Internet check" }.id
            withContext(Dispatchers.IO) {
                app.container.network.sync()
                val local=ChatCard.parse(app.container.network.profile.value.code)
                client.send(peer.encrypt("Привет с другого устройства 👋",local))
                app.container.network.sync()
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Привет с другого устройства 👋").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("chat_input").performTextInput("Ответ Android 6899")
            compose.onNodeWithTag("save_message").performClick()
            withContext(Dispatchers.IO) {
                var received: ChatEnvelope?=null
                repeat(20) {
                    if(received == null) {
                        app.container.network.sync()
                        received=client.inbox().firstOrNull()
                        if(received == null) delay(500)
                    }
                }
                val incoming=requireNotNull(received)
                val local=ChatCard.parse(app.container.network.profile.value.code)
                assertEquals("Ответ Android 6899",peer.decrypt(incoming,local))
                client.acknowledge(listOf(incoming)); app.container.network.sync()
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Доставлено",substring=true).fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Ответ Android 6899").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.container.messages.messages.first().any { it.text == "Ответ Android 6899" && it.delivery == "delivered" })
            assertTrue(app.container.messages.messages.first().any { it.text == "Привет с другого устройства 👋" && it.incoming })
        } finally {
            contact?.let { app.container.network.deleteContact(it) }
            withContext(Dispatchers.IO) { runCatching { client.erase() } }
        }
    }
}
