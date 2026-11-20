@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package kz.mura.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kz.mura.app.MuraViewModel
import kz.mura.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ConverterScreen(vm: MuraViewModel, copy: (String) -> Unit, share: (String) -> Unit, notify: (String) -> Unit, openHistory: () -> Unit) {
    val state by vm.converter.collectAsStateWithLifecycle()
    val recent by vm.entries.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val protected = state.mode in setOf(ConversionMode.ENCRYPT, ConversionMode.DECRYPT)
    val reverse = state.mode in setOf(ConversionMode.DECODE, ConversionMode.DECRYPT)
    var showPassword by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var fullResult by remember { mutableStateOf(false) }
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.output) { if(state.output.isNotEmpty()) resultRequester.bringIntoView() }
    val scan = rememberLauncherForActivityResult(ScanContract()) { result -> if(result.contents != null) vm.importCode(result.contents) else if(result.originalIntent?.getBooleanExtra("MISSING_CAMERA_PERMISSION", false) == true) notify(s[T.ScanDenied]) }
    val invalidFormat = reverse && state.input.isNotBlank() && if(protected) !state.input.startsWith(PasswordCipher.PREFIX) else state.input.any { it !in '0'..'9' && !it.isWhitespace() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).imePadding()) {
        Brand()
        Spacer(Modifier.height(15.dp))
        Text(s[T.Tagline], style = MaterialTheme.typography.displaySmall)
        Text(s[T.Intro], Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(25.dp))
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to T.Encoding, true to T.Protection).forEach { (secure, label) ->
                Surface(onClick = { vm.setMode(if(secure) ConversionMode.ENCRYPT else ConversionMode.ENCODE) }, modifier = Modifier.weight(1f).testTag(if(secure) "tab_secure" else "tab_encoding"), enabled = !state.busy, color = if(secure == protected) MaterialTheme.colorScheme.surface else Color.Transparent, shape = RoundedCornerShape(14.dp), shadowElevation = if(secure == protected) 2.dp else 0.dp) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(if(secure) Icons.Outlined.Shield else Icons.Outlined.Numbers, null, Modifier.size(18.dp), tint = if(secure == protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(7.dp)); Text(s[label], style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        Row(Modifier.padding(top = 15.dp, bottom = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(selected = !reverse, onClick = { vm.setMode(if(protected) ConversionMode.ENCRYPT else ConversionMode.ENCODE) }, enabled = !state.busy, label = { Text(s[if(protected) T.Encrypt else T.Encode], style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.testTag("mode_encode"))
            FilterChip(selected = reverse, onClick = { if(state.output.isNotEmpty() && !reverse) vm.importCode(state.output) else vm.setMode(if(protected) ConversionMode.DECRYPT else ConversionMode.DECODE) }, enabled = !state.busy, label = { Text(s[if(protected) T.Decrypt else T.Decode], style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.testTag("mode_decode"))
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s[if(reverse) T.CodeInput else T.Input], style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        val value = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if(value.isNullOrEmpty()) notify(s[T.ClipboardEmpty]) else vm.setInput(value)
                    }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.testTag("paste")) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(s[T.Paste], style = MaterialTheme.typography.labelMedium) }
                }
                OutlinedTextField(value = state.input, onValueChange = vm::setInput, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp).testTag("converter_input"), placeholder = { Text(s[if(reverse) T.CodeHint else T.InputHint], color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }, enabled = !state.busy, minLines = 3, maxLines = 7, keyboardOptions = KeyboardOptions(keyboardType = if(reverse && !protected) KeyboardType.Number else KeyboardType.Text, autoCorrectEnabled = false), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), disabledBorderColor = Color.Transparent), isError = state.failure != null || invalidFormat)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.input.codePointCount(0, state.input.length)} ${s[T.Characters]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = vm::clear, enabled = !state.busy && state.input.isNotEmpty(), modifier = Modifier.testTag("clear")) { Icon(Icons.Outlined.DeleteOutline, s[T.Clear], Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if(protected) {
            OutlinedTextField(state.password, vm::setPassword, Modifier.fillMaxWidth().padding(top = 14.dp).testTag("password"), label = { Text(s[T.Password]) }, singleLine = true, enabled = !state.busy, visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if(showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, s[if(showPassword) T.HidePassword else T.ShowPassword]) } }, shape = RoundedCornerShape(16.dp))
        }
        val error = state.failure?.let(s::failure) ?: if(invalidFormat) s[T.FormatError] else null
        if(error != null) Text(error, Modifier.padding(top = 10.dp).testTag("conversion_error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { keyboard?.hide(); vm.convert() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp).testTag("action_convert"), enabled = !state.busy, shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) {
            if(state.busy) { CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            else { Icon(if(protected) Icons.Outlined.Lock else Icons.Outlined.AutoAwesome, null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)) }
            Text(s[if(state.busy) T.Processing else if(reverse) T.Decrypt else if(protected) T.Encrypt else T.Convert])
            if(!state.busy) { Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(20.dp)) }
        }
        if(state.output.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Surface(modifier = Modifier.bringIntoViewRequester(resultRequester), shape = RoundedCornerShape(22.dp), color = Navy) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(16.dp), tint = Cyan); Text(s[T.Result], Modifier.padding(start = 7.dp), color = Cyan, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp) }
                    SelectionContainer { Text(state.output.take(1000), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("converter_output"), color = Color.White, fontFamily = if(reverse) FontFamily.SansSerif else FontFamily.Monospace, fontSize = 16.sp, lineHeight = 26.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                    TextButton(onClick = { fullResult = true }, contentPadding = PaddingValues(0.dp)) { Text(s[T.ShowCode], color = Cyan, style = MaterialTheme.typography.labelSmall) }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { copy(state.output) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.13f), contentColor = Color.White), modifier = Modifier.testTag("copy_result")) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(s[T.Copy]) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { share(state.output) }) { Icon(Icons.Outlined.IosShare, s[T.Share], tint = Color.White, modifier = Modifier.size(21.dp)) }
                        if(!reverse) IconButton(onClick = { qr = state.output }) { Icon(Icons.Outlined.QrCode2, s[T.Qr], tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Notice(s[if(protected) T.SecureNotice else T.DemoNotice], if(protected) Icons.Outlined.VerifiedUser else Icons.Outlined.Info)
        TextButton(onClick = { try { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s[T.Scan]).setBeepEnabled(false).setOrientationLocked(false)) } catch (_: Exception) { notify(s[T.ScanDenied]) } }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)) { Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s[T.Scan]) }
        Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(s[T.Recent], style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); TextButton(onClick = openHistory) { Text(s[T.AllHistory], style = MaterialTheme.typography.labelMedium); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp)) } }
        if(recent.isEmpty()) EmptyState(Icons.Outlined.History, s[T.NoHistory], s[T.NoHistoryDetail])
        else recent.take(2).forEach { entry -> EntryCard(entry, copy, { vm.launch { vm.repository.favoriteEntry(entry.id, !entry.favorite) } }, { vm.restore(entry) }) }
        Spacer(Modifier.height(22.dp))
    }
    if(qr != null) QrDialog(qr!!, onDismiss = { qr = null })
    if(fullResult) AlertDialog(onDismissRequest = { fullResult = false }, title = { Text(s[T.Result]) }, text = { SelectionContainer { Text(state.output, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace) } }, confirmButton = { TextButton(onClick = { copy(state.output) }) { Text(s[T.Copy]) } }, dismissButton = { TextButton(onClick = { fullResult = false }) { Text(s[T.Close]) } })
}

@Composable fun QrDialog(value: String, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val payload = value.filterNot(Char::isWhitespace)
    val bitmap by produceState<Bitmap?>(null, payload) {
        if(payload.length <= 2000) this.value = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 768, 768, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
                Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888).apply { setPixels(IntArray(768 * 768) { i -> if(matrix[i % 768, i / 768]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 768, 0, 0, 768, 768) }
            }.getOrNull()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s[T.Qr]) }, text = {
        if(payload.length > 2000) Text(s[T.QrError])
        else if(bitmap == null) CircularProgressIndicator()
        else Image(bitmap!!.asImageBitmap(), contentDescription = s[T.Qr], modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White).testTag("qr_image"))
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(s[T.Done]) } })
}
