import SwiftUI

struct AddContactView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let added: (UUID) -> Void
    @State private var name = ""
    @State private var code = ""
    @State private var scanning = false
    var body: some View {
        NavigationStack {
            Form {
                Section { Text(app.s.n("Каждый из вас добавляет код другого. В коде находятся открытые ключи и адрес сервера.")).font(.footnote) }
                Section(app.s.n("Собеседник")) {
                    TextField(app.s[.Name], text: $name).accessibilityIdentifier("contact_name")
                    TextField("MURA-CONTACT1:…", text: $code, axis: .vertical).lineLimit(3...6).autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityIdentifier("contact_code")
                    Button { scanning = true } label: { Label(app.s[.Scan], systemImage: "qrcode.viewfinder") }
                }
                Section { Button(app.s.n("Добавить контакт")) { if let id = app.addContact(name: name, code: code) { added(id) } }.disabled(code.isEmpty).accessibilityIdentifier("add_contact") }
            }.navigationTitle(app.s[.NewChat]).toolbar { Button(app.s[.Cancel]) { dismiss() } }
                .sheet(isPresented: $scanning) { ScannerView { value in code = value; scanning = false }.ignoresSafeArea() }
        }
    }
}

struct NetworkProfileView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var qr = false
    @State private var sharing = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 21) {
                    Label(app.s.n("Мой код контакта"), systemImage: "person.crop.circle").font(.title2.bold())
                    Text(app.s.n("Отправьте этот код собеседнику и добавьте его код у себя. Код не содержит пароля или закрытых ключей."))
                    Text(app.myContactCode).font(.system(.caption2, design: .monospaced)).lineLimit(5).textSelection(.enabled).accessibilityIdentifier("own_contact_code")
                    HStack { Button { app.copy(app.myContactCode) } label: { Label(app.s[.Copy], systemImage: "doc.on.doc") }; Spacer(); Button { sharing = true } label: { Label(app.s[.Share], systemImage: "square.and.arrow.up") }; Spacer(); Button { qr = true } label: { Label("QR", systemImage: "qrcode") } }.buttonStyle(.bordered).disabled(app.network?.relay.isEmpty != false)
                    Text(app.s.n("Мой код безопасности")).font(.headline)
                    Text(app.myFingerprint).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    Text(app.s.n(app.networkStatus)).font(.footnote).foregroundStyle(.secondary)
                    Text(app.s.n("Сообщения ожидают получателя на сервере до 7 дней. Mura получает их при открытом приложении.")).font(.footnote).foregroundStyle(.secondary)
                }.padding(24)
            }.navigationTitle(app.s.n("Контакт")).toolbar { Button(app.s[.Close]) { dismiss() } }
                .sheet(isPresented: $qr) { QRView(code: app.myContactCode) }
                .sheet(isPresented: $sharing) { ShareSheet(items: [app.myContactCode]) }
        }
    }
}

struct NetworkSettingsSection: View {
    @EnvironmentObject var app: AppState
    @State private var server = ""
    @State private var profile = false
    var body: some View {
        Section {
            Label(app.s.n(app.networkStatus), systemImage: "lock.shield").font(.footnote)
            TextField("https://…", text: $server).keyboardType(.URL).autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityIdentifier("relay_server")
            Button(app.s.n("Подключить сервер")) { app.configureServer(server) }.accessibilityIdentifier("connect_relay")
            Button(app.s.n("Мой код контакта")) { profile = true }
        } header: { Text(app.s.n("ПЕРЕПИСКА ЧЕРЕЗ ИНТЕРНЕТ")) }
          footer: { Text(app.s.n("На обоих iPhone должен быть указан один сервер. Для текущего сервера этот Mac должен оставаться включённым и подключённым к интернету.")) }
          .onAppear { server = app.network?.relay ?? "" }
          .sheet(isPresented: $profile) { NetworkProfileView() }
    }
}
