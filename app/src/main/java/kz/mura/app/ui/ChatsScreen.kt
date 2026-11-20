package kz.mura.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.mura.app.MuraViewModel

@Composable fun ChatsScreen(vm: MuraViewModel, copy: (String) -> Unit, share: (String) -> Unit, open: (Long) -> Unit) {
    val s = LocalStrings.current
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var newChat by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var myCode by remember { mutableStateOf(false) }
    val profile by vm.network.profile.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val grouped = remember(messages) { messages.groupBy { it.conversationId } }
    val filtered = remember(conversations, grouped, query) { conversations.filter { it.name.contains(query, true) }.sortedByDescending { grouped[it.id]?.lastOrNull()?.createdAt ?: it.createdAt } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Brand(); Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(s[T.Chats], style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.weight(1f)); FilledTonalIconButton(onClick = { newChat = true }, modifier = Modifier.testTag("new_chat")) { Icon(Icons.Outlined.Edit, s[T.NewChat]) } }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(s.n(profile.status), Modifier.weight(1f).padding(top=8.dp), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={ myCode=true }, modifier=Modifier.testTag("my_code")) { Text(s.n("Мой код")) }
        }
        if(profile.error != null) Text(s.networkError(profile.error!!),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp)); Notice(s.n("Каждый добавляет код другого. Сообщения приходят, пока Mura открыта."), Icons.Outlined.Devices)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 18.dp), placeholder = { Text(s[T.SearchContacts]) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(17.dp))
        if(filtered.isEmpty()) {
            EmptyState(Icons.Outlined.Forum, s[if(query.isNotBlank()) T.NoResults else T.NoChats], if(query.isNotBlank()) "" else s[T.NoChatsDetail])
            if(query.isBlank()) Button(onClick = { newChat = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(s[T.NewChat]) }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filtered, key = { it.id }) { conversation ->
                val last = grouped[conversation.id]?.lastOrNull()
                val unread = grouped[conversation.id]?.count { it.unread } ?: 0
                Surface(onClick = { open(conversation.id) }, shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(49.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(conversation.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f).padding(start = 13.dp, end = 8.dp)) {
                            Text(conversation.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(last?.text?.take(80) ?: s[T.EmptyConversation], Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatTime(last?.createdAt ?: conversation.createdAt, s.language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(unread > 0) Badge(Modifier.padding(top = 7.dp), containerColor = MaterialTheme.colorScheme.primary) { Text(unread.toString()) }
                        }
                    }
                }
            }
        }
    }
    if(newChat) AddNetworkContact(vm, name, { name=it },code,{ code=it },onDismiss={ newChat=false },added={ id -> newChat=false; name=""; code=""; open(id) })
    if(myCode) NetworkProfileDialog(vm,copy,share) { myCode=false }
}
