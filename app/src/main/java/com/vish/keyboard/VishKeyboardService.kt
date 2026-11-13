package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class VishKeyboardService : InputMethodService() {
    private var sinhala = false
    private var shifted = false
    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }
    private val key = Color.rgb(30, 40, 59)
    private val accent = Color.rgb(40, 105, 220)

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val height = prefs.getInt("height", 300).coerceIn(220, 360)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(-1, dp(height))
            setBackgroundColor(prefs.getInt("color", Color.rgb(12, 18, 31)))
        }
        prefs.getString("image", null)?.let { raw ->
            try {
                contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) root.background = BitmapDrawable(resources, bitmap)
                }
            } catch (_: Exception) {}
        }

        val brand = TextView(this).apply {
            text = "VISH Keyboard"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 1, 0, 3)
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(19)))

        val emojiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("😀", "😂", "🥰", "😍", "🔥", "❤️", "👍", "🙏").forEach { emoji ->
            emojiRow.addView(makeKey(emoji, 1f, Color.rgb(35, 48, 70), 34) {
                currentInputConnection?.commitText(emoji, 1)
            })
        }
        root.addView(emojiRow)

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
        rows.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { ch ->
                line.addView(makeKey(if (!sinhala && shifted) ch.uppercase() else ch, 1f, key, 39) {
                    currentInputConnection?.commitText(ch.let { if (!sinhala && shifted) it.uppercase() else it }, 1)
                    if (shifted && !sinhala) { shifted = false; setInputView(buildKeyboard()) }
                })
            }
            root.addView(line)
        }

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(makeKey("⇧", 1f, accent, 42) { shifted = !shifted; setInputView(buildKeyboard()) })
        controls.addView(makeKey("🌐", 1f, accent, 42) { sinhala = !sinhala; shifted = false; setInputView(buildKeyboard()) })
        controls.addView(makeKey("space", 3.5f, key, 42) { currentInputConnection?.commitText(" ", 1) })
        controls.addView(makeKey("⌫", 1f, accent, 42) { currentInputConnection?.deleteSurroundingText(1, 0) })
        controls.addView(makeKey("↵", 1f, accent, 42) {
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
        })
        root.addView(controls)
        return root
    }

    private fun makeKey(label: String, weight: Float, color: Int, height: Int, action: () -> Unit): View =
        TextView(this).apply {
            text = label
            textSize = if (label == "space") 12f else 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.argb(35, 255, 255, 255))
            }
            val p = LinearLayout.LayoutParams(0, dp(height), weight)
            p.setMargins(2, 2, 2, 2)
            layoutParams = p
            setOnClickListener { action() }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
    }
}
