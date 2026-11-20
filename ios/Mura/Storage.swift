import Foundation
import SwiftData
import Security
import CryptoKit

@Model final class StoredRecord {
    @Attribute(.unique) var id: UUID
    var kind: String
    var mode: String
    var source: Data
    var result: Data
    var createdAt: Date
    var favorite: Bool
    var conversationID: UUID?
    var incoming: Bool
    var unread: Bool
    var networkEnvelope: Data? = nil
    var delivery: String = "local"
    init(kind: String, mode: String = "encode", source: Data, result: Data = Data(), conversationID: UUID? = nil, incoming: Bool = false) {
        id = UUID(); self.kind = kind; self.mode = mode; self.source = source; self.result = result
        createdAt = Date(); favorite = false; self.conversationID = conversationID; self.incoming = incoming; unread = incoming
    }
}
@Model final class StoredContact {
    @Attribute(.unique) var id: UUID
    var name: Data
    var networkCard: Data? = nil
    var createdAt: Date
    init(id: UUID, name: Data) { self.id = id; self.name = name; createdAt = Date() }
}
struct Entry: Identifiable, Sendable { let id: UUID; let mode: Mode; let source: String; let result: String; let date: Date; let favorite: Bool }
struct Contact: Identifiable, Sendable { let id: UUID; let name: String; let date: Date; let peer: ChatCard? }
struct Message: Identifiable, Sendable { let id: UUID; let contactID: UUID; let text: String; let incoming: Bool; let date: Date; let favorite: Bool; let unread: Bool; let delivery: String }
enum Mode: String, CaseIterable, Sendable {
    case encode, decode, encrypt, decrypt
    var secure: Bool { self == .encrypt || self == .decrypt }
    var reverse: Bool { self == .decode || self == .decrypt }
    var label: T { switch self { case .encode: .EncodingLabel; case .decode: .DecodingLabel; case .encrypt: .EncryptedLabel; case .decrypt: .DecryptedLabel } }
}

/// Per-installation key; explicitly device-only and excluded from iCloud Keychain synchronization.
final class LocalVault {
    private var key: SymmetricKey
    private static let service = "kz.mura.ios.local.v1"
    private static var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "storage", kSecAttrSynchronizable as String: false] }
    init(allowCreation: Bool) throws {
        if allowCreation && !UserDefaults.standard.bool(forKey: "mura.installation") { try Self.removeKey() }
        var query = Self.query
        query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var found: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &found)
        if status == errSecSuccess, let data = found as? Data, data.count == 32 { key = SymmetricKey(data: data) }
        else if status == errSecItemNotFound && allowCreation { key = try Self.createKey() }
        else { throw MuraError.storage }
        UserDefaults.standard.set(true, forKey: "mura.installation")
    }
    func seal(_ text: String, context: String) throws -> Data {
        guard let combined = try AES.GCM.seal(Data(text.utf8), using: key, authenticating: Data(context.utf8)).combined else { throw MuraError.storage }
        return combined
    }
    func open(_ data: Data, context: String) throws -> String {
        let clear = try AES.GCM.open(AES.GCM.SealedBox(combined: data), using: key, authenticating: Data(context.utf8))
        guard let text = String(data: clear, encoding: .utf8) else { throw MuraError.storage }
        return text
    }
    func rotate() throws { try Self.removeKey(); key = try Self.createKey() }
    private static func removeKey() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw MuraError.storage }
    }
    private static func createKey() throws -> SymmetricKey {
        var data = Data(count: 32)
        let status = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard status == errSecSuccess else { throw MuraError.storage }
        var values = query; values[kSecValueData as String] = data; values[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        guard SecItemAdd(values as CFDictionary, nil) == errSecSuccess else { throw MuraError.storage }
        return SymmetricKey(data: data)
    }
}

