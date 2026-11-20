import SwiftUI

struct EntryCard: View {
    @EnvironmentObject var app: AppState
    let entry: Entry
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack { Label(app.s[entry.mode.label], systemImage: entry.mode.secure ? "lock" : "number").foregroundStyle(Palette.blue); Spacer(); Text(entry.date, format: .dateTime.day().month().hour().minute()).foregroundStyle(.secondary) }.font(.caption)
            Text(String(entry.source.prefix(180))).lineLimit(1).font(.subheadline)
            Text(String(entry.result.prefix(150))).lineLimit(1).font(.caption).foregroundStyle(.secondary)
            HStack {
                Button(app.s[.Open]) { app.restore(entry) }.font(.caption)
                Spacer()
                Button { app.favorite(entry.id) } label: { Image(systemName: entry.favorite ? "star.fill" : "star").padding(8) }.accessibilityLabel(app.s[.Favorite])
                Button { app.copy(entry.result) } label: { Image(systemName: "doc.on.doc").padding(8) }.accessibilityLabel(app.s[.Copy])
                Button { app.delete(entry.id) } label: { Image(systemName: "trash").padding(8) }.accessibilityLabel(app.s[.Delete])
            }.font(.subheadline)
        }.padding(17).background(Palette.card, in: RoundedRectangle(cornerRadius: 19))
    }
}
struct HistoryView: View {
    @EnvironmentObject var app: AppState
    @State private var query = ""
    @State private var favorites = false
    private var found: [Entry] { app.entries.filter { (!favorites || $0.favorite) && (query.isEmpty || $0.source.localizedCaseInsensitiveContains(query) || $0.result.localizedCaseInsensitiveContains(query)) } }
    private var foundMessages: [Message] { app.messages.reversed().filter { (!favorites || $0.favorite) && (favorites || !query.isEmpty) && (query.isEmpty || $0.text.localizedCaseInsensitiveContains(query)) } }
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                Brand(); Text(app.s[.History]).font(.largeTitle.bold()); Text(app.s[.SavedLocally]).font(.subheadline).foregroundStyle(.secondary)
                HStack { Image(systemName: "magnifyingglass").foregroundStyle(.secondary); TextField(app.s[.Search], text: $query).accessibilityIdentifier("history_search") }.padding(15).background(Palette.card, in: RoundedRectangle(cornerRadius: 16))
                Picker("", selection: $favorites) { Text(app.s[.All]).tag(false); Text(app.s[.Favorites]).tag(true) }.pickerStyle(.segmented)
                if found.isEmpty && foundMessages.isEmpty { EmptyViewCard(icon: favorites ? "star" : "clock", title: app.s[query.isEmpty ? (favorites ? .NoFavorites : .NoHistory) : .NoResults], detail: app.s[favorites ? .NoFavoritesDetail : .NoHistoryDetail]) }
                ForEach(found) { EntryCard(entry: $0) }
                ForEach(foundMessages) { message in
                    NavigationLink { ConversationView(id: message.contactID) } label: {
                        VStack(alignment: .leading, spacing: 9) { Text(app.contacts.first(where: { $0.id == message.contactID })?.name ?? app.s[.Chats]).font(.headline).foregroundStyle(Palette.blue); Text(String(message.text.prefix(300))).font(.subheadline).foregroundStyle(.primary).lineLimit(3); HStack { Text(message.date, style: .time).font(.caption).foregroundStyle(.secondary); Spacer(); Button { app.favorite(message.id) } label: { Image(systemName: message.favorite ? "star.fill" : "star").padding(8) }; Button { app.copy(message.text) } label: { Image(systemName: "doc.on.doc").padding(8) } } }.padding(18).frame(maxWidth: .infinity, alignment: .leading).background(Palette.card, in: RoundedRectangle(cornerRadius: 19))
                    }
                }
            }.padding(.horizontal, 23).padding(.bottom, 25).frame(maxWidth: 760).frame(maxWidth: .infinity)
        }.background(Palette.background).toolbar(.hidden, for: .navigationBar)
    }
}
struct ChatsView: View {
    @EnvironmentObject var app: AppState
    @State private var query = ""
    @State private var creating = false
    @State private var name = ""
    @State private var profile = false
    @State private var path: [UUID] = []
    private var contacts: [Contact] { app.contacts.filter { query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) }.sorted { newest($0) > newest($1) } }
    private func newest(_ contact: Contact) -> Date { app.messages.last(where: { $0.contactID == contact.id })?.date ?? contact.date }
    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 17) {
                    Brand()
                    HStack { Text(app.s[.Chats]).font(.largeTitle.bold()); Spacer(); Button { creating = true } label: { Image(systemName: "square.and.pencil").font(.title3).padding(12).background(Palette.blue.opacity(0.1), in: RoundedRectangle(cornerRadius: 15)) }.accessibilityIdentifier("new_chat") }
                    HStack { Label(app.s.n(app.networkStatus), systemImage: "lock.shield").font(.caption).foregroundStyle(.secondary); Spacer(); Button(app.s.n("Мой код")) { profile = true }.accessibilityIdentifier("my_contact") }
                    Text(app.s.n("Обменяйтесь кодами контактов: каждый добавляет код другого. Сообщения приходят, пока Mura открыта.")).font(.caption).foregroundStyle(.secondary)
                    HStack { Image(systemName: "magnifyingglass"); TextField(app.s[.SearchContacts], text: $query) }.padding(15).background(Palette.card, in: RoundedRectangle(cornerRadius: 16))
                    if contacts.isEmpty { EmptyViewCard(icon: "bubble.left.and.bubble.right", title: app.s[query.isEmpty ? .NoChats : .NoResults], detail: app.s[.NoChatsDetail]); Button { creating = true } label: { Label(app.s[.NewChat], systemImage: "plus").frame(maxWidth: .infinity).padding(.vertical, 10) }.buttonStyle(.borderedProminent) }
                    ForEach(contacts) { contact in
                        NavigationLink(value: contact.id) {
                            HStack(spacing: 13) {
                                Text(String(contact.name.prefix(1)).uppercased()).font(.title2.weight(.semibold)).foregroundStyle(Palette.blue).frame(width: 50, height: 50).background(Palette.blue.opacity(0.1), in: Circle())
                                VStack(alignment: .leading, spacing: 6) { Text(contact.name).font(.headline).foregroundStyle(.primary).lineLimit(1); Text(app.messages.last(where: { $0.contactID == contact.id }).map { String($0.text.prefix(90)) } ?? app.s[.EmptyConversation]).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 5) { Text(newest(contact), style: .time).font(.caption2).foregroundStyle(.secondary); let unread = app.messages.filter { $0.contactID == contact.id && $0.unread }.count; if unread > 0 { Text("\(unread)").font(.caption2.bold()).foregroundStyle(.white).padding(6).background(Palette.blue, in: Circle()) } }
                            }.padding(17).background(Palette.card, in: RoundedRectangle(cornerRadius: 20))
                        }.accessibilityIdentifier(contact.peer.map { "peer_" + $0.id } ?? "contact_" + contact.id.uuidString)
                    }
                }.padding(.horizontal, 23).padding(.bottom, 25).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }.background(Palette.background).toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: UUID.self) { ConversationView(id: $0) }
                .sheet(isPresented: $creating) { AddContactView { id in creating = false; path.append(id) } }
                .sheet(isPresented: $profile) { NetworkProfileView() }
                .refreshable { await app.syncNetwork() }
        }
    }
}
struct ConversationView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let id: UUID
    @State private var deleting = false
    @State private var fullMessage: Message?
    @State private var peerDetails = false
    private var messages: [Message] { app.messages.filter { $0.contactID == id } }
    private var draft: Binding<String> { Binding(get: { app.chatDrafts[id] ?? "" }, set: { app.chatDrafts[id] = String($0.prefix(1_048_576)) }) }
    var body: some View {
        VStack(spacing: 0) {
            Label(app.s.n(app.contacts.first(where: { $0.id == id })?.peer == nil ? "Локальная запись · добавьте собеседника по коду для переписки" : app.networkStatus), systemImage: "lock.shield").font(.caption).foregroundStyle(.secondary).padding(.horizontal, 18).padding(.vertical, 8)
            ScrollViewReader { scroll in
                ScrollView { LazyVStack(spacing: 13) {
                    if messages.isEmpty { EmptyViewCard(icon: "bubble.left", title: app.s[.EmptyConversation], detail: app.s[.SavedLocally]) }
                    ForEach(messages) { message in
                        HStack {
                            if !message.incoming { Spacer(minLength: 35) }
                            VStack(alignment: .leading, spacing: 9) {
                                Text(String(message.text.prefix(3000))).lineLimit(12).textSelection(.enabled)
                                HStack(spacing: 7) { Text(message.date, style: .time); Text("· " + app.s.n(app.deliveryLabel(message.delivery))); if message.favorite { Image(systemName: "star.fill") }; Spacer(minLength: 0); Menu { Button(app.s[.Copy]) { app.copy(message.text) }; Button(app.s[message.favorite ? .Unfavorite : .Favorite]) { app.favorite(message.id) }; Button(app.s[.ShowCode]) { fullMessage = message }; Button(app.s[.Delete], role: .destructive) { app.delete(message.id) } } label: { Image(systemName: "ellipsis").padding(7) } }.font(.caption2).foregroundStyle(.secondary)
                            }.padding(15).background(message.incoming ? Palette.card : Palette.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: 20)).frame(maxWidth: 360, alignment: message.incoming ? .leading : .trailing)
                            if message.incoming { Spacer(minLength: 35) }
                        }.id(message.id)
                    }
                }.padding(18) }
                    .onChange(of: messages.count) { _, _ in if let last = messages.last { withAnimation { scroll.scrollTo(last.id, anchor: .bottom) } }; app.read(id) }
                    .onAppear { if let last = messages.last { scroll.scrollTo(last.id, anchor: .bottom) }; app.read(id) }
            }
            VStack(spacing: 10) {
                HStack(alignment: .bottom, spacing: 11) {
                    TextField(app.s[.Message], text: draft, axis: .vertical).lineLimit(1...5).padding(13).background(Palette.background, in: RoundedRectangle(cornerRadius: 20)).accessibilityIdentifier("chat_input")
                    Button { app.send(draft.wrappedValue, to: id, incoming: false) } label: { Image(systemName: "paperplane.fill").font(.title3.weight(.semibold)).foregroundStyle(.white).frame(width: 49, height: 49).background(Palette.blue, in: RoundedRectangle(cornerRadius: 18)) }.disabled(draft.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).accessibilityLabel(app.s.n("Отправить")).accessibilityIdentifier("save_message")
                }
            }.padding(.horizontal, 17).padding(.vertical, 10).background(Palette.card)
        }.background(Palette.background).navigationTitle(app.contacts.first(where: { $0.id == id })?.name ?? app.s[.Chats]).navigationBarTitleDisplayMode(.inline).toolbar { Button { peerDetails = true } label: { Image(systemName: "person.crop.circle.badge.checkmark") }; Button(role: .destructive) { deleting = true } label: { Image(systemName: "trash") }.accessibilityIdentifier("delete_chat") }
            .sheet(isPresented: $peerDetails) { NavigationStack { ScrollView { VStack(alignment: .leading, spacing: 18) { Text(app.s.n("Код безопасности")).font(.title2.bold()); Text(app.s.n("Сверьте этот код с кодом владельца контакта на его телефоне.")); Text(app.contacts.first(where: { $0.id == id })?.peer?.fingerprint ?? "Локальная запись").font(.system(.body, design: .monospaced)).textSelection(.enabled) }.padding() }.toolbar { Button(app.s.n("Готово")) { peerDetails = false } } } }
            .alert(app.s[.DeleteChat], isPresented: $deleting) { Button(app.s[.Delete], role: .destructive) { app.deleteContact(id); dismiss() }; Button(app.s[.Cancel], role: .cancel) {} }
            .sheet(item: $fullMessage) { message in NavigationStack { ScrollView { Text(message.text).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading).padding() }.navigationTitle(app.s[.Message]).toolbar { Button(app.s[.Close]) { fullMessage = nil }; Button(app.s[.Copy]) { app.copy(message.text) } } } }
    }
}
