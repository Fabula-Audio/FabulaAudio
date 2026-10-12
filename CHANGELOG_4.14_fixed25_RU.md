# Fabula Audio — 4.14_fixed25

## Цель версии

Только исправление отображения двух пользовательских изображений:

1. `Fabula_icon.png` — launcher-иконка.
2. `exit_splash.PNG` — экран выхода.

Аудио-цепочка, интерфейс, автообновления и остальные функции не перерабатывались.

## Изменённые файлы

### `app/src/main/AndroidManifest.xml`
- Добавлен `android:roundIcon="@mipmap/ic_launcher_round"`.
- Существующие `android:icon="@mipmap/ic_launcher"`, `FileProvider`, разрешения и Activity сохранены.

### `app/src/main/java/com/fabula/audio/MainActivity.kt`
Изменена только `exitApplication()`:
- `exit_splash` теперь показывается через `CENTER_CROP`.
- ImageView и контейнер имеют чёрный фон `#000000`.
- Добавлен лёгкий zoom `1.02x`, чтобы исключить тонкие края/поля.
- Добавлен `isClickable = false` / `isFocusable = false`.
- Сохранён показ ровно `3000 ms`.
- Сохранены `finishAffinity()` и `Process.killProcess(...)`.
- Системные панели скрываются через legacy immersive-флаги и дополнительно через `WindowInsetsController` на API 30+.
- Для выхода включён edge-to-edge fullscreen через `setDecorFitsSystemWindows(false)` на API 30+.

### `app/src/main/res/drawable/exit_splash.png`
- Добавлено реальное изображение пользователя `exit_splash.PNG`.
- Старый placeholder `exit_splash.xml` удалён, чтобы ресурс `R.drawable.exit_splash` ссылался непосредственно на PNG.

### `app/src/main/res/mipmap-*`
Добавлены реальные launcher-ресурсы из `Fabula_icon.png`:

- `mipmap-mdpi/ic_launcher.png`
- `mipmap-hdpi/ic_launcher.png`
- `mipmap-xhdpi/ic_launcher.png`
- `mipmap-xxhdpi/ic_launcher.png`
- `mipmap-xxxhdpi/ic_launcher.png`
- соответствующие `ic_launcher_round.png`
- соответствующие `ic_launcher_foreground.png`

Foreground размещён на прозрачном квадратном полотне с содержимым примерно в центральных 66%, чтобы важные детали оставались внутри безопасной зоны адаптивной иконки.

### `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Adaptive icon.
- Background: `#000000`.
- Foreground: `@mipmap/ic_launcher_foreground`.

### `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Аналогичный adaptive icon для `roundIcon`.

### `design_assets/fabula_icon.png`
- Исходное пользовательское изображение иконки сохранено без изменений.

### `design_assets/exit_splash.png`
- Исходное пользовательское изображение выхода сохранено без изменений.

## Не изменялось

- FFmpeg-цепочка подготовки → ×6 → ×144 → ×432.
- `atempo` и `concat` 6/24/3.
- `FABULA_DUR`.
- `afftdn=nr=6:nf=-30:tn=0:tr=0`.
- Тумблеры и ползунки.
- Раскладка UI.
- `UpdateManager.kt` и проверка обновлений.
- `showInstructions()` и `this.text = text`.
- Точный текст `showPreparationChangedWarning()`.
- Меню, сохранение, суффиксы `_x6/_x144/_x432`.
- Громкость через FFmpeg `volume`.
- `colors.xml`.
- `com.arthenica:smart-exception-java`.
- Gradle 8.7 / JDK 21 / targetSdk 35.
- `setSingleLine(true)`.

## Проверка

После распаковки проверьте:

1. Иконка Fabula Audio отображается на рабочем столе.
2. В круглой и квадратной маске лаунчера логотип не обрезается.
3. Нажатие «Выход» показывает пользовательскую картинку на весь экран.
4. Нет видимых полос по краям.
5. Системные панели скрыты.
6. Экран выхода нельзя закрыть нажатием/свайпом как отдельный экран приложения.
7. Через 3 секунды приложение полностью завершается.
