package kz.mura.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.mura.app.MuraViewModel
import kz.mura.app.ChatDraft
import kz.mura.domain.*

@Composable fun ConversationScreen(vm: MuraViewModel, id: Long, copy: (String) -> Unit, back: () -> Unit) {
    val s = LocalStrings.current
    val allConversations by vm.conversations.collectAsStateWithLifecycle()
    val allMessages by vm.messages.collectAsStateWithLifecycle()
    val conversation = allConversations.firstOrNull { it.id == id }
    val messages = remember(allMessages, id) { allMessages.filter { it.conversationId == id } }
    val drafts by vm.chatDrafts.collectAsStateWithLifecycle()
    val draftState = drafts[id] ?: ChatDraft()
    val draft = draftState.text
    val incoming = draftState.incoming
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf<ChatMessage?>(null) }
    var security by remember { mutableStateOf(false) }
    val profile by vm.network.profile.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if(messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex); vm.safely { vm.repository.markRead(id) } }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, modifier = Modifier.testTag("chat_back")) { Icon(Icons.Outlined.ArrowBack, s[T.Back]) }
            Column(Modifier.weight(1f).padding(start = 5.dp)) { Text(conversation?.name ?: s[T.Chats], style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(s.n(if(conversation?.peerCode != null) profile.status else "Локальная запись"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            if(conversation?.peerCode != null) IconButton(onClick={ security=true }) { Icon(Icons.Outlined.VerifiedUser,s.n("Код безопасности")) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.DeleteOutline, s[T.Delete]) }
        }
        if(profile.error != null && conversation?.peerCode != null) Text(s.networkError(profile.error!!),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(messages.isEmpty()) item { EmptyState(Icons.Outlined.ChatBubbleOutline, s[T.EmptyConversation], s[T.SavedLocally]) }
            items(messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if(message.incoming) Alignment.Start else Alignment.End) {
                    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if(message.incoming) 5.dp else 20.dp, bottomEnd = if(message.incoming) 20.dp else 5.dp), color = if(message.incoming) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.widthIn(max = 320.dp)) {
                        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 2.dp)) {
                            SelectionContainer { Text(message.text.take(3000), Modifier.testTag("chat_message_${message.id}"), style = MaterialTheme.typography.bodyLarge, maxLines = 12, overflow = TextOverflow.Ellipsis) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${formatTime(message.createdAt, s.language)} · ${s.delivery(message.delivery)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if(message.favorite) Icon(Icons.Outlined.Star, null, Modifier.padding(start = 5.dp).size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.MoreHoriz, s[T.Open], Modifier.size(19.dp)) }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(text = { Text(s[T.Copy]) }, onClick = { copy(message.text); menu = false }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                                        DropdownMenuItem(text = { Text(s[if(message.favorite) T.Unfavorite else T.Favorite]) }, onClick = { vm.launch { vm.repository.favoriteMessage(message.id, !message.favorite) }; menu = false }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) })
                                        DropdownMenuItem(text = { Text(s[T.ShowCode]) }, onClick = { full = message; menu = false })
                                        DropdownMenuItem(text = { Text(s[T.Delete]) }, onClick = { vm.networkAction { vm.network.deleteMessage(message.id) }; menu = false }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, { vm.updateChatDraft(id, it, incoming) }, Modifier.weight(1f).testTag("chat_input"), placeholder = { Text(s[T.Message]) }, shape = RoundedCornerShape(22.dp), maxLines = 5, enabled = !saving)
                    FilledIconButton(onClick = {
                        if(draft.isNotBlank() && !saving) { val value = draft; saving = true; vm.networkAction { try { vm.sendMessage(id, value); vm.updateChatDraft(id, "", false) } finally { saving = false } } }
                    }, enabled = draft.isNotBlank() && !saving && draft.toByteArray().size <= DecimalCodec.MAX_BYTES && conversation != null, modifier = Modifier.size(56.dp).testTag("save_message"), shape = RoundedCornerShape(20.dp)) { if(saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Send, s.n(if(conversation?.peerCode != null) "Отправить" else "Сохранить локально")) }
                }
                if(draft.toByteArray().size > DecimalCodec.MAX_BYTES) Text(s[T.LongError], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if(deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(s[T.DeleteChat]) }, text={ Text(s.n("Переписка и контакт будут удалены с этого телефона. Для переписки добавьте контакт снова.")) }, confirmButton = { TextButton(onClick = { vm.networkAction { vm.network.deleteContact(id); vm.chatDrafts.value = vm.chatDrafts.value - id; deleting = false; back() } }) { Text(s[T.Delete]) } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(s[T.Cancel]) } })
    if(security) AlertDialog(onDismissRequest={ security=false },title={ Text(s.n("Код безопасности")) },text={ SelectionContainer { Column { Text(s.n("Сверьте этот код с кодом владельца контакта на его телефоне.")); Text(runCatching { ChatCard.parse(conversation!!.peerCode!!).fingerprint }.getOrDefault("")) } } },confirmButton={ TextButton(onClick={ security=false }) { Text(s[T.Close]) } })
    if(full != null) AlertDialog(onDismissRequest = { full = null }, text = { SelectionContainer { Text(full!!.text, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } }, confirmButton = { TextButton(onClick = { copy(full!!.text) }) { Text(s[T.Copy]) } }, dismissButton = { TextButton(onClick = { full = null }) { Text(s[T.Close]) } })
}
