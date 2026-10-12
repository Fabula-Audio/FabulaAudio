# Fabula Audio — автоматические обновления через GitHub Releases

## Что уже добавлено в приложение

Приложение при запуске тихо проверяет файл `version.json` на GitHub Raw.
Если `latest_version` отличается от текущей `BuildConfig.VERSION_NAME`, появляется окно:

- **Скачать** — APK скачивается через Android DownloadManager;
- **Позже** — окно закрывается.

После скачивания Android открывает стандартное окно установки APK.

Если GitHub недоступен, нет интернета или `version.json` повреждён, приложение продолжает работать как обычно.

> В коде пока стоят `USERNAME` и `REPO`. Их нужно заменить на свои данные.

---

# ЧАСТЬ 1. Создать репозиторий на GitHub

Ниже инструкция буквально по шагам.

### Шаг 1. Открыть GitHub

1. Открой браузер.
2. Перейди на https://github.com/.
3. Войди в свой аккаунт.

### Шаг 2. Создать новый репозиторий

1. В правом верхнем углу нажми **+**.
2. Выбери **New repository**.
3. В поле **Repository name** придумай имя, например:

   `FabulaAudio`

4. Выбери **Public**.
5. Поставь галочку **Add a README file**, если GitHub предлагает её.
6. Нажми **Create repository**.

Запомни две вещи:

- `USERNAME` — имя твоего аккаунта GitHub;
- `REPO` — имя созданного репозитория.

Например, если адрес репозитория:

`https://github.com/fedor123/FabulaAudio`

то:

- USERNAME = `fedor123`
- REPO = `FabulaAudio`

---

# ЧАСТЬ 2. Заменить USERNAME и REPO в приложении

Это нужно сделать один раз.

1. Открой проект **FabulaAudio** в Android Studio.
2. Слева открой:

   `app`
   → `src`
   → `main`
   → `java`
   → `com.fabula.audio`

3. Открой файл:

   `UpdateManager.kt`

4. Найди строку:

```kotlin
private const val UPDATE_URL =
    "https://raw.githubusercontent.com/USERNAME/REPO/main/version.json"
```

5. Замени только `USERNAME` и `REPO`.

Например:

```kotlin
private const val UPDATE_URL =
    "https://raw.githubusercontent.com/fedor123/FabulaAudio/main/version.json"
```

6. Нажми **Ctrl+S**.

Больше эту строку менять при каждом выпуске новой версии не нужно.

---

# ЧАСТЬ 3. Создать version.json на GitHub

Файл должен лежать прямо в корне репозитория.

То есть после открытия репозитория ты должен видеть примерно:

`README.md`

`version.json`

### Шаг 1

1. Открой свой репозиторий на GitHub.
2. Нажми **Add file**.
3. Выбери **Create new file**.

### Шаг 2

В поле имени файла напиши:

`version.json`

### Шаг 3

В большое поле вставь:

```json
{
  "latest_version": "4.14",
  "download_url": "https://github.com/USERNAME/REPO/releases/download/v4.14/app-release.apk",
  "changelog": "Описание изменений"
}
```

Теперь замени:

- `USERNAME` на имя своего GitHub;
- `REPO` на имя своего репозитория.

Например:

```json
{
  "latest_version": "4.14",
  "download_url": "https://github.com/fedor123/FabulaAudio/releases/download/v4.14/app-release.apk",
  "changelog": "Добавлена новая система обновлений. Исправлены ошибки."
}
```

### Шаг 4

Прокрути страницу вниз.

Нажми **Commit changes**.

После этого файл должен открываться примерно по адресу:

`https://raw.githubusercontent.com/USERNAME/REPO/main/version.json`

Открой этот адрес в браузере.

Ты должен увидеть JSON-текст.

Если вместо него ошибка 404 — сначала исправь это. Приложение не сможет проверить обновление, пока `version.json` недоступен.

---

# ЧАСТЬ 4. Создать APK новой версии

Например, сейчас приложение имеет версию **4.13**, а следующую ты выпускаешь как **4.14**.

### Шаг 1. Открыть build.gradle

В Android Studio слева открой:

`app`
→ `build.gradle`

Найди:

```groovy
versionCode 14
versionName '4.13'
```

Для следующей версии поставь:

```groovy
versionCode 15
versionName '4.14'
```

### Очень важно

При каждом новом выпуске увеличивай **оба** значения:

- versionCode: 14 → 15 → 16 → ...
- versionName: 4.13 → 4.14 → 4.15 → ...

`versionCode` должен увеличиваться, иначе Android может отказаться устанавливать новую версию поверх старой.

---

# ЧАСТЬ 5. Собрать APK

