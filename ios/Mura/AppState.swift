import SwiftUI
import Combine
import LocalAuthentication
import UniformTypeIdentifiers

@MainActor final class AppState: ObservableObject {
    @Published var language: String { didSet { UserDefaults.standard.set(language, forKey: "language") } }
    @Published var theme: String { didSet { UserDefaults.standard.set(theme, forKey: "theme") } }
    @Published var retention: Int { didSet { UserDefaults.standard.set(retention, forKey: "retention"); prune() } }
    @Published var biometric: Bool { didSet { UserDefaults.standard.set(biometric, forKey: "biometric") } }
    @Published var locked = true
    @Published var authenticating = false
    @Published var sceneActive = true
    @Published var selectedTab = 0
    @Published var input = ""
    @Published var output = ""
    @Published var password = ""
    @Published var mode: Mode = .encode
    @Published var busy = false
    @Published var failure: MuraError?
    @Published var notice: String?
    @Published var entries: [Entry] = []
    @Published var contacts: [Contact] = []
    @Published var messages: [Message] = []
    @Published var chatDrafts: [UUID: String] = [:]
    @Published var network: NetworkSettings?
    @Published var networkConnected = false
    @Published var networkStatus = "Подключение…"
    @Published var networkFailure: String?
    @Published var syncing = false
    var networkGeneration = UUID()
    var registeredServer: String?
    var allowedPeers: Set<String> = []
    var store: LocalStore?
    private var conversion: Task<Void, Never>?
    var s: Strings { Strings(language: language) }
    init() {
        let prefs = UserDefaults.standard
        language = prefs.string(forKey: "language") ?? "ru"; theme = prefs.string(forKey: "theme") ?? "system"
        retention = prefs.object(forKey: "retention") == nil ? 30 : prefs.integer(forKey: "retention")
        biometric = prefs.bool(forKey: "biometric")
        try? ShareFiles.cleanup()
        do { store = try LocalStore(); try store?.prune(days: retention); try refresh(); network = try store?.loadNetwork()
            if var settings = network, NetworkDefaults.previousRelays.contains(settings.relay) {
                settings.relay = NetworkDefaults.relay; try store?.saveNetwork(settings); network = settings
            } } catch { failure = .storage }
    }
    func refresh() throws {
        guard let store else { throw MuraError.storage }
        (entries, contacts, messages) = try store.load()
    }
    func perform(_ work: () throws -> Void) { do { try work(); try refresh() } catch { failure = (error as? MuraError) ?? .storage } }
    func changeMode(_ value: Mode) { guard !busy else { return }; mode = value; output = ""; password = ""; failure = nil }
    func setInput(_ value: String) { guard !busy else { return }; input = String(value.prefix(1_048_576)); output = ""; failure = nil }
    func clear() { guard !busy else { return }; input = ""; output = ""; password = ""; failure = nil }
    func importCode(_ value: String) { guard !busy else { return }; input = String(value.prefix(1_048_576)); output = ""; password = ""; mode = value.hasPrefix(PasswordCipher.prefix) ? .decrypt : .decode; selectedTab = 0 }
    func restore(_ entry: Entry) { guard !busy else { return }; mode = entry.mode; input = entry.source; output = entry.result; password = ""; selectedTab = 0 }
    func convert() {
        guard !busy else { return }
        let source = input, secret = password, selectedMode = mode
        busy = true; output = ""; failure = nil
        conversion = Task { [weak self] in
            do {
                let result = try await Task.detached(priority: .userInitiated) {
                    switch selectedMode {
                    case .encode: return try DecimalCodec().encode(source)
                    case .decode: return try DecimalCodec().decode(source)
                    case .encrypt: return try PasswordCipher().encrypt(source, password: secret)
                    case .decrypt: return try PasswordCipher().decrypt(source, password: secret)
                    }
                }.value
                try Task.checkCancellation()
                guard let self, let store = self.store else { throw MuraError.storage }
                try store.save(mode: selectedMode, source: source, result: result); try self.refresh()
                self.output = result; self.password = ""; self.busy = false
            } catch is CancellationError { self?.busy = false }
            catch { self?.busy = false; self?.failure = (error as? MuraError) ?? .storage }
        }
    }
    func favorite(_ id: UUID) { perform { try store?.toggleFavorite(id) } }
    func delete(_ id: UUID) { perform { try store?.delete(id) } }
    func deleteContact(_ id: UUID) {
        if let peer = contacts.first(where: { $0.id == id })?.peer, var settings = network {
            settings.revoked.append(peer.id)
            do { try store?.saveNetwork(settings); network = settings; allowedPeers.remove(peer.id) }
            catch { failure = .storage; return }
        }
        perform { try store?.deleteContact(id); chatDrafts.removeValue(forKey: id) }
        Task { await syncNetwork() }
    }
    func createContact(_ name: String) -> UUID? {
        do { guard let store else { throw MuraError.storage }; let id = try store.newContact(String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))); try refresh(); return id }
        catch { failure = .storage; return nil }
    }
    func send(_ text: String, to id: UUID, incoming: Bool) {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard text.utf8.count <= DecimalCodec.maximumBytes else { failure = .tooLong; return }
        if let peer = contacts.first(where: { $0.id == id })?.peer {
            guard let settings = network else { failure = .storage; return }
            do {
                let envelope = try settings.identity.encrypt(text, to: peer)
                try store?.queue(text, contact: id, envelope: envelope); chatDrafts[id] = ""; try refresh()
                Task { await syncNetwork() }
            } catch { networkFailure = error.localizedDescription }
        } else { perform { try store?.saveMessage(contact: id, text: text, incoming: incoming); chatDrafts[id] = ""; try store?.read(id) } }
    }
    func read(_ id: UUID) { perform { try store?.read(id) } }
    func prune() { guard store != nil else { return }; perform { try store?.prune(days: retention) } }
    func eraseAll() {
        conversion?.cancel(); conversion = nil; busy = false
        let old = network; networkGeneration = UUID(); registeredServer = nil; allowedPeers = []
        perform {
            try store?.eraseAll(); clear(); chatDrafts = [:]; try ShareFiles.erase()
            var settings = NetworkSettings(); settings.relay = old?.relay ?? NetworkDefaults.relay
            try store?.saveNetwork(settings); network = settings
        }
        if let old, let client = try? RelayClient(identity: old.identity, server: old.relay) { Task { try? await client.erase() } }
    }
    func copy(_ text: String) {
        UIPasteboard.general.setItems([[UTType.utf8PlainText.identifier: text]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(120)])
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        notice = s[.Copied]
        Task { try? await Task.sleep(for: .seconds(2)); notice = nil }
    }
    func authenticate(enable: Bool = false) {
        let context = LAContext(); var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else { failure = .biometric; return }
        authenticating = true
        Task {
            defer { authenticating = false; context.invalidate() }
            do {
                guard try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: s[.Authenticate]) else { return }
                locked = false; if enable { biometric = true }
            } catch { notice = s[.Locked] }
        }
    }
}
@MainActor enum ShareFiles {
    static var directory: URL { URL.cachesDirectory.appending(path: "MuraShares", directoryHint: .isDirectory) }
    static func cleanup() throws {
        guard FileManager.default.fileExists(atPath: directory.path) else { return }
        for file in try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey]) {
            let modified = try file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate ?? .distantPast
            if modified < Date().addingTimeInterval(-3600) { try FileManager.default.removeItem(at: file) }
        }
    }
    static func payload(_ text: String) throws -> [Any] {
        try cleanup()
        guard text.utf8.count > 100_000 else { return [text] }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appending(path: "mura-\(UUID()).txt")
        try Data(text.utf8).write(to: url, options: [.atomic, .completeFileProtection])
        return [url]
    }
    static func erase() throws { if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) } }
}
