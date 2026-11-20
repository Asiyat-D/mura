import Foundation
import MuraCore

struct Fixture: Codable {
    let androidIdentity: ChatIdentity
    let iosIdentity: ChatIdentity
    let iosCard: ChatCard
    let iosEnvelope: ChatEnvelope
}
struct Reply: Decodable { let reply: ChatEnvelope; let receipt: ChatReceipt; let androidCard: ChatCard }
let args = CommandLine.arguments
if args[1] == "generate" {
    let seed: [String: String] = ["signing": Data((1...32).map(UInt8.init)).base64EncodedString(), "agreement": Data((33...64).map(UInt8.init)).base64EncodedString()]
    let android = try JSONDecoder().decode(ChatIdentity.self, from: chatJSON(seed))
    let ios = ChatIdentity()
    let fixture = Fixture(androidIdentity: android, iosIdentity: ios, iosCard: try ios.card(relay: NetworkDefaults.relay), iosEnvelope: try ios.encrypt("iOS → Android: Привет, Сәлем! 👋", to: android.card(relay: NetworkDefaults.relay)))
    try chatJSON(fixture).write(to: URL(fileURLWithPath: args[2]))
    print("Apple CryptoKit fixture generated (public test keys)")
} else if args[1] == "verify" {
    let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: URL(fileURLWithPath: args[2])))
    let reply = try JSONDecoder().decode(Reply.self, from: Data(contentsOf: URL(fileURLWithPath: args[3])))
    guard try fixture.iosIdentity.decrypt(reply.reply, from: reply.androidCard) == "Android → iOS: ответ 6899 🔐",
          try reply.receipt.verifies(fixture.iosEnvelope, recipient: reply.androidCard) else { fatalError("Interop failed") }
    print("PASS: Android HPKE reply decrypted by Apple CryptoKit; Android receipt verified")
} else if args[1] == "relay" || args[1] == "release-relay" {
    let android: ChatCard
    let release = args[1] == "release-relay"
    if release {
        android = try ChatCard.parse(String(contentsOfFile: args[2], encoding: .utf8))
    } else {
        let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: URL(fileURLWithPath: args[2])))
        android = try fixture.androidIdentity.card(relay: NetworkDefaults.relay)
    }
    let ios = ChatIdentity()
    let client = try RelayClient(identity: ios, server: NetworkDefaults.relay)
    do {
        try await client.register(); try await client.allow(android)
        try chatJSON(ios.card(relay: NetworkDefaults.relay)).write(to: URL(fileURLWithPath: args[3]))
        var message: ChatEnvelope?
        for _ in 0..<90 {
            if let incoming = try await client.inbox().first {
                guard try ios.decrypt(incoming, from: android) == (release ? "Android release 6899" : "Android → iOS через интернет 👋") else { fatalError("Bad incoming") }
                try await client.acknowledge([incoming])
                message = try ios.encrypt(release ? "Release reply 6899" : "iOS → Android через интернет 🔐", to: android)
                try await client.send(message!)
                break
            }
            try await Task.sleep(for: .seconds(1))
        }
        guard let message else { fatalError("No Android message") }
        var delivered = false
        for _ in 0..<60 {
            let status = try await client.statuses([message.id])[message.id]
            if let receipt = status?.receipt, try receipt.verifies(message, recipient: android) { delivered = true; break }
            try await Task.sleep(for: .seconds(1))
        }
        guard delivered else { fatalError("No Android receipt") }
        try await client.erase()
        print("PASS: public HTTPS Android ↔ Apple CryptoKit; signed receipts both ways")
    } catch { try? await client.erase(); throw error }
}
