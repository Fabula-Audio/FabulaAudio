# Fabula Audio

Android-приложение для записи и обработки аудио: импорт WAV/MP3/M4A,
подготовка звука, режимы ×6, ×144 и ×432, прослушивание и экспорт.
Ускоренные фрагменты повторяются, поэтому режим ×432 не означает,
что весь итоговый файл становится в 432 раза короче.

## Требования

- Android Studio с поддержкой Android Gradle Plugin 8.6.1.
- JDK 17 или совместимая JDK; в прежней документации указана JDK 21.
- Android SDK Platform 35.
- Android NDK 26.3.11579264 и CMake 3.22.1.
- Интернет для загрузки зависимостей.
- Для установки: Android 8.0/API 26 или новее, ARM64.

## Сборка через Android Studio, без терминала

1. На GitHub нажмите Code → Download ZIP и распакуйте архив.
2. В Android Studio нажмите Open и выберите папку с settings.gradle.
3. В Settings → Languages & Frameworks → Android SDK установите SDK Platform 35.
   Во вкладке SDK Tools включите Show Package Details и установите
   NDK 26.3.11579264 и CMake 3.22.1.
4. Дождитесь Gradle Sync. Если Studio предложит установить недостающие SDK,
   согласитесь с установкой официальных компонентов.
5. Для APK выберите Build → Build Bundle(s) / APK(s) → Build APK(s).
   В новых версиях Studio название может быть Generate App Bundles or APKs → Generate APKs.
6. Результат: app/build/outputs/apk/debug/app-debug.apk.

## Состояние этой копии

Версия 4.34 / versionCode 35. В окне «О приложении и лицензиях»
есть кнопки «Исходный код» и «Лицензии». Исходники:
https://github.com/Fabula-Audio/FabulaAudio
Включён стандартный Gradle Wrapper 8.7 с SHA-256 проверкой дистрибутива.
Комплект дополнен исходными архивами native-зависимостей и полными найденными
лицензиями. Проверки и ограничения описаны в SOURCE_STATUS.md.

## Лицензия

Оригинальный код приложения распространяется под GNU GPL version 3 only
(SPDX: GPL-3.0-only). Полный текст — LICENSE.
Чужие компоненты сохраняют собственные лицензии и copyright-уведомления.
См. NOTICES и оригинальные LICENSE/COPYING/AUTHORS в third_party.

Программа предоставляется без гарантий, в пределах, допускаемых законом.
Для каждого распространяемого APK нужно предоставить соответствующие ему
исходники приложения и необходимые исходники библиотек, изменения и инструкции сборки.
Комплект исходников и область проверки описаны в SOURCE_STATUS.md.

## Основные компоненты

- SBSMS: app/src/main/cpp/third_party/libsbsms (копия из Audacity 3.4.2).
- FFmpegKit Audio: dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7.
- smart-exception-java: com.arthenica:smart-exception-java:0.2.1.
- Kotlin 2.0.21; AndroidX; Material Components.

Не загружайте ключи подписи, пароли, local.properties или личные аудиозаписи.

## Подпись APK

Для публикации используйте release APK, подписанный вашим постоянным ключом.
Ключи и пароли не входят в этот архив. Подпись прежним ключом нужна для обновления
уже установленных копий. См. BUILD_RU.md.
