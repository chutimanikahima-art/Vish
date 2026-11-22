package com.vish.keyboard

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.content.ClipboardManager
import android.widget.EditText
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VishKeyboardService : InputMethodService() {

    // 0 = English, 1 = Singlish transliteration, 2 = Sinhala key layout
    private var keyboardMode = 0
    private var shifted = false
    private var calculatorMode = false
    private var symbolMode = false
    private var ctrlArmed = false
    private var altArmed = false
    private val calculatorExpression = StringBuilder()
    private var calculatorAnswer = ""
    private var keyboardRoot: View? = null
    private var clockTextView: TextView? = null
    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val clockTick = object : Runnable {
        override fun run() {
            clockTextView?.text = "◷ " + clockFormat.format(Date())
            clockHandler.postDelayed(this, 1000L)
        }
    }
    private val singlishBuffer = StringBuilder()
    private val repeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val repeatDeleteRunnable = object : Runnable {
        override fun run() {
            deleteOneCharacter()
            repeatHandler.postDelayed(this, 65L)
        }
    }

    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("theme", "height", "keySize", "color", "image", "customKeyColor", "sound", "vibration", "touchEffect", "keyOpacity", "keyRoundness", "fontStyle", "keyboardStyle", "emojiRow", "numberRow", "devNavRow", "favorite_emojis")) {
                if (keyboardRoot != null && currentInputConnection != null) {
                    setInputView(buildKeyboard())
                }
            }
        }

    private data class KeyboardPalette(
        val background: Int, val key: Int, val specialKey: Int,
        val accent: Int, val text: Int
    )

    private fun keyboardPalette(): KeyboardPalette {
        val base = when (prefs.getString("keyboardStyle", "normal")) {
            "cute" -> KeyboardPalette(Color.rgb(250, 225, 240), Color.rgb(255, 247, 252),
                Color.rgb(244, 202, 228), Color.rgb(190, 83, 164), Color.rgb(72, 43, 69))
            "developer" -> KeyboardPalette(Color.rgb(12, 15, 22), Color.rgb(31, 39, 52),
                Color.rgb(43, 53, 70), Color.rgb(70, 145, 255), Color.rgb(230, 238, 250))
            else -> if (prefs.getString("theme", "dark") == "light") {
                KeyboardPalette(Color.rgb(231,237,246), Color.WHITE,
                    Color.rgb(210,219,231), Color.rgb(45,112,220), Color.rgb(28,40,58))
            } else {
                KeyboardPalette(Color.rgb(12,20,27), Color.rgb(37,48,63),
                    Color.rgb(53,64,79), Color.rgb(49,125,230), Color.WHITE)
            }
        }
        return if (prefs.contains("customKeyColor")) {
            val custom = prefs.getInt("customKeyColor", base.key)
            val brightness = (Color.red(custom) * 299 + Color.green(custom) * 587 + Color.blue(custom) * 114) / 1000
            base.copy(key = custom, text = if (brightness < 135) Color.WHITE else Color.rgb(35, 39, 48))
        } else base
    }

    override fun onCreate() {
        super.onCreate()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val c = keyboardPalette()
        val height = prefs.getInt("height", 410).coerceIn(340, 460)
        val styleForSize = prefs.getString("keyboardStyle", "normal")
        val keyHeight = if (calculatorMode) 42 else if (styleForSize == "developer") 43 else prefs.getInt("keySize", 56).coerceIn(48, 64)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(height))
            setBackgroundColor(if (prefs.contains("color")) prefs.getInt("color", c.background) else c.background)
        }
        keyboardRoot = root
        root.foreground = RgbRippleDrawable()

        prefs.getString("image", null)?.let { raw ->
            try {
                val imageUri = Uri.parse(raw)
                val targetWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                val targetHeight = dp(height).coerceAtLeast(1)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > targetWidth * 2 || bounds.outHeight / sampleSize > targetHeight * 2) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
                contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)?.let { source ->
                        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
                        val sourceRatio = source.width.toFloat() / source.height.toFloat()
                        val cropWidth: Int
                        val cropHeight: Int
                        if (sourceRatio > targetRatio) {
                            cropHeight = source.height
                            cropWidth = (source.height * targetRatio).toInt().coerceIn(1, source.width)
                        } else {
                            cropWidth = source.width
                            cropHeight = (source.width / targetRatio).toInt().coerceIn(1, source.height)
                        }
                        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
                        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
                        val centerCrop = android.graphics.Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
                        root.background = BitmapDrawable(resources, centerCrop).apply {
                            gravity = Gravity.FILL
                            setFilterBitmap(true)
                        }
                        if (centerCrop !== source) source.recycle()
                    }
                }
            } catch (_: Exception) { }
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(makeKey("▤", .8f, c.specialKey, 34, 18f, c) { showClipboardPopup() })
        toolbar.addView(makeKey("✦", .72f, c.specialKey, 34, 17f, c) { showSnippetsPopup() })
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        brand.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.hima_logo)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(25), dp(25)))
        brand.addView(TextView(this).apply {
            text = when (keyboardMode) {
                1 -> "Singlish → සිංහල"
                2 -> "සිංහල"
                else -> when (prefs.getString("keyboardStyle", "normal")) {
                    "cute" -> "HIMA ♡"
                    "developer" -> "HIMA / DEV"
                    else -> "HIMA Board"
                }
            }
            textSize = 12f
            setTextColor(c.text)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, 0, 0)
        })
        brand.isClickable = true
        brand.isFocusable = true
        brand.setOnClickListener { showLoveAnimation() }
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(34), 1f))
        val clockView = TextView(this).apply {
            text = "◷ " + clockFormat.format(Date())
            textSize = 10.5f
            setTextColor(c.text)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { showClockPopup() }
        }
        clockTextView = clockView
        toolbar.addView(clockView, LinearLayout.LayoutParams(dp(92), dp(34)))
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
        toolbar.addView(makeKey(if (calculatorMode) "ABC" else "SCI", .9f,
            if (calculatorMode) c.accent else c.specialKey, 34, 13f, c) {
            calculatorMode = !calculatorMode
            symbolMode = false
            shifted = false
            setInputView(buildKeyboard())
        })
        root.addView(toolbar)

        val style = prefs.getString("keyboardStyle", "normal") ?: "normal"
        if (style != "developer" && prefs.getBoolean("emojiRow", true)) {
            val emojis = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val savedEmojis = prefs.getString("favorite_emojis", "").orEmpty()
                .split("|").filter { it.isNotBlank() }.take(7)
            val defaultEmojis = if (style == "cute")
                listOf("🩷", "🥰", "✨", "🎀", "😊", "💕", "🦋")
            else listOf("❤️", "💕", "🙂", "😂", "🥺", "😘", "🔥")
            val emojiSet = (savedEmojis + defaultEmojis).distinct().take(8) + "•••"
            emojiSet.forEach { emoji ->
                emojis.addView(makeKey(emoji, 1f, Color.TRANSPARENT, 34, 17f, c) {
                    if (emoji == "•••") showEmojiPopup()
                    else commitPendingSinglish(emoji)
                })
            }
            root.addView(emojis)
        }

        if (!symbolMode && keyboardMode != 2 && prefs.getBoolean("numberRow", true) && style != "developer") {
            val numbers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            "1234567890".forEach { n ->
                numbers.addView(makeKey(n.toString(), 1f, c.key, 32, 13f, c) {
                    commitPendingSinglish(n.toString())
                })
            }
            root.addView(numbers)
        }

        if (style == "developer" && prefs.getBoolean("devNavRow", true) && !calculatorMode) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("Tab", "Esc", "Ctrl", "Alt", "Home", "End", "←", "↑", "↓", "→").forEach { key ->
                nav.addView(makeKey(key, 1f, c.specialKey, 34, 11f, c) {
                    when (key) {
                        "Ctrl" -> { ctrlArmed = !ctrlArmed; altArmed = false }
                        "Alt" -> { altArmed = !altArmed; ctrlArmed = false }
                        "Esc" -> sendArrow(android.view.KeyEvent.KEYCODE_ESCAPE)
                        "Home" -> sendArrow(android.view.KeyEvent.KEYCODE_MOVE_HOME)
                        "End" -> sendArrow(android.view.KeyEvent.KEYCODE_MOVE_END)
                        "←" -> sendArrow(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                        "↑" -> sendArrow(android.view.KeyEvent.KEYCODE_DPAD_UP)
                        "↓" -> sendArrow(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                        "→" -> sendArrow(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                        "Tab" -> currentInputConnection?.commitText("\t", 1)
                    }
                })
            }
            root.addView(nav)
            val code = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("(", ")", "[", "]", "{", "}", "<", ">", "=", ";", "_", "\\").forEach { symbol ->
                code.addView(makeKey(symbol, 1f, c.key, 32, 14f, c) { currentInputConnection?.commitText(symbol, 1) })
            }
            root.addView(code)
        }

        if (calculatorMode) {
            val calcDisplay = TextView(this).apply {
                text = if (calculatorAnswer.isNotBlank())
                    "${calculatorExpression}\\n= $calculatorAnswer"
                else if (calculatorExpression.isNotBlank()) calculatorExpression.toString()
                else "Scientific calculator  •  Enter an expression"
                textSize = 17f
                setTextColor(c.text)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(5), dp(12), dp(5))
                maxLines = 2
                background = GradientDrawable().apply {
                    setColor(c.specialKey)
                    cornerRadius = dp(13).toFloat()
                }
            }
            root.addView(calcDisplay, LinearLayout.LayoutParams(-1, dp(48)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(5))
            })
        }

        val rows = if (calculatorMode) listOf(
            listOf("sin", "cos", "tan", "π", "e", "("),
            listOf("√", "x²", "^", "log", "ln", ")"),
            listOf("7", "8", "9", "÷", "⌫"),
            listOf("4", "5", "6", "×", "−"),
            listOf("1", "2", "3", "%", "+"),
            listOf("0", ".", "Ans", "AC", "=")
        ) else if (symbolMode) listOf(
            listOf("1","2","3","4","5","6","7","8","9","0"),
            listOf("@","#","$","%","&","-","+","(",")","/"),
            listOf("*","\"","' ",":",";","!","?","₹","₨","⌫"),
            listOf("ABC","=","<",">","[","]","{","}","\\","~")
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
            if (!calculatorMode && !symbolMode && keyboardMode != 2 && index == 2) {
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
                            "⌫" -> {
                                if (calculatorExpression.isNotEmpty()) calculatorExpression.deleteCharAt(calculatorExpression.length - 1)
                                calculatorAnswer = ""
                                setInputView(buildKeyboard())
                            }
                            "AC" -> {
                                calculatorExpression.clear()
                                calculatorAnswer = ""
                                setInputView(buildKeyboard())
                            }
                            "Ans" -> {
                                if (calculatorAnswer.isNotBlank()) currentInputConnection?.commitText(calculatorAnswer, 1)
                            }
                            "=" -> {
                                try {
                                    calculatorAnswer = ScientificMath.evaluate(calculatorExpression.toString())
                                    setInputView(buildKeyboard())
                                } catch (_: Exception) {
                                    calculatorAnswer = "Error"
                                    setInputView(buildKeyboard())
                                }
                            }
                            "sin", "cos", "tan", "log", "ln" -> {
                                calculatorExpression.append("$character(")
                                calculatorAnswer = ""
                                setInputView(buildKeyboard())
                            }
                            "√" -> { calculatorExpression.append("sqrt("); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            "×" -> { calculatorExpression.append("*"); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            "÷" -> { calculatorExpression.append("/"); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            "−" -> { calculatorExpression.append("-"); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            "x²" -> { calculatorExpression.append("^2"); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            "π" -> { calculatorExpression.append("pi"); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                            else -> { calculatorExpression.append(character); calculatorAnswer = ""; setInputView(buildKeyboard()) }
                        }
                    } else if (symbolMode && character == "⌫") {
                        currentInputConnection?.deleteSurroundingText(1, 0)
                    } else if (symbolMode && character == "ABC") {
                        symbolMode = false
                        setInputView(buildKeyboard())
                    } else if ((ctrlArmed || altArmed) && character.length == 1 && character[0].isLetter()) {
                        val keyCode = letterKeyCode(character[0].lowercaseChar())
                        if (keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                            val meta = (if (ctrlArmed) android.view.KeyEvent.META_CTRL_ON else 0) or
                                (if (altArmed) android.view.KeyEvent.META_ALT_ON else 0)
                            val eventTime = android.os.SystemClock.uptimeMillis()
                            currentInputConnection?.sendKeyEvent(
                                android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, meta)
                            )
                            currentInputConnection?.sendKeyEvent(
                                android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_UP, keyCode, 0, meta)
                            )
                        }
                        ctrlArmed = false
                        altArmed = false
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

            if (!calculatorMode && !symbolMode && keyboardMode != 2 && index == 2) {
                val backspaceKey = makeKey("⌫", 1.25f, c.specialKey, keyHeight, 20f, c) {
                    deleteOneCharacter()
                }
                backspaceKey.setOnLongClickListener {
                    repeatHandler.removeCallbacks(repeatDeleteRunnable)
                    repeatHandler.postDelayed(repeatDeleteRunnable, 350L)
                    true
                }
                backspaceKey.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                        repeatHandler.removeCallbacks(repeatDeleteRunnable)
                    }
                    false
                }
                line.addView(backspaceKey)
            }
            root.addView(line)
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomHeight = (keyHeight + 1).coerceAtLeast(46)
        bottom.addView(makeKey(if (calculatorMode || symbolMode) "ABC" else "?123", 1.15f, c.specialKey, bottomHeight, 13f, c) {
            if (calculatorMode) {
                calculatorMode = false
                symbolMode = false
            } else {
                symbolMode = !symbolMode
            }
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey(if (style == "cute") "❤️" else "☺", .8f,
            if (style == "cute") c.accent else c.specialKey, bottomHeight, 18f, c) {
            commitPendingSinglish(if (style == "cute") "❤️" else "🙂")
        })
        bottom.addView(makeKey("🌐", .85f, c.specialKey, bottomHeight, 18f, c) {
            commitPendingSinglish("")
            keyboardMode = (keyboardMode + 1) % 3
            shifted = false
            setInputView(buildKeyboard())
        })
        bottom.addView(makeKey(if (calculatorMode) "Scientific" else if (keyboardMode == 1) "Singlish" else "English", 2.8f,
            c.key, bottomHeight, 13f, c) {
                if (!calculatorMode) commitPendingSinglish(" ")
            })
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

    private fun letterKeyCode(ch: Char): Int = when (ch) {
        'a' -> android.view.KeyEvent.KEYCODE_A; 'b' -> android.view.KeyEvent.KEYCODE_B
        'c' -> android.view.KeyEvent.KEYCODE_C; 'd' -> android.view.KeyEvent.KEYCODE_D
        'e' -> android.view.KeyEvent.KEYCODE_E; 'f' -> android.view.KeyEvent.KEYCODE_F
        'g' -> android.view.KeyEvent.KEYCODE_G; 'h' -> android.view.KeyEvent.KEYCODE_H
        'i' -> android.view.KeyEvent.KEYCODE_I; 'j' -> android.view.KeyEvent.KEYCODE_J
        'k' -> android.view.KeyEvent.KEYCODE_K; 'l' -> android.view.KeyEvent.KEYCODE_L
        'm' -> android.view.KeyEvent.KEYCODE_M; 'n' -> android.view.KeyEvent.KEYCODE_N
        'o' -> android.view.KeyEvent.KEYCODE_O; 'p' -> android.view.KeyEvent.KEYCODE_P
        'q' -> android.view.KeyEvent.KEYCODE_Q; 'r' -> android.view.KeyEvent.KEYCODE_R
        's' -> android.view.KeyEvent.KEYCODE_S; 't' -> android.view.KeyEvent.KEYCODE_T
        'u' -> android.view.KeyEvent.KEYCODE_U; 'v' -> android.view.KeyEvent.KEYCODE_V
        'w' -> android.view.KeyEvent.KEYCODE_W; 'x' -> android.view.KeyEvent.KEYCODE_X
        'y' -> android.view.KeyEvent.KEYCODE_Y; 'z' -> android.view.KeyEvent.KEYCODE_Z
        else -> android.view.KeyEvent.KEYCODE_UNKNOWN
    }

    private fun sendArrow(code: Int) {
        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    }

    private fun readSnippets(): MutableList<String> {
        val encoded = prefs.getString("custom_snippets", "").orEmpty()
        return encoded.split("|").filter { it.isNotBlank() }.mapNotNull {
            try { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8) }
            catch (_: Exception) { null }
        }.toMutableList()
    }

    private fun saveSnippets(items: List<String>) {
        val encoded = items.distinct().take(40).joinToString("|") {
            android.util.Base64.encodeToString(it.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        }
        prefs.edit().putString("custom_snippets", encoded).apply()
    }

    private fun showSnippetsPopup() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(Color.rgb(27, 23, 42)); cornerRadius = dp(22).toFloat() }
        }
        val title = TextView(this).apply {
            text = "✦  Quick phrases"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(7), dp(8), dp(12))
        }
        panel.addView(title)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val defaults = listOf("Hello! 👋", "Thank you! ❤️", "I will reply soon.", "On my way!", "Good morning ☀️", "සුබ උදෑසනක්!", "ස්තුතියි ❤️", "මම පස්සේ කියන්නම්.")
        (defaults + readSnippets()).distinct().forEach { phrase ->
            val item = TextView(this).apply {
                text = phrase
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = GradientDrawable().apply { setColor(Color.rgb(47, 40, 63)); cornerRadius = dp(12).toFloat() }
                setOnClickListener { currentInputConnection?.commitText(phrase, 1); popupRef?.dismiss() }
            }
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(230)))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply {
            hint = "Type a phrase to save"
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            background = GradientDrawable().apply { setColor(Color.rgb(48, 41, 63)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(10), 0, dp(10), 0)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
        val add = TextView(this).apply {
            text = "+ Save phrase"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(235, 91, 181), Color.rgb(129, 83, 244))).apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) { val items = readSnippets(); items.add(0, value); saveSnippets(items); popupRef?.dismiss(); showSnippetsPopup() }
            }
        }
        actions.addView(add, LinearLayout.LayoutParams(0, dp(44), 1f))
        val close = TextView(this).apply {
            text = "Close"; gravity = Gravity.CENTER; textSize = 13f; setTextColor(Color.WHITE)
            setOnClickListener { popupRef?.dismiss() }
        }
        actions.addView(close, LinearLayout.LayoutParams(dp(70), dp(44)))
        panel.addView(actions)
        val popup = PopupWindow(panel, resources.displayMetrics.widthPixels - dp(32), dp(390), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(16).toFloat()
        }
        popupRef = popup
        keyboardRoot?.let { popup.showAtLocation(it, Gravity.CENTER, 0, 0) }
    }


    private fun deleteOneCharacter() {
        if (keyboardMode == 1 && singlishBuffer.isNotEmpty()) {
            singlishBuffer.deleteCharAt(singlishBuffer.length - 1)
            if (singlishBuffer.isEmpty()) currentInputConnection?.setComposingText("", 1)
            else currentInputConnection?.setComposingText(transliterate(singlishBuffer.toString()), 1)
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun showClockPopup() {
        val now = Date()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(34, 29, 55), Color.rgb(15, 23, 38))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(183, 105, 235))
            }
            elevation = dp(18).toFloat()
        }
        panel.addView(TextView(this).apply {
            text = "HIMA CLOCK"
            textSize = 12f
            letterSpacing = .18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(226, 177, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(24)))
        val bigTime = TextView(this).apply {
            text = clockFormat.format(now)
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        panel.addView(bigTime, LinearLayout.LayoutParams(-1, dp(54)))
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        panel.addView(TextView(this).apply {
            text = dateFormat.format(now)
            textSize = 14f
            setTextColor(Color.rgb(211, 211, 228))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))
        panel.addView(TextView(this).apply {
            text = "Sri Lanka time  •  UTC +5:30"
            textSize = 11f
            setTextColor(Color.rgb(170, 163, 190))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(25)))
        val popup = PopupWindow(panel, dp(300), dp(180), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(18).toFloat()
        }
        popupRef = popup
        keyboardRoot?.let { popup.showAtLocation(it, Gravity.CENTER, 0, 0) }
    }

    private fun showLoveAnimation() {
        val message = TextView(this).apply {
            text = "I love you\nchuti manika\nhimasha❤️vishath"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(238, 91, 174), Color.rgb(132, 83, 244))
            ).apply { cornerRadius = dp(28).toFloat() }
            elevation = dp(16).toFloat()
            alpha = 0f
            scaleX = .55f
            scaleY = .55f
        }
        val popup = PopupWindow(message, dp(300), dp(118), false).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        val anchor = keyboardRoot ?: return
        popup.showAtLocation(anchor, Gravity.CENTER, 0, -dp(28))
        message.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).withEndAction {
            message.animate().alpha(0f).scaleX(1.12f).scaleY(1.12f)
                .setStartDelay(1050).setDuration(380).withEndAction { popup.dismiss() }.start()
        }.start()
    }

    private fun showEmojiPopup() {
        val all = listOf(
            "❤️","🩷","🧡","💛","💚","🩵","💙","💜","🤎","🖤","🤍","💕","💞","💓","💗","💖","💘","💝","💟","❣️","💔","❤️‍🩹",
            "😀","😃","😄","😁","😆","🥹","😊","😇","🙂","🙃","😉","😍","🥰","😘","😗","😙","😚","🤗","🤭","🫶","🤔","😎",
            "😂","🤣","😭","🥺","😢","😤","😡","🤯","😴","🤒","😷","🤩","😋","😜","🤪","😏","🙄","😬","😳","🫣",
            "🌸","🌷","🌹","🌺","🌻","🌼","🪷","🦋","🐻","🐼","🐰","🐱","🐶","🐣","🦄","🌈","⭐","✨","🌙","☀️",
            "🎀","🎉","🎂","🎁","💐","🍓","🍒","🍑","🍉","🍫","☕","🧸","💍","👑","🔥","💯","🙏","👏","👍","👎"
        )
        val favorites = prefs.getString("favorite_emojis", "").orEmpty()
            .split("|").filter { it.isNotBlank() }.toMutableList()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 23, 42))
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(78, 60, 103))
            }
            elevation = dp(18).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "♡  Emoji library"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setOnClickListener { popupRef?.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        panel.addView(header)
        panel.addView(TextView(this).apply {
            text = "Tap an emoji to type it • ☆ to pin it to your keyboard bar"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), 0, 0, dp(8))
        })
        panel.addView(TextView(this).apply {
            text = "YOUR FAVORITES"
            textSize = 11f
            setTextColor(Color.rgb(211, 151, 255))
            setPadding(dp(4), dp(5), 0, dp(5))
        })
        val favRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        (favorites.take(8).ifEmpty { listOf("❤️","🥰","✨","🌸") }).forEach { em ->
            val item = TextView(this).apply {
                text = em
                textSize = 23f
                gravity = Gravity.CENTER
                setOnClickListener { commitPendingSinglish(em); popupRef?.dismiss() }
            }
            favRow.addView(item, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        panel.addView(favRow)
        val scroll = ScrollView(this)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        all.chunked(6).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { em ->
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                val emojiButton = TextView(this).apply {
                    text = em
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setOnClickListener { commitPendingSinglish(em); popupRef?.dismiss() }
                }
                cell.addView(emojiButton, LinearLayout.LayoutParams(-1, dp(42)))
                val pin = TextView(this).apply {
                    text = if (favorites.contains(em)) "★" else "☆"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(218, 166, 255))
                    setOnClickListener {
                        if (favorites.contains(em)) favorites.remove(em)
                        else if (favorites.size < 8) favorites.add(0, em)
                        prefs.edit().putString("favorite_emojis", favorites.distinct().joinToString("|")).apply()
                        popupRef?.dismiss()
                        showEmojiPopup()
                        keyboardRoot?.let { setInputView(buildKeyboard()) }
                    }
                }
                cell.addView(pin, LinearLayout.LayoutParams(-1, dp(22)))
                row.addView(cell, LinearLayout.LayoutParams(0, dp(64), 1f))
            }
            repeat(6 - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(64), 1f)) }
            grid.addView(row)
        }
        scroll.addView(grid)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(310)))
        val popup = PopupWindow(panel, resources.displayMetrics.widthPixels - dp(26), dp(470), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(20).toFloat()
        }
        popupRef = popup
        keyboardRoot?.let { popup.showAtLocation(it, Gravity.CENTER, 0, 0) }
    }

    private object ScientificMath {
        fun evaluate(source: String): String {
            val parser = Parser(source)
            val value = parser.parse()
            if (!value.isFinite()) throw IllegalArgumentException("Invalid result")
            val rounded = if (kotlin.math.abs(value - kotlin.math.round(value)) < 1e-10)
                kotlin.math.round(value).toLong().toString()
            else String.format(java.util.Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
            return rounded
        }

        private class Parser(private val input: String) {
            private var pos = 0
            fun parse(): Double {
                val result = expression()
                skip()
                if (pos != input.length) error("Unexpected character")
                return result
            }
            private fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
            private fun eat(c: Char): Boolean {
                skip()
                if (pos < input.length && input[pos] == c) { pos++; return true }
                return false
            }
            private fun expression(): Double {
                var value = term()
                while (true) {
                    value = when {
                        eat('+') -> value + term()
                        eat('-') -> value - term()
                        else -> return value
                    }
                }
            }
            private fun term(): Double {
                var value = power()
                while (true) {
                    value = when {
                        eat('*') -> value * power()
                        eat('/') -> value / power()
                        eat('%') -> value % power()
                        else -> return value
                    }
                }
            }
            private fun power(): Double {
                var value = unary()
                if (eat('^')) value = Math.pow(value, power())
                return value
            }
            private fun unary(): Double {
                if (eat('+')) return unary()
                if (eat('-')) return -unary()
                return atom()
            }
            private fun atom(): Double {
                skip()
                if (eat('(')) {
                    val v = expression()
                    if (!eat(')')) error("Missing )")
                    return v
                }
                if (pos < input.length && input[pos].isDigit() || (pos < input.length && input[pos] == '.')) {
                    val start = pos
                    while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                    return input.substring(start, pos).toDouble()
                }
                val start = pos
                while (pos < input.length && input[pos].isLetter()) pos++
                val name = input.substring(start, pos).lowercase()
                if (name == "pi") return Math.PI
                if (name == "e") return Math.E
                if (name in listOf("sin","cos","tan","sqrt","log","ln")) {
                    if (!eat('(')) error("Missing function bracket")
                    val x = expression()
                    if (!eat(')')) error("Missing function )")
                    return when (name) {
                        "sin" -> kotlin.math.sin(Math.toRadians(x))
                        "cos" -> kotlin.math.cos(Math.toRadians(x))
                        "tan" -> kotlin.math.tan(Math.toRadians(x))
                        "sqrt" -> kotlin.math.sqrt(x)
                        "log" -> kotlin.math.log10(x)
                        else -> kotlin.math.ln(x)
                    }
                }
                error("Invalid expression")
            }
        }
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
            val style = prefs.getString("keyboardStyle", "normal")
            val defaultRadius = if (style == "cute") 17 else if (style == "developer") 6 else 12
            cornerRadius = dp(prefs.getInt("keyRoundness", defaultRadius).coerceIn(4, 22)).toFloat()
            setStroke(dp(1), Color.argb(34, Color.red(colors.text), Color.green(colors.text), Color.blue(colors.text)))
        }
        background = if (prefs.getBoolean("touchEffect", true)) {
            RippleDrawable(ColorStateList.valueOf(Color.argb(100, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent))), shape, null)
        } else shape
        typeface = if (prefs.getString("keyboardStyle", "normal") == "developer" || prefs.getString("fontStyle", "rounded") == "mono") Typeface.MONOSPACE else Typeface.DEFAULT
        layoutParams = LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        elevation = dp(1).toFloat()
        setOnClickListener {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            animate().scaleX(.94f).scaleY(.94f).setDuration(65).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(105).start()
            }.start()
            startRgbRipple(this)
            if (prefs.getBoolean("vibration", true)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (prefs.getBoolean("sound", false)) {
                try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD) } catch (_: Exception) {}
            }
            action()
        }
    }

    private fun startRgbRipple(source: View) {
        val root = keyboardRoot ?: return
        val rootLocation = IntArray(2)
        val keyLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        source.getLocationOnScreen(keyLocation)
        val x = keyLocation[0] - rootLocation[0] + source.width / 2f
        val y = keyLocation[1] - rootLocation[1] + source.height / 2f
        (root.foreground as? RgbRippleDrawable)?.start(x, y)
    }

    /** A water-like RGB pulse that begins at the pressed key and travels around
     * the keyboard's outer edge before fading away. */
    private inner class RgbRippleDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        private val segment = Path()
        private val rect = RectF()
        private var animator: ValueAnimator? = null
        private var progress = 0f
        private var touchX = 0f
        private var touchY = 0f
        private var startDistance = 0f
        private var perimeter = 1f
        private var alphaValue = 255
        private val rainbow = intArrayOf(
            Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED,
            Color.YELLOW, Color.GREEN, Color.CYAN
        )

        fun start(x: Float, y: Float) {
            touchX = x
            touchY = y
            progress = 0f
            alphaValue = 255
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1.25f).apply {
                duration = 1650L
                interpolator = LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    alphaValue = if (progress <= 1f) 255 else
                        (255f * (1f - ((progress - 1f) / .25f))).toInt().coerceIn(0, 255)
                    invalidateSelf()
                }
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0 || progress <= 0f) return
            val inset = 7f
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            path.reset()
            path.addRoundRect(rect, 22f, 22f, Path.Direction.CW)
            val measure = PathMeasure(path, false)
            perimeter = measure.length.coerceAtLeast(1f)

            val px = touchX.coerceIn(rect.left, rect.right)
            val py = touchY.coerceIn(rect.top, rect.bottom)
            val distances = floatArrayOf(
                kotlin.math.abs(py - rect.top),
                kotlin.math.abs(px - rect.right),
                kotlin.math.abs(py - rect.bottom),
                kotlin.math.abs(px - rect.left)
            )
            val edge = distances.indices.minByOrNull { distances[it] } ?: 0
            startDistance = when (edge) {
                0 -> (px - rect.left) / rect.width().coerceAtLeast(1f) * perimeter * .25f
                1 -> perimeter * (.25f + (py - rect.top) / rect.height().coerceAtLeast(1f) * .25f)
                2 -> perimeter * (.5f + (rect.right - px) / rect.width().coerceAtLeast(1f) * .25f)
                else -> perimeter * (.75f + (rect.bottom - py) / rect.height().coerceAtLeast(1f) * .25f)
            }

            val routeProgress = progress.coerceAtMost(1f)
            val shader = android.graphics.SweepGradient(
                rect.centerX(), rect.centerY(), rainbow, null
            )
            val matrix = Matrix().apply {
                setRotate(routeProgress * 360f, rect.centerX(), rect.centerY())
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            paint.strokeWidth = 6f
            paint.alpha = alphaValue
            glowPaint.shader = shader
            glowPaint.strokeWidth = 17f
            glowPaint.alpha = (alphaValue * .30f).toInt().coerceIn(0, 255)

            val travelled = perimeter * routeProgress
            segment.reset()
            val end = startDistance + travelled
            if (end <= perimeter) {
                measure.getSegment(startDistance, end, segment, true)
            } else {
                measure.getSegment(startDistance, perimeter, segment, true)
                measure.getSegment(0f, end - perimeter, segment, true)
            }
            // Draw only on the keyboard's outer rounded border: soft glow + sharp RGB trail.
            canvas.drawPath(segment, glowPaint)
            canvas.drawPath(segment, paint)
            paint.shader = null
            glowPaint.shader = null
        }

        override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.inputType?.and(0x0000000f) == 2
        singlishBuffer.clear()
    }
}
