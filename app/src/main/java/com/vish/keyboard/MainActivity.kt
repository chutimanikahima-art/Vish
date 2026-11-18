package com.vish.keyboard

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.app.AlertDialog
import android.widget.EditText
import android.util.Base64
import org.json.JSONObject
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }
    private var currentPage = "home"
    private var splashShowing = true

    private data class Palette(
        val background: Int,
        val surface: Int,
        val raised: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val border: Int
    )

    private fun palette(): Palette {
        return if (prefs.getString("theme", "dark") == "light") {
            Palette(
                Color.rgb(243, 246, 251),
                Color.WHITE,
                Color.rgb(231, 237, 246),
                Color.rgb(22, 32, 50),
                Color.rgb(94, 108, 130),
                Color.rgb(38, 108, 220),
                Color.rgb(213, 222, 235)
            )
        } else {
            Palette(
                Color.rgb(9, 13, 24),
                Color.rgb(17, 27, 42),
                Color.rgb(24, 38, 57),
                Color.rgb(245, 248, 255),
                Color.rgb(158, 171, 192),
                Color.rgb(50, 125, 230),
                Color.rgb(42, 58, 79)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPage = savedInstanceState?.getString("page") ?: "home"
        showSplash()
    }

    private fun showSplash() {
        applyImmersiveMode()
        val bg = Color.rgb(17, 14, 29)
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackground(GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(236, 93, 183), Color.rgb(132, 82, 245), Color.rgb(75, 139, 245))
            ))
        }
        val mark = TextView(this).apply {
            text = "⌨"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val name = TextView(this).apply {
            text = "HIMA Board"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val tagline = TextView(this).apply {
            text = "Type cute. Type fast. Type YOU."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.88f
        }
        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        splash.addView(mark, LinearLayout.LayoutParams(-1, dp(76)))
        splash.addView(name)
        splash.addView(tagline, LinearLayout.LayoutParams(-1, dp(34)))
        splash.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            topMargin = dp(30)
        })
        setContentView(splash)
        mark.alpha = 0f
        mark.scaleX = .65f
        mark.scaleY = .65f
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start()
        name.alpha = 0f
        name.animate().alpha(1f).setStartDelay(180).setDuration(650).start()
        Handler(Looper.getMainLooper()).postDelayed({
            splashShowing = false
            render()
        }, 1250)
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (!splashShowing) {
            if (prefs.getBoolean("open_settings_after_enable", false) && isVishEnabled()) {
                prefs.edit().remove("open_settings_after_enable").apply()
                currentPage = "settings"
            }
            render()
        }
    }

    private fun openKeyboardEnableSettings() {
        prefs.edit().putBoolean("open_settings_after_enable", true).apply()
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applySystemBars(p: Palette) {
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        applyImmersiveMode()
    }

    private fun render() {
        val p = palette()
        applySystemBars(p)
        val setupComplete = isVishEnabled()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
            prefs.getString("appBackground", null)?.let { raw ->
                try {
                    contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { background = BitmapDrawable(resources, it) }
                    }
                } catch (_: Exception) { }
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        if (!setupComplete) {
            currentPage = "setup"
            buildSetup(content, p)
        } else if (currentPage == "settings") {
            buildSettings(content, p)
        } else {
            currentPage = "home"
            buildHome(content, p)
        }

        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (setupComplete) page.addView(buildBottomNav(p))
        setContentView(page)
    }

    private fun buildSetup(parent: LinearLayout, p: Palette) {
        val enabled = isVishEnabled()
        val selected = isVishSelected()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(239, 91, 184), Color.rgb(143, 84, 245), Color.rgb(76, 143, 247))
            ).apply { cornerRadius = dp(26).toFloat() }
        }
        hero.addView(text("⌨  Hima Board ♡", 27f, Color.WHITE, true))
        hero.addView(text("Type cute. Type fast. Type YOU.", 14f, Color.WHITE).apply {
            setPadding(0, dp(7), 0, dp(17))
        })
        hero.addView(text("A beautiful keyboard made for your style.", 13f, Color.WHITE))
        parent.addView(hero)
        parent.addView(space(20))

        val statusCard = card(p, p.surface)
        statusCard.addView(text("GET STARTED", 13f, p.accent, true))
        statusCard.addView(space(8))
        val status = TextView(this).apply {
            text = if (enabled && selected) "●  All set! Hima Board is ready." else
                if (enabled) "●  Enabled — select Hima Board to continue" else "●  Hima Board is not enabled yet"
            textSize = 15f
            setTextColor(p.text)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusCard.addView(status)
        status.alpha = .65f
        ObjectAnimator.ofFloat(status, View.ALPHA, .55f, 1f).apply {
            duration = 850
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        parent.addView(statusCard)

        parent.addView(section("Keyboard activation", p))
        parent.addView(infoRow(p, "1. Enable Hima Board", if (enabled) "Completed" else "Allow Hima Board in Android keyboard settings", if (enabled) "✓" else "1"))
        parent.addView(actionButton(if (enabled) "Enabled ✓" else "1   Enable Hima Board", p) {
            openKeyboardEnableSettings()
        })
        parent.addView(space(6))
        parent.addView(infoRow(p, "2. Select Hima Board", if (selected) "Completed" else "Choose Hima Board from the keyboard picker", if (selected) "✓" else "2"))
        parent.addView(actionButton(if (selected) "Selected ✓" else "2   Select Hima Board", p) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })
        parent.addView(space(14))
        parent.addView(text(
            "Enable Hima Board to unlock Settings automatically. You can select it separately when you are ready to type.",
            13f, p.muted
        ))
        parent.addView(space(20))
        parent.addView(text("Hima Board  •  by Vishath Kaushika", 12f, p.muted).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun buildHome(parent: LinearLayout, p: Palette) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.hima_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(58), dp(58)))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        heading.addView(text("Hima Board", 22f, p.text, true))
        heading.addView(text("Sinhala + English", 13f, p.muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(header)

        parent.addView(space(20))
        val hero = card(p, p.surface).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(236, 91, 182), Color.rgb(143, 83, 244), Color.rgb(76, 139, 246))
            ).apply { cornerRadius = dp(24).toFloat() }
            addView(text("Your keyboard. Your style.", 21f, Color.WHITE, true))
            addView(text(
                "Personalize your typing experience with Hima Board.",
                14f, Color.WHITE
            ).apply { setPadding(0, dp(7), 0, dp(14)) })
            addView(keyboardPreview(p))
        }
        parent.addView(hero)
        val styleNow = (prefs.getString("keyboardStyle", "normal") ?: "normal").replaceFirstChar { it.uppercase() }
        parent.addView(card(p, p.raised).apply {
            addView(text("Current keyboard style", 13f, p.muted))
            addView(text("$styleNow  •  Customize in Settings", 17f, p.text, true))
            setOnClickListener { currentPage = "settings"; render() }
        })

        parent.addView(section("Keyboard setup", p))
        val enabled = isVishEnabled()
        val selected = isVishSelected()
        parent.addView(infoRow(
            p,
            "1. Enable Hima Board",
            if (enabled) "Enabled in Android settings" else "Allow Hima Board in keyboard settings",
            if (enabled) "✓" else "1"
        ))
        parent.addView(actionButton(
            if (enabled) "Manage enabled keyboards" else "Enable Hima Board",
            p
        ) {
            openKeyboardEnableSettings()
        })

        parent.addView(space(7))
        parent.addView(infoRow(
            p,
            "2. Select Hima Board",
            if (selected) "Hima Board is currently selected" else "Choose Hima Board when you are ready to type",
            if (selected) "✓" else "2"
        ))
        parent.addView(actionButton("Select Hima Board", p) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })

        parent.addView(section("Quick settings", p))
        val themeName = if (prefs.getString("theme", "dark") == "light") "Light" else "Dark"
        val quick = card(p, p.raised).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Appearance", 16f, p.text, true))
                addView(text("$themeName theme is active", 13f, p.muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(smallButton("Settings", p) { currentPage = "settings"; render() })
        }
        parent.addView(quick)

        parent.addView(space(18))
        parent.addView(text("Developed by Vishath Kaushika", 12f, p.muted).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun buildSettings(parent: LinearLayout, p: Palette) {
        parent.addView(text("Settings", 27f, p.text, true))
        parent.addView(text(
            "Customize the app and keyboard together.",
            14f, p.muted
        ).apply { setPadding(0, dp(5), 0, dp(18)) })

        parent.addView(section("Keyboard style", p))
        parent.addView(text("Switch the keyboard between Cute, Normal and Developer layouts.", 13f, p.muted))
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val activeStyle = prefs.getString("keyboardStyle", "normal") ?: "normal"
        listOf("cute" to "Cute ♡", "normal" to "Normal", "developer" to "Developer") .forEach { (id, label) ->
            styleRow.addView(themeChoice(label, activeStyle == id, p) {
                prefs.edit().putString("keyboardStyle", id).remove("color").apply()
                toast("$label keyboard style applied")
                currentPage = "settings"
                render()
            }, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                if (id != "cute") leftMargin = dp(4)
                if (id != "developer") rightMargin = dp(4)
            })
        }
        parent.addView(styleRow)
        parent.addView(text("Developer style adds a code row, navigation keys and quick symbols. Cute uses softer colors and rounded keys.", 12f, p.muted))

        parent.addView(section("Appearance", p))
        parent.addView(text("Choose a theme", 15f, p.text, true))
        val themes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9), 0, dp(4))
        }
        val isLight = prefs.getString("theme", "dark") == "light"
        themes.addView(themeChoice("🌙  Dark", !isLight, p) {
            prefs.edit().putString("theme", "dark").remove("color").apply()
            currentPage = "settings"
            render()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { rightMargin = dp(6) })
        themes.addView(themeChoice("☀  Light", isLight, p) {
            prefs.edit().putString("theme", "light").remove("color").apply()
            currentPage = "settings"
            render()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(6) })
        parent.addView(themes)
        parent.addView(text(
            "The selected theme is shared by the Hima Board app and keyboard.",
            12f, p.muted
        ).apply { setPadding(0, dp(5), 0, 0) })

        parent.addView(section("App background", p))
        parent.addView(text("Personalize the Hima Board app with your own photo.", 13f, p.muted))
        parent.addView(actionButton("Choose app background image", p) { chooseAppBackground() })
        parent.addView(actionButton("Reset app background", p) {
            prefs.edit().remove("appBackground").apply()
            toast("App background reset")
            render()
        })

        parent.addView(section("Keyboard setup", p))
        parent.addView(actionButton("Enable Hima Board", p) {
            openKeyboardEnableSettings()
        })
        parent.addView(actionButton("Select Hima Board", p) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })

        parent.addView(section("Keyboard size", p))
        val currentHeight = prefs.getInt("height", 410).coerceIn(340, 460)
        parent.addView(sliderSetting(
            "Keyboard height",
            currentHeight,
            340,
            460,
            "dp",
            p,
            "height"
        ))
        val currentKeySize = prefs.getInt("keySize", 56).coerceIn(48, 64)
        parent.addView(sliderSetting(
            "Letter key size",
            currentKeySize,
            48,
            64,
            "dp",
            p,
            "keySize"
        ))

        parent.addView(section("Key appearance", p))
        parent.addView(sliderSetting(
            "Key opacity",
            prefs.getInt("keyOpacity", 94).coerceIn(35, 100),
            35, 100, "%", p, "keyOpacity"
        ))
        parent.addView(sliderSetting(
            "Key roundness",
            prefs.getInt("keyRoundness", 12).coerceIn(4, 22),
            4, 22, "dp", p, "keyRoundness"
        ))
        parent.addView(toggleRow(
            "Touch light / ripple effect",
            prefs.getBoolean("touchEffect", true),
            p, "touchEffect"
        ))
        parent.addView(actionButton(
            if (prefs.getString("fontStyle", "rounded") == "mono") "Use rounded key font" else "Use monospace key font",
            p
        ) {
            val next = if (prefs.getString("fontStyle", "rounded") == "mono") "rounded" else "mono"
            prefs.edit().putString("fontStyle", next).apply()
            toast("Keyboard font updated")
            currentPage = "settings"
            render()
        })

        parent.addView(section("Theme gallery", p))
        parent.addView(text("Choose a ready-made keyboard color style.", 13f, p.muted))
        val gallery = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "Ocean blue" to "#DCE8F7",
            "Mint pastel" to "#DDEFEA",
            "Lavender" to "#EDE3F5",
            "Warm cream" to "#F4E8D8",
            "Midnight" to "#151B2E"
        ).forEach { (name, hex) ->
            gallery.addView(actionButton(name, p) {
                prefs.edit().putInt("color", Color.parseColor(hex)).remove("image").apply()
                toast("$name theme applied")
            })
        }
        parent.addView(gallery)

        parent.addView(section("Keyboard background", p))
        parent.addView(text("Background color", 14f, p.text, true))
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val choices = if (prefs.getString("theme", "dark") == "light") {
            listOf("#E7EDF6", "#DCE8F7", "#DDEFEA", "#F4E8D8", "#EDE3F5")
        } else {
            listOf("#0C141B", "#151B2E", "#17243A", "#202020", "#241638")
        }
        choices.forEach { hex ->
            val c = Color.parseColor(hex)
            val swatch = TextView(this).apply {
                text = "●"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(c)
                background = rounded(p.raised, p.border)
                setOnClickListener {
                    prefs.edit().putInt("color", c).remove("image").apply()
                    toast("Keyboard background color saved")
                }
            }
            swatches.addView(swatch, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(3), dp(8), dp(3), dp(8))
            })
        }
        parent.addView(swatches)
        parent.addView(actionButton("Choose background image", p) { chooseImage() })
        parent.addView(actionButton("Remove background image", p) {
            prefs.edit().remove("image").remove("color").apply()
            toast("Keyboard background reset")
        })

        parent.addView(section("Typing feedback", p))
        parent.addView(toggleRow(
            "Key sound",
            prefs.getBoolean("sound", false),
            p,
            "sound"
        ))
        parent.addView(toggleRow(
            "Key vibration",
            prefs.getBoolean("vibration", true),
            p,
            "vibration"
        ))
        parent.addView(toggleRow(
            "Touch light effect",
            prefs.getBoolean("touchEffect", true),
            p,
            "touchEffect"
        ))

        parent.addView(section("Pro typing tools", p))
        parent.addView(toggleRow("Emoji suggestion row", prefs.getBoolean("emojiRow", true), p, "emojiRow"))
        parent.addView(toggleRow("Number row", prefs.getBoolean("numberRow", true), p, "numberRow"))
        parent.addView(toggleRow("Developer navigation row", prefs.getBoolean("devNavRow", true), p, "devNavRow"))
        parent.addView(actionButton("Clipboard & pinned text help", p) {
            AlertDialog.Builder(this)
                .setTitle("Hima Clipboard")
                .setMessage("Open the keyboard and tap the clipboard icon. You can paste the latest copied text and pin important text for later. Pinned clips stay on this device.")
                .setPositiveButton("OK", null).show()
        })
        parent.addView(section("Backup & restore", p))
        parent.addView(text("Save your Hima Board preferences as a code, or restore them later.", 13f, p.muted))
        parent.addView(actionButton("Copy settings backup", p) { copySettingsBackup() })
        parent.addView(actionButton("Restore from clipboard", p) { restoreSettingsBackup() })

        parent.addView(space(16))
        parent.addView(text("Hima Board  •  Version 3.2", 13f, p.muted).apply {
            gravity = Gravity.CENTER
        })
        parent.addView(text("Developed by Vishath Kaushika", 12f, p.muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(8))
        })
    }

    private fun copySettingsBackup() {
        try {
            val json = JSONObject()
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is String, is Boolean, is Int, is Long, is Float, is Double -> json.put(key, value)
                    else -> Unit
                }
            }
            val encoded = "HIMA3:" + Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Hima Board backup", encoded))
            toast("Backup copied to clipboard")
        } catch (_: Exception) { toast("Could not create backup") }
    }

    private fun restoreSettingsBackup() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (!raw.startsWith("HIMA3:")) { toast("No Hima Board backup found in clipboard"); return }
            val json = JSONObject(String(Base64.decode(raw.removePrefix("HIMA3:"), Base64.DEFAULT), Charsets.UTF_8))
            val edit = prefs.edit()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = json.get(key)) {
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Double -> edit.putFloat(key, value.toFloat())
                    is String -> edit.putString(key, value)
                }
            }
            edit.apply()
            toast("Hima Board settings restored")
            currentPage = "settings"
            render()
        } catch (_: Exception) { toast("Backup code is invalid") }
    }

    private fun buildBottomNav(p: Palette): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(p.surface, p.border)
        }
        bar.addView(navButton("⌂", "Home", currentPage == "home", p) {
            currentPage = "home"; render()
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        bar.addView(navButton("⚙", "Settings", currentPage == "settings", p) {
            currentPage = "settings"; render()
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        return bar
    }

    private fun navButton(
        icon: String,
        label: String,
        selected: Boolean,
        p: Palette,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = if (selected) rounded(p.raised, p.border) else null
        addView(text(icon, 19f, if (selected) p.accent else p.muted, true).apply {
            gravity = Gravity.CENTER
        })
        addView(text(label, 12f, if (selected) p.accent else p.muted, selected).apply {
            gravity = Gravity.CENTER
        })
        setOnClickListener { action() }
    }

    private fun themeChoice(label: String, selected: Boolean, p: Palette, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(
                if (selected) p.raised else p.surface,
                if (selected) p.accent else p.border
            )
            addView(text(label, 16f, if (selected) p.accent else p.text, true))
            addView(text(if (selected) "Selected" else "Tap to use", 11f, p.muted))
            setOnClickListener { action() }
        }

    private fun sliderSetting(
        title: String,
        value: Int,
        minimum: Int,
        maximum: Int,
        unit: String,
        p: Palette,
        preferenceKey: String
    ): View {
        val box = card(p, p.surface).apply { orientation = LinearLayout.VERTICAL }
        val label = text("$title: $value $unit", 14f, p.text, true)
        box.addView(label)
        val seek = SeekBar(this).apply {
            max = maximum - minimum
            progress = value - minimum
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) label.text = "$title: ${progress + minimum} $unit"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val selected = (seekBar?.progress ?: 0) + minimum
                    prefs.edit().putInt(preferenceKey, selected).apply()
                    toast("$title saved")
                }
            })
        }
        box.addView(seek)
        return box
    }

    private fun toggleRow(
        label: String,
        enabled: Boolean,
        p: Palette,
        key: String
    ): View = card(p, p.surface).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(label, 15f, p.text, true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(smallButton(if (enabled) "ON" else "OFF", p) {
            val next = !prefs.getBoolean(key, key == "vibration")
            prefs.edit().putBoolean(key, next).apply()
            currentPage = "settings"
            render()
        })
    }

    private fun keyboardPreview(p: Palette): View {
        val style = prefs.getString("keyboardStyle", "normal") ?: "normal"
        val previewSurface = when (style) {
            "cute" -> Color.rgb(255, 237, 247)
            "developer" -> Color.rgb(12, 15, 22)
            else -> p.raised
        }
        val previewKey = when (style) {
            "cute" -> Color.rgb(255, 250, 253)
            "developer" -> Color.rgb(31, 39, 52)
            else -> p.surface
        }
        val previewText = if (style == "cute") Color.rgb(87, 48, 78) else p.text
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(previewSurface, p.border)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        val header = when (style) {
            "cute" -> "HIMA  ♡     🌸  🩷  ✨  🎀"
            "developer" -> "HIMA / DEV     ▤   ✦   SCI"
            else -> "HIMA     ❤️   🙂   🔥   🥰   👍"
        }
        box.addView(text(header, 13f, previewText, true).apply { gravity = Gravity.CENTER })
        box.addView(space(5))
        val rows = if (style == "developer") listOf("Tab  Esc  Ctrl  Alt  Home  End", "(  )  [  ]  {  }  <  >  =  ;", "q  w  e  r  t  y  u  i  o  p")
            else listOf("q  w  e  r  t  y  u  i  o  p", "a  s  d  f  g  h  j  k  l", "⇧   z  x  c  v  b  n  m   ⌫")
        rows.forEach {
            box.addView(text(it, if (style == "developer") 12f else 14f, previewText, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                background = rounded(previewKey, p.border)
            })
            box.addView(space(3))
        }
        return box
    }

    private fun infoRow(p: Palette, title: String, detail: String, badge: String): View =
        card(p, p.surface).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(badge, 18f, p.accent, true).apply {
                gravity = Gravity.CENTER
                background = rounded(p.raised, p.border)
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            val column = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(text(title, 14f, p.text, true))
                addView(text(detail, 12f, p.muted))
            }
            addView(column)
        }

    private fun section(label: String, p: Palette) = TextView(this).apply {
        text = label
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(p.accent)
        setPadding(0, dp(23), 0, dp(10))
    }

    private fun card(p: Palette, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(color, p.border)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(9)
        layoutParams = lp
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun actionButton(label: String, p: Palette, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(p.accent, p.accent)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(5)
                bottomMargin = dp(5)
            }
        }

    private fun smallButton(label: String, p: Palette, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(p.text)
            background = rounded(p.raised, p.border)
            setOnClickListener { action() }
        }

    private fun rounded(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun isVishEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.enabledInputMethodList.any { it.id.contains("VishKeyboardService") }
    }

    private fun isVishSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        return current.contains("VishKeyboardService")
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun chooseAppBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 502)
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 501)
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 502 && resultCode == RESULT_OK) {
            data?.data?.let { uri: Uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                prefs.edit().putString("appBackground", uri.toString()).apply()
                toast("App background saved")
                render()
            }
        }
        if (requestCode == 501 && resultCode == RESULT_OK) {
            data?.data?.let { uri: Uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                prefs.edit().putString("image", uri.toString()).apply()
                toast("Keyboard image saved")
            }
        }
    }
}
