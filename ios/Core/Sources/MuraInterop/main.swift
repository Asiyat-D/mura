import Foundation
import MuraCore
let text = "Сәлем, мир! Hello 🌍\n 123"
let password = "6899"
let result = ["textBase64": Data(text.utf8).base64EncodedString(), "passwordBase64": Data(password.utf8).base64EncodedString(), "encoded": try DecimalCodec().encode(text), "encrypted": try PasswordCipher().encrypt(text, password: password)]
let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
print(String(decoding: try encoder.encode(result), as: UTF8.self))
