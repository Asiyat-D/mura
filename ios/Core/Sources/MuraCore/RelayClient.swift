import Foundation

private final class NoRelayRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) { completionHandler(nil) }
}

public struct RelayClient: Sendable {
    public let identity: ChatIdentity
    public let base: URL
    private let session: URLSession
    public init(identity: ChatIdentity, server: String, allowLoopback: Bool = false) throws {
        self.identity = identity; base = try relayURL(server, allowLoopback: allowLoopback)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15; configuration.timeoutIntervalForResource = 25
        configuration.httpCookieStorage = nil; configuration.urlCache = nil
        session = URLSession(configuration: configuration, delegate: NoRelayRedirects(), delegateQueue: nil)
    }
    public func register() async throws { _ = try await request("POST", "/v1/register", body: chatJSON(identity.card(relay: base.absoluteString))) }
    public func allow(_ peer: ChatCard) async throws { _ = try await request("POST", "/v1/contacts", body: chatJSON(["peer": peer.id])) }
    public func revokeID(_ id: String) async throws { _ = try await request("POST", "/v1/revoke", body: chatJSON(["peer": id])) }
    public func send(_ envelope: ChatEnvelope) async throws { _ = try await request("POST", "/v1/messages", body: chatJSON(envelope)) }
    public func inbox() async throws -> [ChatEnvelope] { try JSONDecoder().decode([ChatEnvelope].self, from: await request("GET", "/v1/inbox")) }
    public func acknowledge(_ messages: [ChatEnvelope]) async throws { _ = try await request("POST", "/v1/ack", body: chatJSON(["receipts": messages.map { try identity.receipt(for: $0) }])) }
    public func statuses(_ ids: [String]) async throws -> [String: ChatDelivery] {
        guard !ids.isEmpty else { return [:] }
        return try JSONDecoder().decode([String: ChatDelivery].self, from: await request("POST", "/v1/status", body: chatJSON(["ids": ids])))
    }
    public func erase() async throws { _ = try await request("POST", "/v1/erase", body: Data("{}".utf8)) }
    public func request(_ method: String, _ path: String, body: Data = Data()) async throws -> Data {
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = method; if method != "GET" { request.httpBody = body }
        let device = try identity.id, timestamp = String(Int64(Date().timeIntervalSince1970))
        let nonce = UUID().uuidString.lowercased()
        let signed = Data("MURA-RELAY/1\n\(method)\n\(path)\n\(device)\n\(timestamp)\n\(nonce)\n\(chatHash(body))".utf8)
        request.setValue(device, forHTTPHeaderField: "X-Mura-Device")
        request.setValue(timestamp, forHTTPHeaderField: "X-Mura-Time")
        request.setValue(nonce, forHTTPHeaderField: "X-Mura-Nonce")
        request.setValue(try identity.sign(signed).base64EncodedString(), forHTTPHeaderField: "X-Mura-Signature")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            let (data, response) = try await session.data(for: request)
            guard data.count <= 4_000_000, let response = response as? HTTPURLResponse else { throw ChatError.offline }
            guard (200..<300).contains(response.statusCode) else {
                let code = (try? JSONDecoder().decode([String: String].self, from: data))?["error"] ?? "unavailable"
                throw ChatError.server(response.statusCode, code)
            }
            return data
        } catch let error as ChatError { throw error }
        catch is CancellationError { throw CancellationError() }
        catch { throw ChatError.offline }
    }
}
