package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.View
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class VishKeyboardService : InputMethodService() {

    private var sinhala = false
    private var shifted = false
    private var keyboardRoot: View? = null

    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("theme", "height", "keySize", "color", "image", "sound", "vibration")) {
                if (keyboardRoot != null && currentInputConnection != null) {
                    setInputView(buildKeyboard())
                }
            }
        }

    private data class KeyboardPalette(
        val background: Int,
        val key: Int,
        val specialKey: Int,
        val accent: Int,
        val text: Int
    )

    private fun keyboardPalette(): KeyboardPalette {
        return if (prefs.getString("theme", "dark") == "light") {
            KeyboardPalette(
                Color.rgb(231, 237, 246),
                Color.WHITE,
                Color.rgb(218, 228, 241),
                Color.rgb(55, 132, 122),
                Color.rgb(28, 40, 58)
            )
        } else {
            KeyboardPalette(
                Color.rgb(12, 20, 27),
                Color.rgb(30, 42, 57),
                Color.rgb(25, 39, 48),
                Color.rgb(49, 125, 115),
                Color.WHITE
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val colors = keyboardPalette()
        val totalHeight = prefs.getInt("height", 370).coerceIn(300, 430)
        val letterHeight = prefs.getInt("keySize", 49).coerceIn(42, 58)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(-1, dp(totalHeight))
            setBackgroundColor(prefs.getInt("color", colors.background))
        }
        keyboardRoot = root

        prefs.getString("image", null)?.let { raw ->
            try {
                contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let {
                        root.background = BitmapDrawable(resources, it)
                    }
                }
            } catch (_: Exception) { }
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(makeKey("▦", .8f, colors.specialKey, 28, 14f, colors) {})
        toolbar.addView(TextView(this).apply {
            text = "V I S H"
            textSize = 13f
            setTextColor(colors.text)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        toolbar.addView(makeKey("⋯", .8f, colors.specialKey, 28, 17f, colors) {})
        toolbar.addView(makeKey("🎙", .8f, colors.specialKey, 28, 15f, colors) {})
        root.addView(toolbar)

        val emojis = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("❤️", "🙂", "💔", "❤️", "😞", "🥺", "😆", "😘", "💛", "•••").forEach { emoji ->
            emojis.addView(makeKey(emoji, 1f, Color.TRANSPARENT, 34, 17f, colors) {
                if (emoji != "•••") currentInputConnection?.commitText(emoji, 1)
            })
        }
        root.addView(emojis)

        if (!sinhala) {
            val numbers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            "1234567890".forEach { number ->
                numbers.addView(makeKey(number.toString(), 1f, colors.key, 34, 12f, colors) {
                    currentInputConnection?.commitText(number.toString(), 1)
                })
            }
            root.addView(numbers)
        }

        val rows = if (sinhala) listOf(
            listOf("්", "ා", "ැ", "ෑ", "ි", "ී", "ු", "ූ", "ෙ", "ේ"),
            listOf("අ", "ආ", "ඇ", "ඉ", "ඊ", "උ", "එ", "ඔ", "ක", "ග"),
            listOf("ච", "ජ", "ට", "ඩ", "ත", "ද", "න", "ප", "බ"),
            listOf("ම", "ය", "ර", "ල", "ව", "ස", "හ", "ළ", "ඟ")
        ) else listOf(
            "qwertyuiop".map { it.toString() },
            "asdfghjkl".map { it.toString() },
            "zxcvbnm".map { it.toString() }
        )

        rows.forEachIndexed { index, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val rowHeight = letterHeight + 4
            if (!sinhala && index == 1) {
                line.layoutParams = LinearLayout.LayoutParams(-1, dp(rowHeight)).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(10)
                }
            } else if (!sinhala && index == 2) {
                line.layoutParams = LinearLayout.LayoutParams(-1, dp(rowHeight)).apply {
                    leftMargin = dp(24)
                    rightMargin = dp(4)
                }
            } else {
                line.layoutParams = LinearLayout.LayoutParams(-1, dp(rowHeight))
            }

            row.forEach { character ->
                val displayed = if (!sinhala && shifted) character.uppercase() else character
                line.addView(makeKey(displayed, 1f, colors.key, letterHeight, 19f, colors) {
                    val value = if (!sinhala && shifted) character.uppercase() else character
                    currentInputConnection?.commitText(value, 1)
                    if (shifted && !sinhala) {
                        shifted = false
                        setInputView(buildKeyboard())
                    }
                })
            }
            root.addView(line)
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomHeight = (letterHeight - 1).coerceAtLeast(42)
        bottom.addView(makeKey("?123", 1.15f, colors.specialKey, bottomHeight, 13f, colors) {})
        bottom.addView(makeKey("⇧", .9f, colors.specialKey, bottomHeight, 19f, colors) {
            shifted = !shifted
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey("🌐", .9f, colors.specialKey, bottomHeight, 18f, colors) {
            sinhala = !sinhala
            shifted = false
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey("English", 3.0f, colors.specialKey, bottomHeight, 14f, colors) {
            currentInputConnection?.commitText(" ", 1)
        })
        bottom.addView(makeKey(".", .7f, colors.specialKey, bottomHeight, 18f, colors) {
            currentInputConnection?.commitText(".", 1)
        })
        bottom.addView(makeKey("⌫", 1.0f, colors.specialKey, bottomHeight, 18f, colors) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        bottom.addView(makeKey("↵", 1.0f, colors.accent, bottomHeight, 20f, colors) {
            currentInputConnection?.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
            )
            currentInputConnection?.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
            )
        })
        root.addView(bottom)

        return root
    }

    private fun makeKey(
        label: String,
        weight: Float,
        color: Int,
        height: Int,
        size: Float,
        colors: KeyboardPalette,
        action: () -> Unit
    ): View = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(colors.text)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.argb(35, Color.red(colors.text), Color.green(colors.text), Color.blue(colors.text)))
        }
        layoutParams = LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        elevation = dp(1).toFloat()
        setOnClickListener {
            alpha = 0.88f
            post { alpha = 1f }
            if (prefs.getBoolean("vibration", true)) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            if (prefs.getBoolean("sound", false)) {
                try {
                    (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                        .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
                } catch (_: Exception) { }
            }
            action()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
    }
}
