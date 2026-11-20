package kz.mura.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kz.mura.app.MuraViewModel

@Composable fun AddNetworkContact(vm: MuraViewModel, name: String, setName: (String)->Unit, code: String, setCode: (String)->Unit, onDismiss: ()->Unit, added: (Long)->Unit) {
    val s=LocalStrings.current; val context=LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val scan=rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(setCode) }
    AlertDialog(onDismissRequest={ if(!busy) onDismiss() },title={ Text(s[T.NewChat]) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(s.n("Каждый добавляет код другого. Сообщения приходят, пока Mura открыта."),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(name,{ setName(it.take(100)) },label={ Text(s[T.Name]) },singleLine=true,modifier=Modifier.testTag("contact_name"))
            OutlinedTextField(code,{ setCode(it.take(3000)) },label={ Text(s.n("Код контакта")) },placeholder={ Text("MURA-CONTACT1:…") },maxLines=4,modifier=Modifier.testTag("contact_code"))
            Row {
                TextButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(setCode) }) { Text(s[T.Paste]) }
                TextButton(onClick={ scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s[T.Scan]).setBeepEnabled(false).setOrientationLocked(false)) }) { Text(s[T.Scan]) }
            }
        }
    },confirmButton={ TextButton(onClick={ busy=true; vm.networkAction { try { added(vm.network.addContact(name,code)) } finally { busy=false } } },enabled=code.isNotBlank() && !busy,modifier=Modifier.testTag("create_chat")) { Text(s.n("Добавить контакт")) } },dismissButton={ TextButton(onClick=onDismiss,enabled=!busy) { Text(s[T.Cancel]) } })
}

@Composable fun NetworkProfileDialog(vm: MuraViewModel, copy: (String)->Unit, share: (String)->Unit, close: ()->Unit) {
    val s=LocalStrings.current; val profile by vm.network.profile.collectAsStateWithLifecycle()
    var qr by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={ Text(s.n("Мой код контакта")) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(s.n("Отправьте свой код собеседнику и добавьте его код у себя."))
            SelectionContainer { Text(profile.code,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("own_contact_code")) }
            Row {
                TextButton(onClick={ copy(profile.code) },enabled=profile.code.isNotEmpty()) { Text(s[T.Copy]) }
                IconButton(onClick={ share(profile.code) },enabled=profile.code.isNotEmpty()) { Icon(Icons.Outlined.IosShare,s[T.Share]) }
                IconButton(onClick={ qr=true },enabled=profile.code.isNotEmpty()) { Icon(Icons.Outlined.QrCode2,s[T.Qr]) }
            }
            Text(s.n("Мой код безопасности"),style=MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(profile.fingerprint,style=MaterialTheme.typography.bodySmall) }
            Text(s.n("Сообщения приходят, пока Mura открыта и разблокирована. Уведомлений пока нет."),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ TextButton(onClick=close) { Text(s[T.Close]) } })
    if(qr) QrDialog(profile.code) { qr=false }
}

@Composable fun NetworkSettings(vm: MuraViewModel) {
    val s=LocalStrings.current; val profile by vm.network.profile.collectAsStateWithLifecycle()
    var server by remember(profile.relay) { mutableStateOf(profile.relay) }
    Text(s.n("Переписка через интернет"),style=MaterialTheme.typography.titleSmall)
    Text(s.n(profile.status),style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(server,{ server=it },label={ Text(s.n("Адрес сервера")) },singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("relay_server"))
    TextButton(onClick={ vm.networkAction { vm.network.configure(server) } },modifier=Modifier.testTag("connect_relay")) { Text(s.n("Подключить сервер")) }
    Text(s.n("На обоих устройствах укажите один сервер. Mac должен быть включён, без сна и с интернетом."),style=MaterialTheme.typography.bodySmall)
}
