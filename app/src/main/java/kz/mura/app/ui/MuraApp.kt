package kz.mura.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import kz.mura.app.MuraViewModel
import kotlinx.coroutines.launch

@Composable fun MuraApp(vm: MuraViewModel, onEnableBiometric: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "cipher"
    val s = LocalStrings.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val storageError by vm.storageFailure.collectAsStateWithLifecycle()
    val networkError by vm.networkError.collectAsStateWithLifecycle()
    val notify: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val copy: (String) -> Unit = { value ->
        if(value.length > 180_000) notify(s[T.ClipboardLong]) else try {
            val clip = ClipData.newPlainText("Mura", value)
            if(Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            notify(s[T.Copied])
        } catch (_: RuntimeException) { notify(s[T.ClipboardLong]) }
    }
    val share: (String) -> Unit = { value -> scope.launch {
        try {
            val intent = withContext(Dispatchers.IO) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    if(value.length <= 100_000) putExtra(Intent.EXTRA_TEXT, value)
                    else {
                        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
                        val cutoff = System.currentTimeMillis() - 3_600_000
                        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
                        val file = File(directory, "mura-${UUID.randomUUID()}.txt").apply { writeText(value, Charsets.UTF_8) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("Mura", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            context.startActivity(Intent.createChooser(intent, s[T.Share]))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { notify(s[T.ShareError]) }
    } }
    val navigate: (String) -> Unit = { target -> nav.navigate(target) { popUpTo("cipher") { saveState = true }; launchSingleTop = true; restoreState = true } }
    val destinations = listOf(Triple("cipher", T.Cipher, Icons.Outlined.Numbers), Triple("chats", T.Chats, Icons.Outlined.ChatBubbleOutline), Triple("history", T.History, Icons.Outlined.History), Triple("settings", T.Settings, Icons.Outlined.Tune))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Row {
            if(wide && !route.startsWith("conversation")) NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars), containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(36.dp))
                destinations.forEach { (path, label, icon) -> NavigationRailItem(selected = route == path, onClick = { navigate(path) }, icon = { Icon(icon, s[label]) }, label = { Text(s[label]) }, modifier = Modifier.testTag("nav_$path")) }
            }
            Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if(!wide && !route.startsWith("conversation")) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { (path, label, icon) -> NavigationBarItem(selected = route == path, onClick = { navigate(path) }, icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) }, label = { Text(s[label], style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("nav_$path"), colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary)) }
                }
            }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    NavHost(nav, "cipher", modifier = Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                        composable("cipher") { ConverterScreen(vm, copy, share, notify, { navigate("history") }) }
                        composable("history") { HistoryScreen(vm, copy, { entry -> vm.restore(entry); navigate("cipher") }, { id -> nav.navigate("conversation/$id") }) }
                        composable("chats") { ChatsScreen(vm, copy, share, { nav.navigate("conversation/$it") }) }
                        composable("settings") { SettingsScreen(vm, onEnableBiometric) }
                        composable("conversation/{id}") { entry -> ConversationScreen(vm, entry.arguments?.getString("id")?.toLongOrNull() ?: 0, copy, { nav.popBackStack() }) }
                    }
                }
            }
        }
    }
    if(storageError) AlertDialog(onDismissRequest = { vm.storageFailure.value = false }, title = { Text(s[T.Error]) }, text = { Text(s[T.StorageError]) }, confirmButton = { TextButton(onClick = { vm.storageFailure.value = false }) { Text(s[T.Close]) } })
    if(networkError != null) AlertDialog(onDismissRequest = { vm.networkError.value = null }, title = { Text(s[T.Error]) }, text = { Text(s.networkError(networkError!!)) }, confirmButton = { TextButton(onClick = { vm.networkError.value = null }) { Text(s[T.Close]) } })
}
