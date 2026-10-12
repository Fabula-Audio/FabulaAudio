# Fabula Audio 4.27 — fixed33

## Изменения

### 1. Белый общий фон приложения

Изменён только общий фон интерфейса:
- `app/src/main/res/values/colors.xml`
  - `fabula_background`: `#D8EDD0` → `#FFFFFF`
  - `fabula_bg`: `#D8EDD0` → `#FFFFFF`
- `app/src/main/res/values/themes.xml`
  - добавлен `android:windowBackground` → `@color/fabula_background`
- `app/src/main/res/layout/activity_main.xml`
  - корневой фон остаётся `@color/fabula_background`, поэтому теперь он белый.

### 2. Сохранён внешний вид специальных кнопок

Чтобы изменение общего фона не изменило шапочные кнопки «Инструкция» и «Меню», добавлен отдельный ресурс:
- `fabula_header_button`: `#D8EDD0`

Он используется только для этих кнопок. Их внешний вид относительно версии 4.26 сохранён.

### 3. Версия приложения

В `app/build.gradle`:
- `versionCode`: `26` → `27`
- `versionName`: `4.25` → `4.27`

## Не изменялось

- аудиологика ×6 / ×144 / ×432;
- `UpdateManager.kt`;
- иконки;
- `exit_splash.png`;
- `file_paths.xml`;
- основные кнопки действий `#5B7553`;
- карточки и панели;
- меню и email разработчика;
- статус записи и логика записи;
- зависимости.

## Публикационная версия

- `versionName`: **4.27**
- `versionCode`: **27**
- GitHub Release tag: **v4.27**
