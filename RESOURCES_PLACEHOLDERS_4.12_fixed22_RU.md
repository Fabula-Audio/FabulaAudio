# Ресурсы-заглушки 4.12 fixed22

## Заставка выхода
- `app/src/main/res/drawable/exit_splash.xml`
- Код обращается к `R.drawable.exit_splash`.
- Позже файл-заглушку можно удалить и положить пользовательскую картинку `exit_splash.png` с тем же базовым именем.
- Картинка не генерировалась как пользовательский дизайн; XML — только техническая заглушка.

## Иконка
- Manifest: `android:icon="@mipmap/ic_launcher"`.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive-icon каркас.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — foreground-заглушка.
- Позже пользователь может заменить foreground/ресурсы иконки, не меняя `AndroidManifest.xml`.

## Важно
Пользовательские изображения в эту версию намеренно не встраивались.
