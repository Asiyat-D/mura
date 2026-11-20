import XCTest
@testable import MuraCore

final class ChatTests: XCTestCase {
    func testContactKeysAndHTTPS() throws {
        let card = try ChatIdentity().card(relay: "https://relay.example")
        XCTAssertEqual(try ChatCard.parse(card.code), card)
        XCTAssertEqual(card.id.count, 64)
        XCTAssertThrowsError(try ChatCard.parse("MURA-CONTACT1:bad"))
        for address in ["http://relay.example", "https://user:password@relay.example", "https://relay.example/path", "https://relay.example?secret=1"] {
            XCTAssertThrowsError(try relayURL(address))
        }
    }
    func testAuthenticatedEncryptionAndTampering() throws {
        let alice = ChatIdentity(), bob = ChatIdentity(), mallory = ChatIdentity()
        let a = try alice.card(relay: "https://relay.example"), b = try bob.card(relay: a.relay)
        let text = "Сәлем! Привет 👋\n 123"
        let message = try alice.encrypt(text, to: b)
        XCTAssertEqual(try bob.decrypt(message, from: a), text)
        XCTAssertThrowsError(try mallory.decrypt(message, from: a))
        XCTAssertThrowsError(try bob.decrypt(message, from: mallory.card(relay: a.relay)))
        var damaged = message; damaged.ciphertext[damaged.ciphertext.startIndex] ^= 1
        XCTAssertThrowsError(try bob.decrypt(damaged, from: a))
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: chatJSON(message)) as? [String: Any])
        json["sentAt"] = message.sentAt + 1
        let changed = try JSONDecoder().decode(ChatEnvelope.self, from: JSONSerialization.data(withJSONObject: json))
        XCTAssertThrowsError(try bob.decrypt(changed, from: a))
        XCTAssertNotEqual(try alice.encrypt(text, to: b).ciphertext, message.ciphertext)
    }
    func testReceiptCannotBeForgedOrReused() throws {
        let alice = ChatIdentity(), bob = ChatIdentity(), mallory = ChatIdentity()
        let card = try bob.card(relay: "https://relay.example")
        let envelope = try alice.encrypt("hello", to: card)
        var receipt = try bob.receipt(for: envelope)
        XCTAssertTrue(try receipt.verifies(envelope, recipient: card))
        XCTAssertFalse(try receipt.verifies(alice.encrypt("other", to: card), recipient: card))
        receipt.signature = try mallory.sign(receipt.signedData)
        XCTAssertFalse(try receipt.verifies(envelope, recipient: card))
    }
    func testRelayDeliveryAndAcknowledgement() async throws {
        guard let address = ProcessInfo.processInfo.environment["MURA_TEST_RELAY_URL"] else { throw XCTSkip("Set MURA_TEST_RELAY_URL to run live relay integration") }
        let alice = ChatIdentity(), bob = ChatIdentity()
        let a = try alice.card(relay: address), b = try bob.card(relay: address)
        let left = try RelayClient(identity: alice, server: address, allowLoopback: true)
        let right = try RelayClient(identity: bob, server: address, allowLoopback: true)
        try await left.register(); try await right.register(); try await left.allow(b)
        let message = try alice.encrypt("Зашифрованный привет 🌍", to: b)
        do { try await left.send(message); XCTFail("Both people must add the contact") }
        catch ChatError.server(let status, let code) { XCTAssertEqual(status, 403); XCTAssertEqual(code, "peer_not_ready") }
        try await right.allow(a)
        try await left.send(message); try await left.send(message)
        let inbox = try await right.inbox()
        XCTAssertEqual(inbox.count, 1)
        XCTAssertEqual(try bob.decrypt(XCTUnwrap(inbox.first), from: a), "Зашифрованный привет 🌍")
        try await right.acknowledge([message])
        let status = try await left.statuses([message.id])
        XCTAssertEqual(status[message.id]?.state, "delivered")
        XCTAssertTrue(try XCTUnwrap(status[message.id]?.receipt).verifies(message, recipient: b))
        let empty = try await right.inbox(); XCTAssertTrue(empty.isEmpty)
        let reply = try bob.encrypt("Ответ с другого клиента", to: a)
        try await right.send(reply)
        let replies = try await left.inbox()
        XCTAssertEqual(try alice.decrypt(XCTUnwrap(replies.first), from: b), "Ответ с другого клиента")
        try await left.erase(); try await right.erase()
    }
}
