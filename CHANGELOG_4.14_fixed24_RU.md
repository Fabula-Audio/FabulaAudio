# FabulaAudio 4.14_fixed24 — changelog

## Изменено

1. Добавлен `UpdateManager.kt`.
   - Фоновая проверка `version.json` через GitHub Raw.
   - Сравнение `latest_version` с `BuildConfig.VERSION_NAME`.
   - Диалог с кнопками «Скачать» и «Позже».
   - Скачивание APK через Android `DownloadManager`.
   - Открытие системной установки через `FileProvider`.
   - Проверка разрешения Android 8+ на установку неизвестных приложений.
   - Ошибки сети/HTTP/JSON/скачивания логируются и не показываются пользователю.

2. `AndroidManifest.xml`:
   - добавлен `INTERNET`;
   - добавлен `REQUEST_INSTALL_PACKAGES`;
   - добавлен `FileProvider`.

3. Добавлен `res/xml/file_paths.xml` для `FileProvider`.

4. В `MainActivity.onCreate()` после инициализации обработчиков добавлен:
   `UpdateManager(this).checkForUpdate()`.

5. `app/build.gradle`:
   - `versionCode` увеличен до `14`;
   - `versionName` установлен в `4.13`.

6. Добавлена подробная инструкция:
   `UPDATE_SETUP_RU.md`.

## Не изменялось

- аудиоцепочка подготовки → ×6 → ×144 → ×432;
- `adeclick` / `afftdn` и ползунки шумоподавления;
- live-переприменение подготовки;
- сохранение результатов через SAF;
- громкость сохранения;
- название и описание результата;
- кнопки и текущая палитра интерфейса;
- инструкция пользователя в приложении;
- выход с `exit_splash`;
- `FIT_CENTER`;
- `this.text = text`;
- предупреждение `showPreparationChangedWarning()`;
- иконка приложения;
- `smart-exception-java`;
- `setSingleLine(true)`;
- существующие FFmpeg-команды и логи `FABULA_CMD`, `FABULA_SAVE`, `FABULA_PLAY`, `FABULA_DUR`.

## Примечание

URL GitHub в `UpdateManager.kt` намеренно оставлен с плейсхолдерами `USERNAME` и `REPO`.
Перед первым использованием их нужно заменить на реальные данные GitHub-репозитория.