@MainActor final class LocalStore {
    private let container: ModelContainer
    private let context: ModelContext
    private let vault: LocalVault
    init() throws {
        var directory = URL.applicationSupportDirectory.appending(path: "Mura", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true; try directory.setResourceValues(values)
        let url = directory.appending(path: "mura.store")
        let schema = Schema([StoredRecord.self, StoredContact.self])
        container = try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, url: url, cloudKitDatabase: .none)])
        context = ModelContext(container); context.autosaveEnabled = false
        let hasData = try context.fetchCount(FetchDescriptor<StoredRecord>()) > 0 || context.fetchCount(FetchDescriptor<StoredContact>()) > 0
        vault = try LocalVault(allowCreation: !hasData)
    }
    private func records() throws -> [StoredRecord] { try context.fetch(FetchDescriptor<StoredRecord>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)])) }
    func load() throws -> ([Entry], [Contact], [Message]) {
        let all = try records()
        let entries = try all.filter { $0.kind == "entry" }.map { record in
            guard let mode = Mode(rawValue: record.mode) else { throw MuraError.storage }
            return Entry(id: record.id, mode: mode, source: try vault.open(record.source, context: "entry/source/\(record.mode)"), result: try vault.open(record.result, context: "entry/result/\(record.mode)"), date: record.createdAt, favorite: record.favorite)
        }
        let contacts = try context.fetch(FetchDescriptor<StoredContact>()).map { contact in
            let peer = try contact.networkCard.map { try JSONDecoder().decode(ChatCard.self, from: Data(vault.open($0, context: "contact/card/\(contact.id)").utf8)) }
            return Contact(id: contact.id, name: try vault.open(contact.name, context: "contact/\(contact.id)"), date: contact.createdAt, peer: peer)
        }
        let messages = try all.filter { $0.kind == "message" }.reversed().map { record in
            guard let contactID = record.conversationID else { throw MuraError.storage }
            return Message(id: record.id, contactID: contactID, text: try vault.open(record.source, context: "message/\(contactID)/\(record.incoming)"), incoming: record.incoming, date: record.createdAt, favorite: record.favorite, unread: record.unread, delivery: record.delivery)
        }
        return (entries, contacts, messages)
    }
    func save(mode: Mode, source: String, result: String) throws {
        context.insert(StoredRecord(kind: "entry", mode: mode.rawValue, source: try vault.seal(source, context: "entry/source/\(mode.rawValue)"), result: try vault.seal(result, context: "entry/result/\(mode.rawValue)")))
        try context.save()
    }
    func newContact(_ name: String) throws -> UUID {
        let id = UUID(); context.insert(StoredContact(id: id, name: try vault.seal(name, context: "contact/\(id)"))); try context.save(); return id
    }
    func saveMessage(contact: UUID, text: String, incoming: Bool) throws {
        context.insert(StoredRecord(kind: "message", source: try vault.seal(text, context: "message/\(contact)/\(incoming)"), conversationID: contact, incoming: incoming)); try context.save()
    }
    func toggleFavorite(_ id: UUID) throws { if let record = try records().first(where: { $0.id == id }) { record.favorite.toggle(); try context.save() } }
    func delete(_ id: UUID) throws { if let record = try records().first(where: { $0.id == id }) { context.delete(record); try context.save() } }
    func deleteContact(_ id: UUID) throws {
        for record in try records() where record.conversationID == id { context.delete(record) }
        for contact in try context.fetch(FetchDescriptor<StoredContact>()) where contact.id == id { context.delete(contact) }
        try context.save()
    }
    func read(_ id: UUID) throws { for record in try records() where record.conversationID == id && record.unread { record.unread = false }; try context.save() }
    func prune(days: Int) throws {
        guard days > 0 else { return }
        let cutoff = Date().addingTimeInterval(-Double(days) * 86_400)
        for record in try records() where !record.favorite && record.delivery != "queued" && record.delivery != "sent" && record.createdAt < cutoff { context.delete(record) }
        try context.save()
    }
    func eraseAll() throws {
        for record in try records() { context.delete(record) }
        for contact in try context.fetch(FetchDescriptor<StoredContact>()) { context.delete(contact) }
        try context.save(); if FileManager.default.fileExists(atPath: networkFile.path) { try FileManager.default.removeItem(at: networkFile) }; try vault.rotate()
    }
    private var networkFile: URL { URL.applicationSupportDirectory.appending(path: "Mura/network.sealed") }
    func loadNetwork() throws -> NetworkSettings {
        if FileManager.default.fileExists(atPath: networkFile.path) {
            let plain = try vault.open(Data(contentsOf: networkFile), context: "network/v1")
            return try JSONDecoder().decode(NetworkSettings.self, from: Data(plain.utf8))
        }
        guard try context.fetch(FetchDescriptor<StoredContact>()).allSatisfy({ $0.networkCard == nil }),
              try records().allSatisfy({ $0.networkEnvelope == nil }) else { throw MuraError.storage }
        let settings = NetworkSettings(); try saveNetwork(settings); return settings
    }
    func saveNetwork(_ settings: NetworkSettings) throws {
        let text = String(decoding: try chatJSON(settings), as: UTF8.self)
        try vault.seal(text, context: "network/v1").write(to: networkFile, options: [.atomic, .completeFileProtection])
    }
    func addPeer(name: String, card: ChatCard) throws -> UUID {
        let existing = try context.fetch(FetchDescriptor<StoredContact>()).first { contact in
            guard let data = contact.networkCard, let text = try? vault.open(data, context: "contact/card/\(contact.id)"),
                  let value = try? JSONDecoder().decode(ChatCard.self, from: Data(text.utf8)) else { return false }
            return value.id == card.id
        }
        let id = existing?.id ?? UUID()
        let contact = existing ?? StoredContact(id: id, name: Data())
        contact.name = try vault.seal(name, context: "contact/\(id)")
        contact.networkCard = try vault.seal(String(decoding: chatJSON(card), as: UTF8.self), context: "contact/card/\(id)")
        if existing == nil { context.insert(contact) }
        try context.save(); return id
    }
    func queue(_ text: String, contact: UUID, envelope: ChatEnvelope) throws {
        let record = StoredRecord(kind: "message", source: try vault.seal(text, context: "message/\(contact)/false"), conversationID: contact)
        record.id = UUID(uuidString: envelope.id)!; record.delivery = "queued"
        record.networkEnvelope = try vault.seal(String(decoding: chatJSON(envelope), as: UTF8.self), context: "envelope/\(record.id)")
        context.insert(record); try context.save()
    }
    func networkMessages() throws -> [(UUID, ChatEnvelope, String)] {
        try records().filter { !$0.incoming && $0.networkEnvelope != nil }.map { record in
            let text = try vault.open(record.networkEnvelope!, context: "envelope/\(record.id)")
            return (record.id, try JSONDecoder().decode(ChatEnvelope.self, from: Data(text.utf8)), record.delivery)
        }.reversed()
    }
    func delivery(_ id: UUID, _ status: String) throws {
        guard let record = try records().first(where: { $0.id == id }), record.delivery != status else { return }
        record.delivery = status
        if status == "delivered" || status == "expired" { record.networkEnvelope = nil }
        try context.save()
    }
    func receive(_ text: String, contact: UUID, envelope: ChatEnvelope) throws {
        guard let id = UUID(uuidString: envelope.id) else { throw ChatError.invalidMessage }
        guard try !records().contains(where: { $0.id == id }) else { return }
        let record = StoredRecord(kind: "message", source: try vault.seal(text, context: "message/\(contact)/true"), conversationID: contact, incoming: true)
        record.id = id; record.createdAt = Date(timeIntervalSince1970: Double(envelope.sentAt)); record.delivery = "received"
        context.insert(record); try context.save()
    }

}
