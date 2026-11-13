package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class VishKeyboardService : InputMethodService() {
    private var sinhala = false
    private var shifted = false
    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }
    private val keyColor = Color.rgb(30, 42, 57)
    private val accent = Color.rgb(49, 125, 115)

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val totalHeight = prefs.getInt("height", 370).coerceIn(300, 430)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(-1, dp(totalHeight))
            setBackgroundColor(prefs.getInt("color", Color.rgb(12, 20, 27)))
        }

        // Compact brand bar, like the supplied reference
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(makeKey("▦", .8f, Color.rgb(26,37,48), 28, 14f) {})
        toolbar.addView(TextView(this).apply {
            text = "VISH"; textSize = 12f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        toolbar.addView(makeKey("⋯", .8f, Color.rgb(26,37,48), 28, 17f) {})
        toolbar.addView(makeKey("🎙", .8f, Color.rgb(26,37,48), 28, 15f) {})
        root.addView(toolbar)

        val emojis = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("❤️","🙂","💔","❤️","😞","🥺","😆","😘","💛","•••").forEach { e ->
            emojis.addView(makeKey(e, 1f, Color.TRANSPARENT, 34, 17f) {
                if (e != "•••") currentInputConnection?.commitText(e, 1)
            })
        }
        root.addView(emojis)

        if (!sinhala) {
            val nums = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            "1234567890".forEach { n ->
                nums.addView(makeKey(n.toString(), 1f, keyColor, 34, 12f) {
                    currentInputConnection?.commitText(n.toString(), 1)
                })
            }
            root.addView(nums)
        }

        val rows = if (sinhala) listOf(
            listOf("්","ා","ැ","ෑ","ි","ී","ු","ූ","ෙ","ේ"),
            listOf("අ","ආ","ඇ","ඉ","ඊ","උ","එ","ඔ","ක","ග"),
            listOf("ච","ජ","ට","ඩ","ත","ද","න","ප","බ"),
            listOf("ම","ය","ර","ල","ව","ස","හ","ළ","ඟ")
        ) else listOf(
            "qwertyuiop".map { it.toString() },
            "asdfghjkl".map { it.toString() },
            "zxcvbnm".map { it.toString() }
        )

        rows.forEachIndexed { index, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (!sinhala && index == 1) setPadding(dp(10), 0, dp(10), 0)
            if (!sinhala && index == 2) setPadding(dp(24), 0, dp(4), 0)
            row.forEach { ch ->
                val shown = if (!sinhala && shifted) ch.uppercase() else ch
                line.addView(makeKey(shown, 1f, keyColor, 49, 19f) {
                    currentInputConnection?.commitText(if (!sinhala && shifted) ch.uppercase() else ch, 1)
                    if (shifted && !sinhala) { shifted = false; setInputView(buildKeyboard()) }
                })
            }
            root.addView(line)
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(makeKey("?123", 1.15f, Color.rgb(25,39,48), 48, 13f) {})
        bottom.addView(makeKey("⇧", .9f, Color.rgb(25,39,48), 48, 19f) {
            shifted = !shifted; setInputView(buildKeyboard())
        })
        bottom.addView(makeKey("🌐", .9f, Color.rgb(25,39,48), 48, 18f) {
            sinhala = !sinhala; shifted = false; setInputView(buildKeyboard())
        })
        bottom.addView(makeKey("English", 3.0f, Color.rgb(36,55,61), 48, 14f) {
            currentInputConnection?.commitText(" ", 1)
        })
        bottom.addView(makeKey(".", .7f, Color.rgb(25,39,48), 48, 18f) {
            currentInputConnection?.commitText(".", 1)
        })
        bottom.addView(makeKey("⌫", 1.0f, Color.rgb(25,39,48), 48, 18f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        bottom.addView(makeKey("↵", 1.0f, accent, 48, 20f) {
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
        })
        root.addView(bottom)
        return root
    }

    private fun makeKey(label: String, weight: Float, color: Int, height: Int, size: Float, action: () -> Unit): View =
        TextView(this).apply {
            text = label; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT
            background = GradientDrawable().apply {
                setColor(color); cornerRadius = dp(7).toFloat()
                setStroke(dp(1), Color.argb(28,255,255,255))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(height), weight).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setOnClickListener { action() }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
    }
}
