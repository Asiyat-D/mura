package kz.mura.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.mura.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MuraUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as MuraApplication
    @Before fun reset() {
        runBlocking { app.container.settings.update(Preferences()); app.container.messages.clearHistory() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("converter_input").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun unicodeEncodeCopyDecodeAndRotation() {
        val text = "Сәлем, мир! Hello 🌍\n 123"
        compose.onNodeWithTag("converter_input").performTextInput(text)
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { app.container.messages.entries.first().isNotEmpty() } }
        val code = runBlocking { app.container.messages.entries.first().first().result }
        assertEquals(text, DecimalCodec().decode(code))
        compose.onNodeWithTag("copy_result").performScrollTo().performClick()
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(code, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("converter_input").assertTextContains(text)
        compose.onNodeWithTag("mode_decode").performScrollTo().performClick()
        compose.onNodeWithTag("clear").performScrollTo().performClick()
        compose.onNodeWithTag("paste").performScrollTo().performClick()
        compose.onNodeWithTag("converter_input").assertTextContains(code)
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { app.container.messages.entries.first().size >= 2 } }
        compose.onNodeWithTag("converter_output").performScrollTo().assertTextEquals(text)
    }
    @Test fun invalidCodeShowsUsefulError() {
        compose.onNodeWithTag("mode_decode").performClick()
        compose.onNodeWithTag("converter_input").performTextInput("010000")
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("conversion_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("conversion_error").performScrollTo().assertExists()
        assertTrue(runBlocking { app.container.messages.entries.first().isEmpty() })
    }
    @Test fun qrResultRendersOffline() {
        compose.onNodeWithTag("converter_input").performTextInput("Сәлем 🌍")
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { app.container.messages.entries.first().isNotEmpty() } }
        compose.onNodeWithContentDescription("QR-код").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("qr_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("qr_image").assertIsDisplayed()
    }
    @Test fun localChatPersistsAndDoesNotClaimDelivery() {
        val name="Legacy ${System.nanoTime()}"
        runBlocking { app.container.messages.createConversation(name) }
        compose.onNodeWithTag("nav_chats").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat_input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_input").performTextInput("Привет, Сәлем!")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_message").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { runBlocking { app.container.messages.messages.first().any { it.text == "Привет, Сәлем!" } } }
        compose.onNodeWithText("Привет, Сәлем!").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Привет, Сәлем!").assertExists()
        compose.onNodeWithText("Локальная запись").assertExists()
    }
    @Test fun languageAndThemePersist() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_kk").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Баптаулар").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Баптаулар").onFirst().assertExists()
        compose.onNodeWithTag("theme_DARK").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { app.container.settings.preferences.first().theme == ThemeMode.DARK } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { runBlocking { app.container.settings.preferences.first().language == "kk" } }
        compose.onAllNodesWithText("Баптаулар").onFirst().assertExists()
        assertEquals(ThemeMode.DARK, runBlocking { app.container.settings.preferences.first().theme })
    }
    @Test fun passwordModeEncryptsAndRecoversText() {
        compose.onNodeWithTag("tab_secure").performClick()
        compose.onNodeWithTag("converter_input").performTextInput("Құпия хабарлама 🔐")
        compose.onNodeWithTag("password").performScrollTo().performTextInput("my unique long password")
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(60_000) { runBlocking { app.container.messages.entries.first().any { it.mode == ConversionMode.ENCRYPT } } }
        val encrypted = runBlocking { app.container.messages.entries.first().first().result }
        assertTrue(encrypted.startsWith(PasswordCipher.PREFIX))
        compose.onNodeWithTag("mode_decode").performScrollTo().performClick()
        compose.onNodeWithTag("password").performScrollTo().performTextInput("my unique long password")
        compose.onNodeWithTag("action_convert").performScrollTo().performClick()
        compose.waitUntil(60_000) { runBlocking { app.container.messages.entries.first().any { it.mode == ConversionMode.DECRYPT } } }
        compose.onNodeWithTag("converter_output").performScrollTo().assertTextEquals("Құпия хабарлама 🔐")
        compose.onNodeWithTag("password").performScrollTo().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun draftSurvivesRealOrientationChange() {
        compose.onNodeWithTag("converter_input").performTextInput("Поворот · айналу · rotate")
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithTag("converter_input").performScrollTo().assertTextContains("Поворот · айналу · rotate")
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun internetChatRequestsNetworkAccess() {
        val info = compose.activity.packageManager.getPackageInfo("kz.mura.app", android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
    }
}
