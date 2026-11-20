import XCTest
@testable import MuraCore

final class CoreTests: XCTestCase {
    let codec = DecimalCodec()
    func testLanguagesWhitespaceEmojiAndExactUnicode() throws {
        for text in ["ПРИВЕТ", "Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі", "Hello, 123!", "  \n\t🙂 👩🏽‍💻 𝄞\u{0}", "a\u{301}"] {
            XCTAssertEqual(Array(try codec.decode(codec.encode(text)).utf8), Array(text.utf8))
        }
        XCTAssertNotEqual(try codec.encode("a\u{301}"), try codec.encode("á"))
    }
    func testEveryUnicodeScalar() throws {
        var text = String(); var count = 0
        for point in UInt32(0)...UInt32(0x10ffff) {
            guard let scalar = UnicodeScalar(point) else { continue }
            text.unicodeScalars.append(scalar); count += 1
            if count == 8192 { XCTAssertEqual(Array(try codec.decode(codec.encode(text)).utf8), Array(text.utf8)); text = ""; count = 0 }
        }
        if !text.isEmpty { XCTAssertEqual(Array(try codec.decode(codec.encode(text)).utf8), Array(text.utf8)) }
    }
    func testGroupingMayChange() throws {
        let original = "Сәлем 🌍"
        let code = try codec.encode(original).replacingOccurrences(of: " ", with: "\n")
        XCTAssertEqual(try codec.decode(code), original)
        XCTAssertEqual(try codec.decode(code.replacingOccurrences(of: "\n", with: "")), original)
    }
    func testLongMessagesAndEmptyInput() throws {
        let text = String(repeating: "x", count: DecimalCodec.maximumBytes)
        XCTAssertEqual(try codec.decode(codec.encode(text)), text)
        check(.tooLong) { _ = try codec.encode(text + "x") }
        check(.empty) { _ = try codec.encode("") }
        check(.empty) { _ = try codec.decode(" \n ") }
    }
    func testInvalidAndIncompleteData() throws {
        check(.format) { _ = try codec.decode("123 abc") }
        check(.incomplete) { _ = try codec.decode("010000") }
        let code = try codec.encode("A").replacingOccurrences(of: " ", with: "")
        check(.incomplete) { _ = try codec.decode(String(code.dropLast())) }
        let modified = String(code.dropLast()) + (code.last == "9" ? "8" : "9")
        check(.corrupted) { _ = try codec.decode(modified) }
        let bytes = Array(code)
        check(.format) { _ = try codec.decode(String(bytes[0..<11]) + "999" + String(bytes.suffix(10))) }
    }
    func testPasswordRoundTripAndRandomization() throws {
        let cipher = PasswordCipher(), text = "Құпия, секрет! Hello 👩🏽‍💻\n 123", pass = "a unique long test password"
        let a = try cipher.encrypt(text, password: pass), b = try cipher.encrypt(text, password: pass)
        XCTAssertNotEqual(a, b)
        XCTAssertEqual(try PasswordCipher().decrypt(a, password: pass), text)
    }
    func testPasswordTamperingAndBoundaries() throws {
        let cipher = PasswordCipher(), pass = "a unique long test password"
        let code = try cipher.encrypt("private", password: pass)
        check(.authentication) { _ = try cipher.decrypt(code, password: "wrong password") }
        var groups = String(code.dropFirst(PasswordCipher.prefix.count)).split(separator: " ").map(String.init)
        groups[30] = String(format: "%03d", (Int(groups[30])! + 1) % 256)
        check(.authentication) { _ = try cipher.decrypt(PasswordCipher.prefix + groups.joined(separator: " "), password: pass) }
        check(.passwordShort) { _ = try cipher.encrypt("hello", password: "123") }
        check(.format) { _ = try cipher.decrypt("MURA2:000", password: pass) }
        check(.incomplete) { _ = try cipher.decrypt("MURA1:000", password: pass) }
    }
    func testFourDigitPassword() throws {
        let encrypted = try PasswordCipher().encrypt("Сәлем 👋", password: "6899")
        XCTAssertEqual(try PasswordCipher().decrypt(encrypted, password: "6899"), "Сәлем 👋")
    }
    func testAndroidCodesAreCompatibleWithIOS() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "android-vectors", withExtension: "json"))
        let json = try JSONDecoder().decode([String: String].self, from: Data(contentsOf: url))
        let text = String(decoding: try XCTUnwrap(Data(base64Encoded: json["textBase64"]!)), as: UTF8.self)
        let pass = String(decoding: try XCTUnwrap(Data(base64Encoded: json["passwordBase64"]!)), as: UTF8.self)
        XCTAssertEqual(try codec.encode(text), json["encoded"])
        XCTAssertEqual(try codec.decode(json["encoded"]!), text)
        XCTAssertEqual(try PasswordCipher().decrypt(json["encrypted"]!, password: pass), text)
    }
    private func check(_ expected: MuraError, _ work: () throws -> Void, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertThrowsError(try work(), file: file, line: line) { XCTAssertEqual($0 as? MuraError, expected, file: file, line: line) }
    }
}
