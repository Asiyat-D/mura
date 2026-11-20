# Подписанный Android APK

Файл: `artifacts/Mura-1.1.0.apk`. applicationId `kz.mura.app`, versionName `1.1.0`, versionCode `2`, minSdk 26, targetSdk 37. Release: `debuggable=false`, R8 и сокращение ресурсов. Подпись APK Signature Scheme v2, RSA 3072.

Постоянный ключ: `~/Library/Application Support/MuraSigning/mura-release.p12`; конфигурация и пароли: соседний `release.properties`. Каталог 0700, файлы 0600. Они находятся вне проекта и публичной папки. Сохраните защищённую резервную копию ключа и конфигурации: обновления должны подписываться тем же ключом. [Документация Android](https://developer.android.com/studio/publish/app-signing).

SHA-256 сертификата:

```text
aed71acfb600a7f24f4842aa4f61795d368d1d79f7c8ffa10da68cdc674a196c
```

Сборка: `./gradlew :app:assembleRelease :app:lintRelease`. Результат: `app/build/outputs/apk/release/app-release.apk`. На другом Mac без локального файла подписи release-сборка будет неподписанной. Для обновлений нужно безопасно перенести существующий ключ или настроить подписание через собственный процесс.

`scripts/package.sh` упаковывает APK, исходники и отчёты. `MURA_SKIP_VALIDATED_CHECKS=1` позволяет упаковать уже проверенные сборки без повторного запуска проверок. Закрытые ключи, пароли, runtime, базы, кэши, публичный APK и сырые результаты Xcode исключаются из архива исходников.

Страница `/download` и файл `/download/mura.apk` обслуживаются тем же HTTPS-туннелем, что чат. Сервер разрешает только фиксированные имена `index.html`, `mura.apk` и `SHA256SUMS`; к базе, ключам, исходникам и логам доступа нет. Проверка SHA-256 относится к байтам APK; подпись проверяется `apksigner`.

Для выкладки положите подписанный APK в `server/public/mura.apk`, рядом обновите SHA256SUMS, затем выполните `python3 server/mac-service.py install`. Работающий туннель остаётся запущенным. В постоянный репозиторий APK не включается.

Debug-сборка подписана отдельным тестовым ключом и не обновляется release-подписью поверх себя. Перед удалением старого тестового приложения сохраните нужные локальные данные.
