# Fabula Audio 4.15_fixed26 — changelog

## Изменённые файлы

### `app/src/main/java/com/fabula/audio/MainActivity.kt`
- В меню шапки добавлен пункт **«О приложении»** последним после **«Папка сохранения»**.
- Добавлен `showAboutDialog()`.
- Диалог имеет заголовок **«О приложении и лицензиях»**.
- Содержимое размещено в `ScrollView` + `TextView`.
- Текст `TextView` выделяемый (`textIsSelectable = true`).
- Добавлена кнопка **«Закрыть»**.
- Текст диалога внесён в соответствии с заданным текстом.

### `app/build.gradle`
- `versionCode`: `15` → `16`.
- `versionName`: `4.14` → `4.15`.

## Не изменялось

Остальной исходный проект сохранён без изменений: аудио-цепочка FFmpeg, UI и его раскладка,
`UpdateManager.kt`, `AndroidManifest.xml`, подпись Release, иконка, `exit_splash`, палитра,
инструкция, предупреждение, зависимости, `gradle.properties`, совместимость Gradle 8.7 / JDK 21.
