package com.fabula.audio

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import android.graphics.Color
import android.text.method.ScrollingMovementMethod
import android.widget.ScrollView
import android.widget.TextView

object AboutDialog {
    private const val SOURCE_URL = "https://github.com/Fabula-Audio/FabulaAudio"

    private const val ABOUT_TEXT = """Fabula Audio — приложение для обработки аудиозаписей по методике проекта "Ключ к Единству.".

Авторские права:
© 2026 Fabula Audio.
Оригинальный код приложения: GNU GPL version 3 only (GPL-3.0-only).
Программа предоставляется без гарантий. Вы можете изменять и
распространять её в соответствии с GPLv3.

Версия: 4.34
Исходный код: https://github.com/Fabula-Audio/FabulaAudio

Используемые библиотеки:

1. FFmpeg и FFmpegKit Audio 8.1.7
   Taner Sener / Arthenica, FFmpeg contributors,
   ffmpegkit-maintained / Jokobee
   Лицензия: GNU Lesser General Public License v3.0 (LGPL 3.0)
   https://github.com/ffmpegkit-maintained/ffmpeg

2. smart-exception-java и smart-exception-common 0.2.1
   © Taner Sener; издатель Arthenica
   Лицензия: BSD 3-Clause

3. AndroidX DocumentFile
   © The Android Open Source Project
   Лицензия: Apache License 2.0
   https://developer.android.com/jetpack/androidx/releases/documentfile

4. Kotlin Standard Library
   © JetBrains s.r.o.
   Лицензия: Apache License 2.0
   https://kotlinlang.org/

5. SBSMS (исходники из Audacity 3.4.2)
   © Clayton Otey и авторы SBSMS
   Лицензия: GNU General Public License v3.0 (GPL 3.0)
   https://github.com/audacity/audacity/tree/Audacity-3.4.2/lib-src/libsbsms

Библиотеки сохраняют собственные лицензии и авторские права.
Полные тексты и уведомления доступны по кнопке «Лицензии».

По вопросам, сообщениям об ошибках и предложениям новых функций пишите: fabula.audio@yandex.ru"""

    fun show(context: Context) {
        val textView = TextView(context).apply {
            text = ABOUT_TEXT
            textSize = 15f
            setTextColor(context.getColor(R.color.fabula_text))
            setPadding(24, 8, 24, 8)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        val scroll = ScrollView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(textView)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("О приложении и лицензиях")
            .setView(scroll)
            .setNegativeButton("Исходный код") { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Откройте $SOURCE_URL в браузере", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Лицензии") { _, _ -> showLicenseList(context) }
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun showLicenseList(context: Context) {
        fun files(path: String): List<String> =
            context.assets.list(path).orEmpty().flatMap { name ->
                val child = "$path/$name"
                if (context.assets.list(child).orEmpty().isEmpty()) listOf(child)
                else files(child)
            }
        val paths = files("licenses").sorted()
        val labels = paths.map { it.removePrefix("licenses/") }.toTypedArray()
        val dialog = AlertDialog.Builder(context)
            .setTitle("Лицензии и уведомления")
            .setItems(labels) { _, index ->
                val text = context.assets.open(paths[index]).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val view = TextView(context).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(context.getColor(R.color.fabula_text))
                    setPadding(24, 16, 24, 16)
                    setTextIsSelectable(true)
                }
                val detail = AlertDialog.Builder(context)
                    .setTitle(labels[index])
                    .setView(ScrollView(context).apply { addView(view) })
                    .setPositiveButton("Закрыть", null)
                    .create()
                detail.show()
                UiStyles.styleDialogButtons(detail)
            }
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }
}
