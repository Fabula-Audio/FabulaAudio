package com.fabula.audio

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
class SpeedModesLayoutTest {
    @Test fun allThreeModesFitOnNarrowAndWideScreens() {
        for (width in listOf(320, 360, 411, 600)) {
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
            val screen = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
            screen.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            screen.layout(0, 0, width, screen.measuredHeight)
            val buttons = listOf(R.id.speed6Button, R.id.speed144Button, R.id.speed432Button)
                .map { screen.findViewById<Button>(it) }
            val row = buttons.first().parent as LinearLayout
            for (button in buttons) {
                val label = "${button.text} at ${width}dp"
                assertEquals(label, View.VISIBLE, button.visibility)
                assertTrue("$label has a usable touch target", button.width >= 48)
                assertTrue("$label fits inside the row", button.left >= 0 && button.right <= row.width)
                assertTrue("$label has readable text", button.width - button.paddingLeft - button.paddingRight >=
                    button.paint.measureText(button.text.toString()))
                assertTrue("$label has equal width", abs(button.width - buttons.first().width) <= 1)
            }
            assertTrue(buttons[0].right <= buttons[1].left)
            assertTrue(buttons[1].right <= buttons[2].left)
        }
    }
}
