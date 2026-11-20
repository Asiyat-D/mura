import Foundation
import UIKit

struct NetworkSettings: Codable, Sendable {
    var relay = NetworkDefaults.relay
    var identity = ChatIdentity()
    var seen: [String: Double] = [:]
    var revoked: [String] = []
}



extension AppState {
    var myContactCode: String { (try? network?.identity.card(relay: network?.relay ?? "").code) ?? "" }
    var myFingerprint: String { (try? network?.identity.card(relay: network?.relay ?? "").fingerprint) ?? "" }
    func configureServer(_ text: String) {
        do {
            let address = try relayURL(text).absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard var settings = network else { throw MuraError.storage }
            settings.relay = address
            try store?.saveNetwork(settings); network = settings
            networkGeneration = UUID(); registeredServer = nil; allowedPeers = []
            networkConnected = false; networkStatus = "Подключение…"; networkFailure = nil
            Task { await syncNetwork() }
        } catch { networkFailure = error.localizedDescription }
    }
    func addContact(name: String, code: String) -> UUID? {
        do {
            let peer = try ChatCard.parse(code)
            guard var settings = network, let store else { throw MuraError.storage }
            guard peer.id != (try settings.identity.id) else { throw ChatError.ownContact }
            if peer.relay.trimmingCharacters(in: CharacterSet(charactersIn: "/")) != settings.relay.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
                if contacts.contains(where: { $0.peer != nil }) { throw ChatError.differentServer }
                _ = try relayURL(peer.relay); settings.relay = peer.relay
                networkGeneration = UUID(); registeredServer = nil; allowedPeers = []
            }
            settings.revoked.removeAll { $0 == peer.id }
            try store.saveNetwork(settings); network = settings
            let cleanName = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))
            let id = try store.addPeer(name: cleanName.isEmpty ? String(peer.id.prefix(8)) : cleanName, card: peer)
            try refresh(); networkFailure = nil
            Task { await syncNetwork() }; return id
        } catch { networkFailure = error.localizedDescription; return nil }
    }
    func runNetwork() async {
        while !Task.isCancelled {
            sceneActive = UIApplication.shared.applicationState == .active
            if sceneActive && (!biometric || !locked) { await syncNetwork() }
            do { try await Task.sleep(for: .seconds(4)) } catch { return }
        }
    }
    func syncNetwork() async {
        guard !syncing, sceneActive, (!biometric || !locked), let settings = network, let store else { return }
        guard !settings.relay.isEmpty else { networkStatus = "Укажите сервер в настройках"; return }
        syncing = true; defer { syncing = false }
        let generation = networkGeneration
        func current() throws { guard generation == networkGeneration, !Task.isCancelled else { throw CancellationError() } }
        do {
            let client = try RelayClient(identity: settings.identity, server: settings.relay)
            if registeredServer != settings.relay {
                try await client.register(); try current(); registeredServer = settings.relay; allowedPeers = []
            }
            for peer in contacts.compactMap(\.peer) where !allowedPeers.contains(peer.id) {
                try await client.allow(peer); try current(); allowedPeers.insert(peer.id)
            }
            for id in settings.revoked {
                try await client.revokeID(id); try current()
                if var value = network { value.revoked.removeAll { $0 == id }; try store.saveNetwork(value); network = value }
            }
            var queuedNotice: String?
            let pending = try store.networkMessages()
            for (id, envelope, _) in pending.filter({ $0.2 == "queued" }).prefix(20) {
                if Date().timeIntervalSince1970 - Double(envelope.sentAt) > 7 * 86400 { try store.delivery(id, "expired"); continue }
                do { try await client.send(envelope); try current(); try store.delivery(id, "sent") }
                catch let error as ChatError {
                    if case .server(let status, _) = error, status == 403 || status == 404 { queuedNotice = error.localizedDescription }
                    else { throw error }
                }
            }
            let received = try await client.inbox(); try current()
            var acknowledgements: [ChatEnvelope] = []
            for envelope in received {
                if network?.seen[envelope.id] != nil { acknowledgements.append(envelope); continue }
                guard let contact = contacts.first(where: { $0.peer?.id == envelope.sender }), let peer = contact.peer else { continue }
                let clear = try settings.identity.decrypt(envelope, from: peer)
                try store.receive(clear, contact: contact.id, envelope: envelope)
                guard var value = network else { throw MuraError.storage }
                value.seen = value.seen.filter { $0.value > Date().timeIntervalSince1970 - 8 * 86400 }
                value.seen[envelope.id] = Date().timeIntervalSince1970
                try store.saveNetwork(value); network = value
                acknowledgements.append(envelope)
            }
            if !acknowledgements.isEmpty { try await client.acknowledge(acknowledgements); try current() }
            let waiting = try store.networkMessages().filter { $0.2 == "sent" }.prefix(100)
            if !waiting.isEmpty {
                let states = try await client.statuses(waiting.map { $0.1.id }); try current()
                for (id, envelope, _) in waiting {
                    if states[envelope.id]?.state == "delivered" {
                        guard let receipt = states[envelope.id]?.receipt,
                              let peer = contacts.compactMap(\.peer).first(where: { $0.id == envelope.recipient }),
                              try receipt.verifies(envelope, recipient: peer) else { throw ChatError.invalidMessage }
                        try store.delivery(id, "delivered")
                    }
                    else if states[envelope.id]?.state == "missing" {
                        try store.delivery(id, Date().timeIntervalSince1970 - Double(envelope.sentAt) > 7 * 86400 ? "expired" : "queued")
                    }
                }
            }
            try refresh(); networkConnected = true; networkStatus = queuedNotice ?? "Подключено · сквозное шифрование"
        } catch is CancellationError { }
        catch {
            guard generation == networkGeneration else { return }
            if case ChatError.server(401, _) = error { registeredServer = nil; allowedPeers = [] }
            networkConnected = false; networkStatus = error.localizedDescription
            try? refresh()
        }
    }
    func deliveryLabel(_ value: String) -> String {
        switch value {
        case "queued": "В очереди"
        case "sent": "Отправлено"
        case "delivered": "Доставлено"
        case "received": "Получено"
        case "expired": "Срок доставки истёк"
        default: "Локально"
        }
    }
}
