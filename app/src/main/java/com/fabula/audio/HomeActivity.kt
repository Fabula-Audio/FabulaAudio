package com.fabula.audio

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.Process
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        UpdateManager(this).checkForUpdate()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.fabula_background))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 4.dp(), 8.dp(), 4.dp())
        }
        val title = TextView(this).apply {
            text = "Fabula Audio"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setTextColor(getColor(R.color.fabula_text))
        }
        header.addView(title, LinearLayout.LayoutParams(0, 56.dp(), 1f))
        val menuButton = Button(this).apply {
            text = "⋮"
            textSize = 24f
            gravity = Gravity.CENTER
            contentDescription = "Меню"
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.fabula_header_button)))
            setTextColor(getColor(R.color.fabula_text))
            minWidth = 48.dp()
        }
        menuButton.setOnClickListener { showMenu(it) }
        header.addView(menuButton, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(20.dp(), 24.dp(), 20.dp(), 24.dp())
        }
        val subtitle = TextView(this).apply {
            text = "Выберите режим работы"
            textSize = 18f
            setTextColor(getColor(R.color.fabula_section_heading))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20.dp())
        }
        body.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        val recordButton = makeLargeButton("Записать аудио-заготовку фабулы по тексту")
        recordButton.setOnClickListener { startActivity(Intent(this, RecordTextActivity::class.java)) }
        body.addView(recordButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, 12.dp())
        })

        val quantizeButton = makeLargeButton("Ускорить вашу фабулу\n×6 · ×144 · ×432")
        quantizeButton.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        body.addView(quantizeButton, LinearLayout.LayoutParams(-1, -2))

        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val exitButton = Button(this).apply {
            text = "Выход из приложения"
            isAllCaps = false
            UiStyles.styleActionButton(this)
        }
        exitButton.setOnClickListener { exitApplication() }
        root.addView(exitButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(16.dp(), 4.dp(), 16.dp(), 12.dp())
        })
        return root
    }

    private fun makeLargeButton(textValue: String): Button = Button(this).apply {
        text = textValue
        textSize = 18f
        gravity = Gravity.CENTER
        UiStyles.styleActionButton(this)
        isAllCaps = false
        setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())
        minHeight = 0
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Инструкция")
            menu.add("О приложении")
            menu.add("Связаться с разработчиком")
            setOnMenuItemClickListener {
                when (it.title) {
                    "Инструкция" -> {
                        showInstructions()
                        true
                    }
                    "О приложении" -> {
                        AboutDialog.show(this@HomeActivity)
                        true
                    }
                    "Связаться с разработчиком" -> {
                        contactDeveloper()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun contactDeveloper() {
        val uri = Uri.Builder()
            .scheme("mailto")
            .path("fabula.audio@yandex.ru")
            .appendQueryParameter("subject", "Fabula Audio — вопрос/предложение")
            .build()
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "Почтовое приложение не найдено.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun showInstructions() {
        val text = """Fabula Audio — инструкция пользователя

РЕЖИМ 1. Запись аудиозаготовки по тексту

1. На главном экране выберите «Записать аудио-заготовку фабулы по тексту».
2. Загрузите текст из файла .txt или .docx либо вставьте текст из буфера обмена.
3. Приложение разделит текст на фрагменты. Для каждого фрагмента отображается его состояние.

Запись фрагмента:
• «Записать» — начать запись.
• Во время записи кнопка меняется на «Стоп».
• «Стоп» завершает запись. После этого фрагмент сразу считается записанным.
• «Прослушать» — прослушать текущую запись фрагмента.
• «Удалить» — удалить аудиозапись фрагмента. Текст фрагмента при этом сохраняется.
• Если фрагмент уже записан, «Записать» можно нажать снова — новая запись заменит предыдущую.

4. Запишите все нужные фрагменты.
5. Нажмите «Склеить и сохранить».
6. Введите название и при необходимости комментарий к записи.
7. Нажмите «Далее» и выберите папку для сохранения.
8. Приложение сохранит итоговую аудиозапись и текстовый файл с информацией о ней.

Важно: «Склеить и сохранить» становится доступной, когда записан хотя бы один фрагмент.
Фрагменты и склеенная заготовка записываются в WAV: 44,1 кГц, 16 бит, стерео.
Галочка «Выбрать всё» отмечает все строки загруженного TXT или DOCX.

РЕЖИМ 2. Ускорение фабулы

1. На главном экране выберите «Ускорить вашу фабулу».
2. Выберите исходный аудиофайл и необходимые параметры подготовки.
3. Выберите ×6, ×144 или ×432. Предыдущие этапы выполняются автоматически.
   В ×144 и ×432 сохраняется основной голос ×6, ускоренные дорожки добавляются тихим фоном.
4. Дождитесь окончания обработки.
5. Нажмите «Прослушать результат», чтобы проверить запись.
6. Ползунок громкости регулирует громкость прослушивания.
7. Сохраните готовый результат в выбранную папку.

МЕНЮ

• «Инструкция» — эта справка.
• «О приложении» — сведения о Fabula Audio и используемых библиотеках.
• «Связаться с разработчиком» — отправка вопроса, сообщения об ошибке или предложения по электронной почте.

При возникновении вопросов или ошибок можно написать разработчику: fabula.audio@yandex.ru"""
    val textView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.fabula_text))
            setPadding(24.dp(), 8.dp(), 24.dp(), 8.dp())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(textView) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Инструкция")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.show()
        UiStyles.styleDialogButtons(dialog)
    }

    private fun exitApplication() {
        val splash = ImageView(this).apply {
            setImageResource(R.drawable.exit_splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            scaleX = 1.02f
            scaleY = 1.02f
            isClickable = false
            isFocusable = false
        }
        val splashContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(splash, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }
        setContentView(splashContainer)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            finishAffinity()
            Process.killProcess(Process.myPid())
        }, 3000L)
    }
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
