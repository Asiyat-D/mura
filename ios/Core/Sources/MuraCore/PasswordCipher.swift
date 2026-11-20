import Foundation
import CryptoKit
import CommonCrypto
import Security

/// Wire-compatible with the Android MURA1 envelope. All crypto primitives come from Apple.
public struct PasswordCipher: Sendable {
    public static let prefix = "MURA1:"
    private static let aad = Data("Mura/password/v1;PBKDF2-SHA256/600000;AES256-GCM".utf8)
    public init() {}
    public func encrypt(_ text: String, password: String) throws -> String {
        guard !text.isEmpty else { throw MuraError.empty }
        guard password.utf16.count >= 4 else { throw MuraError.passwordShort }
        let clear = Data(text.utf8)
        guard clear.count <= DecimalCodec.maximumBytes else { throw MuraError.tooLong }
        let salt = try Self.random(16)
        let nonceBytes = try Self.random(12)
        let key = try derive(password, salt: salt)
        let sealed = try AES.GCM.seal(clear, using: key, nonce: AES.GCM.Nonce(data: nonceBytes), authenticating: Self.aad)
        return Self.prefix + grouped(decimalBytes(salt + nonceBytes + sealed.ciphertext + sealed.tag))
    }
    public func decrypt(_ envelope: String, password: String) throws -> String {
        guard envelope.hasPrefix(Self.prefix) else { throw MuraError.format }
        guard !password.isEmpty else { throw MuraError.passwordShort }
        let bytes = try parseDecimal(compact(String(envelope.dropFirst(Self.prefix.count)), limit: (DecimalCodec.maximumBytes + 44) * 3))
        guard bytes.count >= 45 else { throw MuraError.incomplete }
        do {
            let key = try derive(password, salt: bytes.prefix(16))
            let nonce = try AES.GCM.Nonce(data: bytes[16..<28])
            let sealed = try AES.GCM.SealedBox(nonce: nonce, ciphertext: bytes[28..<(bytes.count - 16)], tag: bytes.suffix(16))
            let clear = try AES.GCM.open(sealed, using: key, authenticating: Self.aad)
            guard let text = String(data: clear, encoding: .utf8) else { throw MuraError.corrupted }
            return text
        } catch let error as MuraError { throw error }
        catch { throw MuraError.authentication }
    }
    private func derive(_ password: String, salt: Data) throws -> SymmetricKey {
        var output = [UInt8](repeating: 0, count: 32)
        var passwordBytes = Array(password.utf8)
        defer { _ = passwordBytes.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) }; _ = output.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) } }
        let status = passwordBytes.withUnsafeBytes { pass in salt.withUnsafeBytes { saltBytes in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pass.bindMemory(to: Int8.self).baseAddress, pass.count, saltBytes.bindMemory(to: UInt8.self).baseAddress, salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 600_000, &output, output.count)
        } }
        guard status == kCCSuccess else { throw MuraError.storage }
        return SymmetricKey(data: output)
    }
    static func random(_ count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else { throw MuraError.storage }
        return Data(bytes)
    }
}
