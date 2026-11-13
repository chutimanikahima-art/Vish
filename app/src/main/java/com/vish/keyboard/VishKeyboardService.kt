package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class VishKeyboardService : InputMethodService() {
    private var sinhala = false
    private var shifted = false
    private val panel = Color.rgb(12, 18, 31)
    private val key = Color.rgb(30, 40, 59)
    private val accent = Color.rgb(45, 105, 220)

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
            setBackgroundColor(panel)
        }
        val rows = if (sinhala) listOf(
            listOf("්", "ා", "ැ", "ෑ", "ි", "ී", "ු", "ූ", "ෙ", "ේ"),
            listOf("අ", "ආ", "ඇ", "ඉ", "ඊ", "උ", "එ", "ඔ", "ක", "ග"),
            listOf("ච", "ජ", "ට", "ඩ", "ත", "ද", "න", "ප", "බ"),
            listOf("ම", "ය", "ර", "ල", "ව", "ස", "හ", "ළ", "ඟ")
        ) else listOf(
            "1234567890".map { it.toString() },
            "qwertyuiop".map { it.toString() },
            "asdfghjkl".map { it.toString() },
            "zxcvbnm".map { it.toString() }
        )
        rows.forEachIndexed { index, row -> root.addView(makeRow(row.map { if (!sinhala && shifted) it.uppercase() else it }, index)) }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(makeKey("⇧", 1f, accent) { shifted = !shifted; setInputView(buildKeyboard()) })
        controls.addView(makeKey("🌐", 1f, accent) { sinhala = !sinhala; shifted = false; setInputView(buildKeyboard()) })
        controls.addView(makeKey("😊", 1f) { currentInputConnection?.commitText("😊", 1) })
        controls.addView(makeKey("space", 4f) { currentInputConnection?.commitText(" ", 1) })
        controls.addView(makeKey("⌫", 1f, accent) { currentInputConnection?.deleteSurroundingText(1, 0) })
        controls.addView(makeKey("↵", 1f, accent) { currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)); currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)) })
        root.addView(controls)
        return root
    }

    private fun makeRow(chars: List<String>, row: Int): View {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chars.forEach { ch -> line.addView(makeKey(ch, 1f) { currentInputConnection?.commitText(ch, 1); if (shifted && !sinhala) { shifted = false; setInputView(buildKeyboard()) } }) }
        return line
    }

    private fun makeKey(label: String, weight: Float, color: Int = key, action: () -> Unit): View =
        TextView(this).apply {
            text = label
            textSize = if (label == "space") 13f else 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(color)
            val p = LinearLayout.LayoutParams(0, 48, weight)
            p.setMargins(2, 3, 2, 3)
            layoutParams = p
            setOnClickListener { action() }
        }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
    }
}
