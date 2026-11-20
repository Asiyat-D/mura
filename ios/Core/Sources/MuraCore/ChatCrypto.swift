import Foundation
import CryptoKit

public enum ChatError: Error, LocalizedError, Sendable {
    case invalidContact, invalidServer, differentServer, ownContact, invalidMessage, offline, server(Int, String)
    public var errorDescription: String? {
        switch self {
        case .invalidContact: "Неверный код контакта. Скопируйте его целиком."
        case .invalidServer: "Укажите адрес сервера HTTPS без пути и параметров."
        case .differentServer: "Контакт использует другой сервер. Укажите тот же сервер в настройках."
        case .ownContact: "Это ваш собственный код. Добавьте код собеседника."
        case .invalidMessage: "Не удалось проверить защищённое сообщение."
        case .offline: "Нет связи с сервером. Сообщения остаются в очереди."
        case .server(_, let code): switch code {
            case "peer_not_ready": "Собеседник ещё не добавил ваш код контакта."
            case "recipient_missing": "Собеседник ещё не подключился к серверу."
            case "queue_full": "Очередь собеседника заполнена. Повторим отправку позже."
            case "rate_limited": "Слишком много запросов. Повторим подключение позже."
            default: "Сервер временно недоступен. Сообщения остаются в очереди."
        }
        }
    }
}

public func chatJSON<T: Encodable>(_ value: T) throws -> Data {
    let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
    return try encoder.encode(value)
}
public func chatHash(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }

public func relayURL(_ value: String, allowLoopback: Bool = false) throws -> URL {
    guard let c = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
          let host = c.host, !host.isEmpty, c.user == nil, c.password == nil, c.query == nil, c.fragment == nil,
          c.path.isEmpty || c.path == "/", value.count <= 240,
          c.scheme == "https" || (allowLoopback && c.scheme == "http" && ["127.0.0.1", "localhost"].contains(host)),
          let url = c.url else { throw ChatError.invalidServer }
    return url
}

/// Public keys are exchanged directly by contact code, never looked up from the relay.
public struct ChatCard: Codable, Equatable, Sendable {
    public let version: Int
    public let relay: String
    public let signingKey: Data
    public let agreementKey: Data
    public var id: String { chatHash(signingKey + agreementKey) }
    public var fingerprint: String { stride(from: 0, to: id.count, by: 4).map { String(Array(id)[$0..<min($0 + 4, id.count)]) }.joined(separator: " ") }
    public var code: String { get throws { "MURA-CONTACT1:" + (try chatJSON(self)).base64EncodedString() } }
    public static func parse(_ text: String) throws -> Self {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.hasPrefix("MURA-CONTACT1:"), text.utf8.count < 3000,
              let data = Data(base64Encoded: String(text.dropFirst(14))) else { throw ChatError.invalidContact }
        do { let card = try JSONDecoder().decode(Self.self, from: data); try card.validate(); return card }
        catch { throw ChatError.invalidContact }
    }
    public func validate(allowLoopback: Bool = false) throws {
        guard version == 1, signingKey.count == 32, agreementKey.count == 32 else { throw ChatError.invalidContact }
        _ = try relayURL(relay, allowLoopback: allowLoopback)
        _ = try Curve25519.Signing.PublicKey(rawRepresentation: signingKey)
        _ = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: agreementKey)
    }
}

