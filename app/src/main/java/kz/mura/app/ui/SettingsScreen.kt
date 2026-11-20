package kz.mura.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.mura.app.MuraViewModel
import kz.mura.domain.ThemeMode

@Composable fun SettingsScreen(vm: MuraViewModel, onEnableBiometric: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val context = LocalContext.current
    var erase by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Brand(); Spacer(Modifier.height(15.dp)); Heading(s[T.Settings], s[T.Personalize]); Spacer(Modifier.height(28.dp))
        NetworkSettings(vm)
        Spacer(Modifier.height(28.dp))
        Text(s[T.Appearance], color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) {
            Text(s[T.Theme], style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(ThemeMode.SYSTEM to T.System, ThemeMode.LIGHT to T.Light, ThemeMode.DARK to T.Dark).forEach { (mode, label) -> FilterChip(prefs.theme == mode, { vm.updatePreferences(prefs.copy(theme = mode)) }, label = { Text(s[label], style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.testTag("theme_${mode.name}")) }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Text(s[T.Language], style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("ru" to "RU", "kk" to "KZ", "en" to "EN").forEach { (lang, label) -> FilterChip(prefs.language == lang, { vm.updatePreferences(prefs.copy(language = lang)) }, label = { Text(label) }, modifier = Modifier.testTag("language_$lang")) }
            }
        } }
        Spacer(Modifier.height(28.dp))
        Text(s[T.Privacy], color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(s[T.Biometric], style = MaterialTheme.typography.titleSmall); Text(s[T.BiometricDetail], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                Switch(prefs.biometric, { if(it) onEnableBiometric() else vm.updatePreferences(prefs.copy(biometric = false)) }, modifier = Modifier.testTag("biometric_switch"))
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Text(s[T.Retention], style = MaterialTheme.typography.titleSmall)
            Text(s[T.RetentionDetail], Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to T.Never, 1 to T.Day, 7 to T.Week, 30 to T.Month).forEach { (days, label) -> FilterChip(prefs.retentionDays == days, { vm.updatePreferences(prefs.copy(retentionDays = days)) }, label = { Text(s[label], fontSize = 11.sp) }, modifier = Modifier.testTag("retention_$days")) }
            }
        } }
        Spacer(Modifier.height(18.dp)); Notice(s[T.VaultDetail], Icons.Outlined.Lock)
        TextButton(onClick = { erase = true }, Modifier.fillMaxWidth().padding(top = 20.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(s[T.Erase]) }
        Text(s[T.About], Modifier.align(Alignment.CenterHorizontally).padding(vertical = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
    if(erase) AlertDialog(onDismissRequest = { if(!erasing) erase = false }, title = { Text(s[T.Erase]) }, text = { Text(s[T.EraseConfirm]) }, confirmButton = { TextButton(onClick = { erasing = true; vm.launch { try { vm.eraseAll(); withContext(Dispatchers.IO) { File(context.cacheDir, "shares").let { if(it.exists()) check(it.deleteRecursively()) } }; erase = false } finally { erasing = false } } }, enabled = !erasing) { Text(s[T.Delete], color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { erase = false }, enabled = !erasing) { Text(s[T.Cancel]) } })
}
