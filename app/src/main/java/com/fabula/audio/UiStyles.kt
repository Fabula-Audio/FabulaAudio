package com.fabula.audio

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.Button

/**
 * Единый визуальный стиль основных кнопок Fabula Audio.
 * Основной цвет действия: #5B7553, текст: #C3E8BD.
 */
object UiStyles {
    fun styleActionButton(button: Button) {
        val context = button.context
        button.setBackgroundTintList(
            ColorStateList.valueOf(context.getColor(R.color.fabula_button))
        )
        button.setTextColor(context.getColor(R.color.fabula_button_text))
    }

    /**
     * Кнопки AlertDialog делаем такими же зелёными и скруглёнными,
     * как основные кнопки приложения.
     */
    fun styleDialogButtons(dialog: AlertDialog) {
        val context = dialog.context
        val density = context.resources.displayMetrics.density
        listOf(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        ).forEach { button ->
            if (button != null) {
                button.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(context.getColor(R.color.fabula_button))
                }
                button.setTextColor(context.getColor(R.color.fabula_button_text))
                button.setPadding((18 * density).toInt(), (8 * density).toInt(), (18 * density).toInt(), (8 * density).toInt())
            }
        }
    }
}
