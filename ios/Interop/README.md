# Межплатформенные тесты

`Core/Tests/MuraCoreTests/Fixtures/android-vectors.json` создан настоящими `DecimalCodec` / `PasswordCipher` из Kotlin/JVM-модуля Android-проекта. Пароль в fixture — публичные тестовые данные.

Swift XCTest проверяет одинаковый цифровой формат и расшифровывает Android-конверт.

Обратное направление:

```sh
swift run --package-path Core MuraInterop > /tmp/mura-ios-vectors.json
java -cp "$MURA_ANDROID_CLASSPATH" Interop/MuraVerifyIOS.java /tmp/mura-ios-vectors.json
```

`MURA_ANDROID_CLASSPATH` должен содержать собранный `core/domain/build/libs/domain.jar` Android-проекта и `kotlin-stdlib` соответствующей версии. Верификатор декодирует iOS-результат теми же классами, которые использует Android-приложение. Он не выводит тестовый пароль или текст сообщения, только результаты проверки.
