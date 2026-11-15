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
import android.widget.ScrollView
import android.widget.PopupWindow

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
        val height = prefs.getInt("height", 410).coerceIn(340, 460)
        val keyHeight = prefs.getInt("keySize", 56).coerceIn(48, 64)

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
        toolbar.addView(makeKey("▤", .8f, c.specialKey, 34, 18f, c) { showClipboardPopup() })
        toolbar.addView(TextView(this).apply {
            text = when (keyboardMode) {
                1 -> "SINGLISH  →  සිංහල"
                2 -> "සිංහල"
                else -> "H I M A"
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
        bottom.addView(makeKey(if (calculatorMode) "Scientific" else if (keyboardMode == 1) "Singlish" else "Hima Board", 2.8f,
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

    private fun showClipboardPopup() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val currentText = try {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            else ""
        } catch (_: Exception) { "" }

        fun readPins(): MutableList<String> {
            val encoded = prefs.getString("clipboard_pins", "").orEmpty()
            return encoded.split("|").filter { it.isNotBlank() }.mapNotNull {
                try {
                    String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
                } catch (_: Exception) { null }
            }.toMutableList()
        }
        fun savePins(items: List<String>) {
            val encoded = items.distinct().take(20).joinToString("|") {
                android.util.Base64.encodeToString(it.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            }
            prefs.edit().putString("clipboard_pins", encoded).apply()
        }
        fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER_VERTICAL
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(9), dp(12), dp(9))
            }
        fun chip(text: String, action: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(235, 91, 181), Color.rgb(129, 83, 244))
            ).apply { cornerRadius = dp(13).toFloat() }
            setOnClickListener { action() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 23, 42))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(68, 55, 91))
            }
            elevation = dp(12).toFloat()
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(label("▤  Clipboard", 19f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(label("✕", 18f, Color.LTGRAY).apply {
            gravity = Gravity.CENTER
            setOnClickListener { popupRef?.dismiss() }
        })
        panel.addView(titleRow)
        panel.addView(label("Recent copied text and your pinned notes", 12f, Color.LTGRAY))
        panel.addView(label("LATEST", 11f, Color.rgb(184, 145, 255), true))
        val latest = if (currentText.isBlank()) "Copy some text first, then open Clipboard again." else currentText
        val latestCard = label(latest.take(600), 14f, Color.WHITE).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(43, 37, 59))
                cornerRadius = dp(14).toFloat()
            }
            maxLines = 4
            setOnClickListener {
                if (currentText.isNotBlank()) {
                    currentInputConnection?.commitText(currentText, 1)
                    popupRef?.dismiss()
                }
            }
        }
        panel.addView(latestCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(5)
        })
        val pinCurrent = chip("＋  Pin latest", {
            if (currentText.isNotBlank()) {
                val pins = readPins()
                pins.remove(currentText)
                pins.add(0, currentText)
                savePins(pins)
                popupRef?.dismiss()
                showClipboardPopup()
            }
        })
        panel.addView(pinCurrent, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(8)
        })
        panel.addView(label("PINNED", 11f, Color.rgb(184, 145, 255), true))
        val pinnedScroll = ScrollView(this).apply {
            isFillViewport = false
        }
        val pinnedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pins = readPins()
        if (pins.isEmpty()) {
            pinnedList.addView(label("No pinned clips yet. Pin your useful text here.", 12f, Color.LTGRAY))
        } else {
            pins.forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(43, 37, 59))
                        cornerRadius = dp(12).toFloat()
                    }
                }
                row.addView(label(item.take(90), 13f, Color.WHITE).apply {
                    maxLines = 2
                    setOnClickListener {
                        currentInputConnection?.commitText(item, 1)
                        popupRef?.dismiss()
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("×", 19f, Color.rgb(255, 139, 174)).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        val updated = readPins()
                        updated.remove(item)
                        savePins(updated)
                        popupRef?.dismiss()
                        showClipboardPopup()
                    }
                })
                pinnedList.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(5)
                })
            }
        }
        pinnedScroll.addView(pinnedList)
        panel.addView(pinnedScroll, LinearLayout.LayoutParams(-1, dp(150)))
        panel.addView(label("Tap a clip to paste it. Pin important text to keep it here.", 11f, Color.LTGRAY))

        val popup = PopupWindow(panel, resources.displayMetrics.widthPixels - dp(28),
            dp(430), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(18).toFloat()
        }
        popupRef = popup
        keyboardRoot?.let { popup.showAtLocation(it, Gravity.CENTER, 0, 0) }
    }

    private var popupRef: PopupWindow? = null

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
