import XCTest

@MainActor final class OnlineChatUITests: XCTestCase {
    func testEncryptedChatOverPublicInternetAndPersistence() async throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "kz.mura.ios")
        app.launch()
        openChats(app)
        app.buttons["my_contact"].tap()
        let own = app.staticTexts["own_contact_code"].firstMatch
        XCTAssertTrue(own.waitForExistence(timeout: 5))
        let phoneCard = try ChatCard.parse(own.label)
        app.navigationBars.buttons["Закрыть"].tap()

        let identity = ChatIdentity(), card = try identity.card(relay: NetworkDefaults.relay)
        let peer = try RelayClient(identity: identity, server: NetworkDefaults.relay)
        try await peer.register(); try await peer.allow(phoneCard)
        app.buttons["new_chat"].tap()
        let name = app.textFields["contact_name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5)); name.tap(); name.typeText("Network check")
        let field = app.descendants(matching: .any).matching(identifier: "contact_code").firstMatch
        field.tap(); field.typeText(try card.code)
        app.buttons["add_contact"].tap()

        let incoming = "Зашифрованный привет с другого клиента"
        let message = try identity.encrypt(incoming, to: phoneCard)
        for attempt in 0..<20 {
            do { try await peer.send(message); break }
            catch { if attempt == 19 { throw error }; try await Task.sleep(for: .seconds(1)) }
        }
        XCTAssertTrue(app.staticTexts[incoming].waitForExistence(timeout: 20))
        let draft = app.descendants(matching: .any).matching(identifier: "chat_input").firstMatch
        draft.tap(); draft.typeText("network reply 6899")
        app.buttons["save_message"].tap()
        var received = false
        for _ in 0..<20 {
            let inbox = try await peer.inbox()
            if let reply = inbox.first {
                XCTAssertEqual(try identity.decrypt(reply, from: phoneCard), "network reply 6899")
                try await peer.acknowledge([reply]); received = true; break
            }
            try await Task.sleep(for: .seconds(1))
        }
        XCTAssertTrue(received)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Доставлено")).firstMatch.waitForExistence(timeout: 15))
        app.terminate(); app.launch(); openChats(app)
        let contact = app.descendants(matching: .any).matching(identifier: "peer_" + card.id).firstMatch
        XCTAssertTrue(contact.waitForExistence(timeout: 10)); contact.tap()
        XCTAssertTrue(app.staticTexts[incoming].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["network reply 6899"].exists)

        app.buttons["delete_chat"].tap()
        app.alerts.buttons["Удалить"].tap()
        try await peer.erase()
    }
    private func openChats(_ app: XCUIApplication) {
        let unlock = app.buttons["Разблокировать"].firstMatch
        if unlock.waitForExistence(timeout: 2) { unlock.tap() }
        let chats = app.tabBars.buttons["Чаты"].firstMatch
        XCTAssertTrue(chats.waitForExistence(timeout: 45))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: chats)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 20), .completed)
        chats.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.buttons["my_contact"].waitForExistence(timeout: 10))
    }

}
