package kz.mura.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.mura.app.MuraViewModel
import kz.mura.domain.*

@Composable fun EntryCard(entry: SavedEntry, copy: (String) -> Unit, favorite: () -> Unit, open: () -> Unit, delete: (() -> Unit)? = null) {
    val s = LocalStrings.current
    Surface(onClick = open, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if(entry.mode in setOf(ConversionMode.ENCRYPT, ConversionMode.DECRYPT)) Icons.Outlined.Lock else Icons.Outlined.Numbers, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(s[modeTitle(entry.mode)], Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(formatTime(entry.createdAt, s.language, true), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Text(entry.source.take(200), Modifier.padding(top = 10.dp, end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(entry.result.take(150), Modifier.padding(top = 3.dp, end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = favorite, modifier = Modifier.size(44.dp)) { Icon(if(entry.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, s[if(entry.favorite) T.Unfavorite else T.Favorite], Modifier.size(19.dp), tint = if(entry.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { copy(entry.result) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.ContentCopy, s[T.Copy], Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                if(delete != null) IconButton(onClick = delete, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.DeleteOutline, s[T.Delete], Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
@Composable fun HistoryScreen(vm: MuraViewModel, copy: (String) -> Unit, open: (SavedEntry) -> Unit, openChat: (Long) -> Unit) {
    val s = LocalStrings.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val chats by vm.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(false) }
    val found = remember(entries, query, favorites) { entries.filter { (!favorites || it.favorite) && (query.isBlank() || it.source.contains(query, true) || it.result.contains(query, true)) } }
    val foundMessages = remember(messages, query, favorites) { messages.filter { (!favorites || it.favorite) && (favorites || query.isNotBlank()) && (query.isBlank() || it.text.contains(query, true)) }.reversed() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Brand(); Spacer(Modifier.height(15.dp)); Heading(s[T.History], s[T.SavedLocally]); Spacer(Modifier.height(22.dp)) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("history_search"), placeholder = { Text(s[T.Search]) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(17.dp)); Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!favorites, { favorites = false }, label = { Text(s[T.All]) })
            FilterChip(favorites, { favorites = true }, label = { Text(s[T.Favorites]) }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null, Modifier.size(16.dp)) }, modifier = Modifier.testTag("filter_favorites"))
        } }
        if(found.isEmpty() && foundMessages.isEmpty()) item { EmptyState(if(favorites) Icons.Outlined.StarOutline else Icons.Outlined.History, s[if(query.isNotBlank()) T.NoResults else if(favorites) T.NoFavorites else T.NoHistory], if(query.isNotBlank()) "" else s[if(favorites) T.NoFavoritesDetail else T.NoHistoryDetail]) }
        items(found, key = { "entry${it.id}" }) { entry -> EntryCard(entry, copy, { vm.launch { vm.repository.favoriteEntry(entry.id, !entry.favorite) } }, { open(entry) }, { vm.launch { vm.repository.deleteEntry(entry.id) } }) }
        items(foundMessages, key = { "message${it.id}" }) { message ->
            Surface(onClick = { openChat(message.conversationId) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(chats.firstOrNull { it.id == message.conversationId }?.name ?: s[T.Chats], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(message.text.take(300), Modifier.padding(top = 7.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(message.createdAt, s.language, true), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.launch { vm.repository.favoriteMessage(message.id, !message.favorite) } }) { Icon(if(message.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, s[T.Favorite], Modifier.size(18.dp)) }
                        IconButton(onClick = { copy(message.text) }) { Icon(Icons.Outlined.ContentCopy, s[T.Copy], Modifier.size(18.dp)) }
                        IconButton(onClick = { vm.launch { vm.repository.deleteMessage(message.id) } }) { Icon(Icons.Outlined.DeleteOutline, s[T.Delete], Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}
