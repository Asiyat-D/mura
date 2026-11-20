import Foundation
import zlib

public enum MuraError: String, Error, Equatable, Sendable {
    case empty, format, incomplete, corrupted, tooLong, passwordShort, authentication, storage, qrTooLong, biometric
}

/// Public reversible representation; CRC detects accidental damage and is not authentication.
public struct DecimalCodec: Sendable {
    public static let maximumBytes = 131_072
    public init() {}
    public func encode(_ text: String) throws -> String {
        guard !text.isEmpty else { throw MuraError.empty }
        let data = Data(text.utf8)
        guard data.count <= Self.maximumBytes else { throw MuraError.tooLong }
        let raw = "010" + String(format: "%08d", data.count) + decimalBytes(data) + String(format: "%010llu", checksum(data))
        return grouped(raw)
    }
    public func decode(_ code: String) throws -> String {
        let raw = try compact(code, limit: Self.maximumBytes * 3 + 21)
        guard raw.hasPrefix("010") else { throw MuraError.format }
        guard raw.count >= 24 else { throw MuraError.incomplete }
        let digits = Array(raw.utf8)
        guard let length = Int(String(decoding: digits[3..<11], as: UTF8.self)), (1...Self.maximumBytes).contains(length), digits.count == 21 + length * 3 else { throw MuraError.incomplete }
        let data = try parseDecimal(String(decoding: digits[11..<(digits.count - 10)], as: UTF8.self))
        guard let expected = UInt64(String(decoding: digits.suffix(10), as: UTF8.self)), checksum(data) == expected else { throw MuraError.corrupted }
        guard let text = String(data: data, encoding: .utf8) else { throw MuraError.corrupted }
        return text
    }
}
func checksum(_ data: Data) -> UInt64 {
    data.withUnsafeBytes { bytes in UInt64(zlib.crc32(0, bytes.bindMemory(to: Bytef.self).baseAddress, uInt(bytes.count))) }
}
func decimalBytes(_ data: Data) -> String {
    var result = String(); result.reserveCapacity(data.count * 3)
    for byte in data { result += String(format: "%03d", byte) }
    return result
}
func grouped(_ value: String) -> String {
    let bytes = Array(value.utf8)
    var result = [UInt8](); result.reserveCapacity(bytes.count * 4 / 3)
    for (index, byte) in bytes.enumerated() { if index > 0 && index % 3 == 0 { result.append(32) }; result.append(byte) }
    return String(decoding: result, as: UTF8.self)
}
func compact(_ code: String, limit: Int) throws -> String {
    guard code.utf8.count <= limit * 4 else { throw MuraError.tooLong }
    guard !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw MuraError.empty }
    var result = String(); result.reserveCapacity(min(code.utf8.count, limit))
    for scalar in code.unicodeScalars {
        if (48...57).contains(scalar.value) { result.unicodeScalars.append(scalar) }
        else if !CharacterSet.whitespacesAndNewlines.contains(scalar) { throw MuraError.format }
    }
    guard result.count <= limit else { throw MuraError.tooLong }
    return result
}
func parseDecimal(_ raw: String) throws -> Data {
    let values = Array(raw.utf8)
    guard !values.isEmpty && values.count % 3 == 0 else { throw MuraError.incomplete }
    var result = Data(); result.reserveCapacity(values.count / 3)
    for index in stride(from: 0, to: values.count, by: 3) {
        let group = values[index..<(index + 3)]
        guard group.allSatisfy({ (48...57).contains($0) }) else { throw MuraError.format }
        let value = Int(values[index] - 48) * 100 + Int(values[index + 1] - 48) * 10 + Int(values[index + 2] - 48)
        guard value <= 255 else { throw MuraError.format }
        result.append(UInt8(value))
    }
    return result
}
