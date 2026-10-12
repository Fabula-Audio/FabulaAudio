# Fabula Audio 4.16 — changelog

## Изменённые файлы

### `app/build.gradle`
- `versionCode`: `16` → `17`.
- `versionName`: `4.15` → `4.16`.
- Остальные настройки Gradle, SDK, JDK и зависимости не изменены.

### `app/src/main/AndroidManifest.xml`
- Launcher перенесён с `MainActivity` на новую `HomeActivity`.
- `MainActivity` оставлена зарегистрированной, но без `MAIN/LAUNCHER` intent-filter.
- Зарегистрированы новые активности: `RecordTextActivity`, `FragmentListActivity`, `RecordFragmentActivity`.
- Добавлено только необходимое runtime-разрешение `RECORD_AUDIO`.
- Существующие `INTERNET`, `REQUEST_INSTALL_PACKAGES` и `FileProvider` сохранены.

### `app/src/main/java/com/fabula/audio/MainActivity.kt`
- Существующий пункт меню «О приложении» сохранён.
- Его диалог вынесен в общий `AboutDialog.show(this)`; аудио-логика и остальное поведение MainActivity не изменены.

## Новые файлы

### `app/src/main/java/com/fabula/audio/AboutDialog.kt`
- Общий диалог «О приложении и лицензиях».
- `ScrollView` + `TextView`.
- Текст выделяемый.
- Текст лицензий сохранён дословно по заданию.

### `app/src/main/java/com/fabula/audio/HomeActivity.kt`
- Новый экран-развилка.
- Кнопка «Записать аудио-заготовку фабулы по тексту».
- Кнопка «Квантовать фабулу» с подписью `×6, ×144, ×432`.
- В `onCreate()` вызывается `UpdateManager(this).checkForUpdate()`.
- Меню содержит «О приложении».

### `app/src/main/java/com/fabula/audio/RecordTextActivity.kt`
- Загрузка `.txt` через `ACTION_OPEN_DOCUMENT` с `text/plain`.
- Вставка из буфера обмена.
- Отображение текста списком строк.
- Выбор строк касанием; выбранные строки подсвечиваются.
- Уже существующие `*...*` и строки, начинающиеся с `*`, автоматически считаются выбранными.
- Соседние выбранные строки объединяются в фрагменты.

### `app/src/main/java/com/fabula/audio/FragmentListActivity.kt`
- Нумерованный список фрагментов.
- Отображение статусов «не записан» / «записан».
- Открытие записи касанием.
- Удаление долгим нажатием с подтверждением.
- Кнопка «Склеить и сохранить».
- Склейка через ffmpeg-kit concat demuxer с `-c copy`.
- Сохранение через существующий SAF URI `fabula_storage/results_tree`.
- Имя результата: `fabula_zagotovka_YYYY-MM-DD_HH-mm-ss.m4a`.

### `app/src/main/java/com/fabula/audio/RecordFragmentActivity.kt`
- Runtime-запрос `RECORD_AUDIO`.
- Запись через `MediaRecorder` в MPEG-4/AAC в `cacheDir`.
- Живая индикация амплитуды через `getMaxAmplitude()`.
- Прослушивание через `MediaPlayer`.
- Перезапись и возврат записанного фрагмента в список.

## Что намеренно не изменялось

- FFmpeg-цепочка MainActivity: подготовка → ×6 → ×144 → ×432.
- `FABULA_DUR`, `FABULA_CMD`, `FABULA_SAVE`, `FABULA_PLAY`.
- `UpdateManager.kt` и URL автообновлений.
- Существующая папка сохранения и SAF-механизм MainActivity.
- Инструкция и `this.text = text`.
- `showPreparationChangedWarning()` и его точный текст.
- Иконка, `exit_splash.png`, `exitApplication()` и палитра.
- Существующие зависимости; новые библиотеки не добавлялись.
- Gradle 8.7 / JDK 21 / targetSdk 35 / minSdk 26.
