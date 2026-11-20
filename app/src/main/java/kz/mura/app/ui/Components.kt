package kz.mura.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.mura.domain.ConversionMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun Brand() {
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(37.dp).background(Blue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("m", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp) }
        Text("mura", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge, letterSpacing = (-0.7).sp)
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) Text(subtitle, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Notice(text: String, icon: ImageVector = Icons.Outlined.Info, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}
@Composable fun EmptyState(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        Text(title, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.titleMedium)
        Text(detail, Modifier.padding(top = 7.dp).widthIn(max = 270.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun LoadingScreen() { Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
@Composable fun LockScreen(error: Boolean, onUnlock: () -> Unit) {
    val s = LocalStrings.current
    Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(Icons.Outlined.Lock, s[T.Locked], if(error) s[T.BiometricUnavailable] else s[T.BiometricDetail])
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Fingerprint, null); Spacer(Modifier.width(10.dp)); Text(s[T.Unlock]) }
    } }
}
fun modeTitle(mode: ConversionMode): T = when(mode) { ConversionMode.ENCODE -> T.EncodingLabel; ConversionMode.DECODE -> T.DecodingLabel; ConversionMode.ENCRYPT -> T.EncryptedLabel; ConversionMode.DECRYPT -> T.DecryptedLabel }
fun formatTime(time: Long, language: String, full: Boolean = false): String = DateTimeFormatter.ofPattern(if(full) "dd MMM · HH:mm" else "HH:mm", Locale.forLanguageTag(language)).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

@Composable fun StorageFailureScreen() { val s = LocalStrings.current; Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) { EmptyState(Icons.Outlined.ErrorOutline, s[T.Error], s[T.StorageError]) } } }