1. В Android Studio сверху выбери:

   **Build**

2. Выбери:

   **Generate App Bundles or APKs** → **Generate APKs**

   Если в твоей версии Android Studio название немного другое, ищи пункт со словом **APK**.

3. Дождись окончания сборки.
4. Android Studio покажет сообщение о созданном APK.
5. Нажми **locate**, если такая кнопка появилась.

Обычно APK находится примерно здесь:

`app/build/outputs/apk/release/app-release.apk`

Для обновлений используй именно APK, а не AAB.

---

# ЧАСТЬ 6. Создать GitHub Release

Теперь нужно положить APK в GitHub.

1. Открой свой репозиторий.
2. Справа найди раздел **Releases**.
3. Нажми **Create a new release**.

### Tag

В поле **Choose a tag** создай новый tag:

`v4.14`

Он должен совпадать с частью URL из `version.json`.

### Название

Например:

`Fabula Audio 4.14`

### APK

Найди область **Attach binaries by dropping them here**.

Перетащи туда файл:

`app-release.apk`

### Завершение

Нажми кнопку создания публикации Release.

После публикации APK должен быть доступен по адресу:

`https://github.com/USERNAME/REPO/releases/download/v4.14/app-release.apk`

---

# ЧАСТЬ 7. Проверить version.json ещё раз

Открой в браузере:

`https://raw.githubusercontent.com/USERNAME/REPO/main/version.json`

Проверь три вещи:

1. `latest_version` равен `4.14`.
2. `download_url` ведёт на Release `v4.14`.
3. `changelog` содержит нормальное описание изменений.

---

# ЧАСТЬ 8. Проверить обновление на телефоне

Самый простой тест:

1. На телефоне должна быть установлена старая версия **4.13**.
2. На GitHub должна существовать Release **v4.14**.
3. В `version.json` должно быть:

```json
"latest_version": "4.14"
```

4. Запусти Fabula Audio.
5. Подожди несколько секунд.
6. Если всё настроено правильно, появится окно:

**Доступно обновление 4.14**

с текстом из `changelog`.

7. Нажми **Скачать**.
8. Дождись окончания загрузки.
9. Android может попросить разрешить установку неизвестных приложений для Fabula Audio.
10. Разреши установку.
11. Вернись к установке APK.
12. Подтверди установку.

После установки запусти приложение и проверь версию.

---

# ЧАСТЬ 9. Как выпустить следующую версию

Допустим, сегодня у тебя 4.14, а завтра готова 4.15.

Делай всегда одну и ту же последовательность:

**1. Android Studio**

`versionCode 16`

`versionName '4.15'`

**2. Собрать**

`app-release.apk`

**3. GitHub Release**

Tag:

`v4.15`

Загрузить:

`app-release.apk`

**4. version.json**

```json
{
  "latest_version": "4.15",
  "download_url": "https://github.com/USERNAME/REPO/releases/download/v4.15/app-release.apk",
  "changelog": "Описание изменений версии 4.15"
}
```

**5. Запустить старую версию приложения.**

Она увидит, что `4.15` отличается от её версии, и предложит обновление.

---

# ВАЖНО ПРО ПЕРВЫЙ РЕЛИЗ

Для самого первого теста не нужно ждать большого обновления.

Текущая версия проекта — **4.13**.

Поэтому сделай тестовую Release:

`v4.14`

и положи в `version.json`:

```json
"latest_version": "4.14"
```

Это позволит проверить всю цепочку:

**4.13 → обнаружение 4.14 → диалог → DownloadManager → APK → установка.**

---

# ЕСЛИ ОБНОВЛЕНИЕ НЕ ПОЯВИЛОСЬ

Проверяй строго сверху вниз:

### 1. version.json открывается в браузере?

Если нет — исправь GitHub-файл.

### 2. В нём действительно 4.14?

Должно быть:

```json
"latest_version": "4.14"
```

### 3. В приложении действительно 4.13?

В `app/build.gradle`:

```groovy
versionName '4.13'
```

### 4. Ссылка download_url открывается?

Она должна вести именно на существующий APK Release.

### 5. Tag совпадает?

В JSON:

`v4.14`

и GitHub Release:

`v4.14`

### 6. APK называется правильно?

В `download_url`:

`app-release.apk`

и загруженный файл должен иметь это имя.

---

# ВАЖНО ПРО USERNAME/REPO

`USERNAME` и `REPO` нужно заменить только в коде `UpdateManager.kt` один раз.

После этого при выпуске 4.14, 4.15, 4.16 и т.д. менять `UPDATE_URL` не нужно.

Меняется только `version.json` и номер Release.
