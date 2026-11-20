# Mura 1.1

Приложение для кодирования текста, парольного шифрования и интернет-переписки между **Android и iPhone**.

- [Установка APK и начало переписки на Android](docs/ANDROID_GUIDE_RU.md).
- [Первая установка и использование на iPhone](ios/docs/USER_GUIDE_RU.md).
- [Сервер на Mac](server/README.md), [проверки](docs/TESTING.md), [сеть](docs/NETWORK.md), [безопасность](docs/SECURITY.md).

**Скачать APK:** https://defense-arthritis-maritime-salvador.trycloudflare.com/download

Прямая ссылка: https://defense-arthritis-maritime-salvador.trycloudflare.com/download/mura.apk

Адрес временный: Mac должен быть включён, без сна и с интернетом. После перезапуска туннеля адрес может измениться.

## Возможности

Текст ↔ цифры, UTF-8 / Unicode / emoji, CRC32, парольные конверты `MURA1:` (AES-256-GCM, PBKDF2-SHA256 / 600000), пароли от 4 символов, включая `6899`. Форматы совместимы между платформами.

Интернет-чат: взаимное добавление по коду или QR, HPKE Auth на устройствах, постоянная зашифрованная очередь, проверяемые подписи квитанций. Для чата пароль не нужен. «Доставлено» подтверждает сохранение приложением получателя, а не прочтение человеком.

История, поиск, избранное, непрочитанные, темы, RU / KZ / EN, биометрическая блокировка, автоочистка, копирование, отправка текста / файла и QR. Старые локальные диалоги сохраняются как локальные записи.

Получение и повторы отправки работают при открытом и разблокированном приложении. Push / APNs / FCM и фоновая доставка отсутствуют. Сервер хранит неполученные зашифрованные сообщения до 7 дней.

## Android

Kotlin, Compose Material 3, Room 2, DataStore, Keystore. Android **8.0+**, minSdk 26, targetSdk 37; версия **1.1.0**, versionCode **2**. Для чата добавлен INTERNET; открытый HTTP запрещён.

Файл для распространения: **`artifacts/Mura-1.1.0.apk`**. Подписанная release-сборка, R8, `debuggable=false`. `artifacts/mura-debug.apk` оставлен для разработки с прежней тестовой подписью.

Проверенное окружение: JDK 25, Gradle 9.6.0, AGP 9.4.1, SDK 37 / build-tools 36.0.0. На другой машине укажите SDK в `local.properties` или `ANDROID_HOME`.

```sh
./gradlew :core:domain:test :app:assembleRelease :app:lintRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:connectedDebugAndroidTest
```

Release подписывается ключом из `~/Library/Application Support/MuraSigning/release.properties`. Ключ и пароли находятся вне проекта и не публикуются. Без локальной конфигурации release будет неподписанным. Для обновлений сохраняйте тот же ключ. [Подпись и упаковка](docs/ANDROID_DISTRIBUTION.md).

## iOS

[Проект SwiftUI / SwiftData / CryptoKit](ios/README.md) — `ios/Mura.xcodeproj`, iOS **17+**, версия 1.1.0. Development-сборка установлена на iPhone владельца; для нового iPhone нужна регистрация устройства и подпись. TestFlight / App Store пока не настроены.

## Сервер

Python + SQLite, loopback за HTTPS-туннелем Cloudflare. Пользовательские службы macOS: `kz.mura.relay` и `kz.mura.tunnel`.

```sh
python3 server/mac-service.py status
# Обновление сервера и публичного APK без перезапуска работающего туннеля:
python3 server/mac-service.py install
```

`scripts/package.sh` упаковывает исходники, APK и отчёты. Независимый аудит, Double Ratchet и forward secrecy при компрометации долговременного ключа получателя пока не реализованы.