/// Private material is serialized only into the device's encrypted, backup-excluded local store.
public struct ChatIdentity: Codable, Sendable {
    private let signing: Data
    private let agreement: Data
    public init() {
        signing = Curve25519.Signing.PrivateKey().rawRepresentation
        agreement = Curve25519.KeyAgreement.PrivateKey().rawRepresentation
    }
    public func card(relay: String) throws -> ChatCard {
        ChatCard(version: 1, relay: relay,
                 signingKey: try Curve25519.Signing.PrivateKey(rawRepresentation: signing).publicKey.rawRepresentation,
                 agreementKey: try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: agreement).publicKey.rawRepresentation)
    }
    public var id: String { get throws { try card(relay: "https://unused.invalid").id } }
    public func sign(_ bytes: Data) throws -> Data { try Curve25519.Signing.PrivateKey(rawRepresentation: signing).signature(for: bytes) }
    public func receipt(for envelope: ChatEnvelope) throws -> ChatReceipt {
        guard envelope.recipient == (try id) else { throw ChatError.invalidMessage }
        var receipt = ChatReceipt(id: envelope.id, sender: envelope.sender, recipient: envelope.recipient,
                                  messageHash: chatHash(try chatJSON(envelope)), signature: Data())
        receipt.signature = try sign(receipt.signedData)
        return receipt
    }

    public func encrypt(_ text: String, to peer: ChatCard, id: UUID = UUID(), date: Date = Date()) throws -> ChatEnvelope {
        guard !text.isEmpty else { throw MuraError.empty }
        guard text.utf8.count <= DecimalCodec.maximumBytes else { throw MuraError.tooLong }
        var envelope = ChatEnvelope(version: 1, id: id.uuidString.lowercased(), sender: try self.id, recipient: peer.id,
                                    sentAt: Int64(date.timeIntervalSince1970), encapsulatedKey: Data(), ciphertext: Data())
        var sender = try HPKE.Sender(recipientKey: Curve25519.KeyAgreement.PublicKey(rawRepresentation: peer.agreementKey),
                                     ciphersuite: .Curve25519_SHA256_ChachaPoly, info: Self.info,
                                     authenticatedBy: Curve25519.KeyAgreement.PrivateKey(rawRepresentation: agreement))
        envelope.encapsulatedKey = sender.encapsulatedKey
        envelope.ciphertext = try sender.seal(Data(text.utf8), authenticating: envelope.aad)
        return envelope
    }
    public func decrypt(_ envelope: ChatEnvelope, from peer: ChatCard) throws -> String {
        guard envelope.version == 1, envelope.sender == peer.id, envelope.recipient == (try id),
              UUID(uuidString: envelope.id) != nil, envelope.encapsulatedKey.count == 32,
              (17...(DecimalCodec.maximumBytes + 16)).contains(envelope.ciphertext.count) else { throw ChatError.invalidMessage }
        do {
            var recipient = try HPKE.Recipient(privateKey: Curve25519.KeyAgreement.PrivateKey(rawRepresentation: agreement),
                                               ciphersuite: .Curve25519_SHA256_ChachaPoly, info: Self.info,
                                               encapsulatedKey: envelope.encapsulatedKey,
                                               authenticatedBy: Curve25519.KeyAgreement.PublicKey(rawRepresentation: peer.agreementKey))
            let data = try recipient.open(envelope.ciphertext, authenticating: envelope.aad)
            guard let text = String(data: data, encoding: .utf8), !text.isEmpty else { throw ChatError.invalidMessage }
            return text
        } catch { throw ChatError.invalidMessage }
    }
    private static let info = Data("Mura/chat/1;HPKE-auth;X25519-HKDF-SHA256-ChaCha20Poly1305".utf8)
}

public struct ChatEnvelope: Codable, Sendable {
    public let version: Int
    public let id: String
    public let sender: String
    public let recipient: String
    public let sentAt: Int64
    public var encapsulatedKey: Data
    public var ciphertext: Data
    var aad: Data { Data("MURA-CHAT/1\n\(id)\n\(sender)\n\(recipient)\n\(sentAt)".utf8) }
}

public struct ChatReceipt: Codable, Sendable {
    public let id: String
    public let sender: String
    public let recipient: String
    public let messageHash: String
    public var signature: Data
    var signedData: Data { Data("MURA-RECEIPT/1\n\(id)\n\(sender)\n\(recipient)\n\(messageHash)".utf8) }
    public func verifies(_ envelope: ChatEnvelope, recipient peer: ChatCard) throws -> Bool {
        guard id == envelope.id, sender == envelope.sender, recipient == envelope.recipient, recipient == peer.id,
              messageHash == chatHash(try chatJSON(envelope)) else { return false }
        return try Curve25519.Signing.PublicKey(rawRepresentation: peer.signingKey).isValidSignature(signature, for: signedData)
    }
}

public struct ChatDelivery: Codable, Sendable {
    public let state: String
    public let receipt: ChatReceipt?
}
