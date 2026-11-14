package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.content.ClipboardManager
import android.media.AudioManager
import android.net.Uri
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class VishKeyboardService : InputMethodService() {

    // 0 = English, 1 = Singlish transliteration, 2 = Sinhala key layout
    private var keyboardMode = 0
    private var shifted = false
    private var calculatorMode = false
    private var keyboardRoot: View? = null
    private val singlishBuffer = StringBuilder()

    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("theme", "height", "keySize", "color", "image", "sound", "vibration", "touchEffect", "keyOpacity", "keyRoundness", "fontStyle")) {
                if (keyboardRoot != null && currentInputConnection != null) {
                    setInputView(buildKeyboard())
                }
            }
        }

    private data class KeyboardPalette(
        val background: Int, val key: Int, val specialKey: Int,
        val accent: Int, val text: Int
    )

    private fun keyboardPalette(): KeyboardPalette =
        if (prefs.getString("theme", "dark") == "light") {
            KeyboardPalette(Color.rgb(231,237,246), Color.WHITE,
                Color.rgb(210,219,231), Color.rgb(45,112,220), Color.rgb(28,40,58))
        } else {
            KeyboardPalette(Color.rgb(12,20,27), Color.rgb(37,48,63),
                Color.rgb(53,64,79), Color.rgb(49,125,230), Color.WHITE)
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
        val c = keyboardPalette()
        val height = prefs.getInt("height", 390).coerceIn(320, 440)
        val keyHeight = prefs.getInt("keySize", 52).coerceIn(46, 60)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(height))
            setBackgroundColor(prefs.getInt("color", c.background))
        }
        keyboardRoot = root

        prefs.getString("image", null)?.let { raw ->
            try {
                contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { root.background = BitmapDrawable(resources, it) }
                }
            } catch (_: Exception) { }
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(makeKey("▤", .8f, c.specialKey, 34, 18f, c) { pasteClipboard() })
        toolbar.addView(TextView(this).apply {
            text = when (keyboardMode) {
                1 -> "SINGLISH  →  සිංහල"
                2 -> "සිංහල"
                else -> "V I S H"
            }
            textSize = 13f
            setTextColor(c.text)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        toolbar.addView(makeKey(if (calculatorMode) "ABC" else "SCI", .9f,
            if (calculatorMode) c.accent else c.specialKey, 34, 13f, c) {
            calculatorMode = !calculatorMode
            shifted = false
            setInputView(buildKeyboard())
        })
        root.addView(toolbar)

        val emojis = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("❤️", "💕", "🙂", "💔", "😣", "😘", "😒", "😁", "🥺", "•••").forEach { emoji ->
            emojis.addView(makeKey(emoji, 1f, Color.TRANSPARENT, 34, 17f, c) {
                if (emoji != "•••") commitPendingSinglish(emoji)
            })
        }
        root.addView(emojis)

        if (keyboardMode != 2) {
            val numbers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            "1234567890".forEach { n ->
                numbers.addView(makeKey(n.toString(), 1f, c.key, 32, 13f, c) {
                    commitPendingSinglish(n.toString())
                })
            }
            root.addView(numbers)
        }

        val rows = if (calculatorMode) listOf(
            listOf("sin", "cos", "tan", "π", "(", ")"),
            listOf("√", "x²", "^", "log", "ln", "÷"),
            listOf("7", "8", "9", "×", "⌫"),
            listOf("4", "5", "6", "+", "−"),
            listOf("1", "2", "3", "%", "="),
            listOf("0", ".", "Ans", "AC")
        ) else if (keyboardMode == 2) listOf(
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
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val rowHeight = keyHeight + 2
            line.layoutParams = LinearLayout.LayoutParams(-1, dp(rowHeight))
            if (keyboardMode != 2 && index == 1) {
                line.setPadding(dp(10), 0, dp(10), 0)
            }
            if (!calculatorMode && keyboardMode != 2 && index == 2) {
                line.addView(makeKey("⇧", 1.25f, c.specialKey, keyHeight, 21f, c) {
                    shifted = !shifted
                    setInputView(buildKeyboard())
                })
            }

            row.forEach { character ->
                val shown = if (!calculatorMode && keyboardMode != 2 && shifted) character.uppercase() else character
                val specialCalc = calculatorMode && character in listOf("⌫", "AC", "=")
                line.addView(makeKey(shown, if (calculatorMode && specialCalc) 1.12f else 1f,
                    if (calculatorMode && character == "=") c.accent else if (specialCalc) c.specialKey else c.key,
                    keyHeight, if (character.length > 2) 13f else 19f, c) {
                    if (calculatorMode) {
                        when (character) {
                            "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
                            "AC" -> currentInputConnection?.commitText("", 1)
                            "Ans" -> currentInputConnection?.commitText("Ans", 1)
                            "sin", "cos", "tan", "log", "ln" -> currentInputConnection?.commitText("$character(", 1)
                            "√" -> currentInputConnection?.commitText("√(", 1)
                            "×" -> currentInputConnection?.commitText("*", 1)
                            "÷" -> currentInputConnection?.commitText("/", 1)
                            "−" -> currentInputConnection?.commitText("-", 1)
                            else -> currentInputConnection?.commitText(character, 1)
                        }
                    } else if (keyboardMode == 1) {
                        singlishBuffer.append(character)
                        currentInputConnection?.setComposingText(transliterate(singlishBuffer.toString()), 1)
                    } else {
                        val value = if (keyboardMode == 0 && shifted) character.uppercase() else character
                        currentInputConnection?.commitText(value, 1)
                    }
                    if (shifted && keyboardMode == 0) {
                        shifted = false
                        setInputView(buildKeyboard())
                    }
                })
            }

            if (!calculatorMode && keyboardMode != 2 && index == 2) {
                line.addView(makeKey("⌫", 1.25f, c.specialKey, keyHeight, 20f, c) {
                    if (keyboardMode == 1 && singlishBuffer.isNotEmpty()) {
                        singlishBuffer.deleteCharAt(singlishBuffer.length - 1)
                        if (singlishBuffer.isEmpty()) currentInputConnection?.setComposingText("", 1)
                        else currentInputConnection?.setComposingText(transliterate(singlishBuffer.toString()), 1)
                    } else currentInputConnection?.deleteSurroundingText(1, 0)
                })
            }
            root.addView(line)
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomHeight = (keyHeight + 1).coerceAtLeast(46)
        bottom.addView(makeKey(if (calculatorMode) "ABC" else "?123", 1.15f, c.specialKey, bottomHeight, 13f, c) {
            calculatorMode = false
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey("☺", .8f, c.specialKey, bottomHeight, 19f, c) {
            commitPendingSinglish("🙂")
        })
        bottom.addView(makeKey("🌐", .85f, c.specialKey, bottomHeight, 18f, c) {
            commitPendingSinglish("")
            keyboardMode = (keyboardMode + 1) % 3
            shifted = false
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey(if (calculatorMode) "Scientific" else if (keyboardMode == 1) "Singlish" else "VISH Keyboard", 2.8f,
            c.key, bottomHeight, 13f, c) { if (!calculatorMode) commitPendingSinglish(" ") })
        bottom.addView(makeKey(",", .65f, c.specialKey, bottomHeight, 18f, c) {
            commitPendingSinglish(",")
        })
        bottom.addView(makeKey(".", .65f, c.specialKey, bottomHeight, 18f, c) {
            commitPendingSinglish(".")
        })
        bottom.addView(makeKey("↵", .9f, c.accent, bottomHeight, 20f, c) {
            commitPendingSinglish("")
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_ENTER))
        })
        root.addView(bottom)
        return root
    }

    private fun pasteClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val value = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
                if (value.isNotEmpty()) {
                    commitPendingSinglish("")
                    currentInputConnection?.commitText(value, 1)
                }
            }
        } catch (_: Exception) { }
    }

    private fun commitPendingSinglish(after: String) {
        if (keyboardMode == 1 && singlishBuffer.isNotEmpty()) {
            currentInputConnection?.commitText(transliterate(singlishBuffer.toString()) + after, 1)
            singlishBuffer.clear()
        } else if (after.isNotEmpty()) currentInputConnection?.commitText(after, 1)
    }

    // Basic phonetic Singlish transliteration. Users can type common forms such as "mama", "oya", "amma".
    private fun transliterate(input: String): String {
        val consonants = linkedMapOf(
            "kh" to "ඛ", "gh" to "ඝ", "ch" to "ච", "jh" to "ඣ", "th" to "ත",
            "dh" to "ධ", "ph" to "ඵ", "bh" to "භ", "sh" to "ශ", "ng" to "ඟ",
            "ny" to "ඤ", "k" to "ක", "g" to "ග", "c" to "ච", "j" to "ජ",
            "t" to "ට", "d" to "ඩ", "n" to "න", "p" to "ප", "b" to "බ",
            "m" to "ම", "y" to "ය", "r" to "ර", "l" to "ල", "v" to "ව",
            "w" to "ව", "s" to "ස", "h" to "හ", "f" to "ෆ"
        )
        val vowels = linkedMapOf(
            "aa" to "ා", "a" to "", "ii" to "ී", "ee" to "ී", "i" to "ි",
            "uu" to "ූ", "oo" to "ූ", "u" to "ු", "e" to "ෙ", "ai" to "ෛ",
            "o" to "ො", "au" to "ෞ"
        )
        val independent = mapOf("a" to "අ", "aa" to "ආ", "i" to "ඉ", "ii" to "ඊ",
            "u" to "උ", "uu" to "ඌ", "e" to "එ", "ee" to "ඒ", "o" to "ඔ", "oo" to "ඕ")
        val src = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            if (src[i].isWhitespace()) { out.append(src[i]); i++; continue }
            if (!src[i].isLetter()) { out.append(src[i]); i++; continue }
            var cToken: String? = null
            for (len in 2 downTo 1) {
                if (i + len <= src.length) {
                    val candidate = src.substring(i, i + len)
                    if (consonants.containsKey(candidate)) { cToken = candidate; break }
                }
            }
            if (cToken != null) {
                val base = consonants[cToken]!!
                i += cToken.length
                var vToken: String? = null
                for (len in 2 downTo 1) {
                    if (i + len <= src.length) {
                        val candidate = src.substring(i, i + len)
                        if (vowels.containsKey(candidate)) { vToken = candidate; break }
                    }
                }
                if (vToken != null) {
                    out.append(base).append(vowels[vToken])
                    i += vToken.length
                } else out.append(base).append("්")
            } else {
                var vToken: String? = null
                for (len in 2 downTo 1) {
                    if (i + len <= src.length) {
                        val candidate = src.substring(i, i + len)
                        if (independent.containsKey(candidate)) { vToken = candidate; break }
                    }
                }
                if (vToken != null) {
                    out.append(independent[vToken]!!)
                    i += vToken.length
                } else { out.append(src[i]); i++ }
            }
        }
        return out.toString()
    }

    private fun makeKey(
        label: String, weight: Float, color: Int, height: Int, size: Float,
        colors: KeyboardPalette, action: () -> Unit
    ): View = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(colors.text)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT
        val opacity = prefs.getInt("keyOpacity", 94).coerceIn(35, 100)
        val alphaColor = Color.argb((opacity * 255 / 100), Color.red(color), Color.green(color), Color.blue(color))
        val shape = GradientDrawable().apply {
            setColor(alphaColor)
            cornerRadius = dp(prefs.getInt("keyRoundness", 12).coerceIn(4, 22)).toFloat()
            setStroke(dp(1), Color.argb(34, Color.red(colors.text), Color.green(colors.text), Color.blue(colors.text)))
        }
        background = if (prefs.getBoolean("touchEffect", true)) {
            RippleDrawable(ColorStateList.valueOf(Color.argb(100, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent))), shape, null)
        } else shape
        typeface = if (prefs.getString("fontStyle", "rounded") == "mono") Typeface.MONOSPACE else Typeface.DEFAULT
        layoutParams = LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        elevation = dp(1).toFloat()
        setOnClickListener {
            alpha = .86f
            post { alpha = 1f }
            if (prefs.getBoolean("vibration", true)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (prefs.getBoolean("sound", false)) {
                try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD) } catch (_: Exception) {}
            }
            action()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
        singlishBuffer.clear()
    }
}
