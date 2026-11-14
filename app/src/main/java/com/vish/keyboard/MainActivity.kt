package com.vish.keyboard

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }
    private var currentPage = "home"

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
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applySystemBars(p: Palette) {
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (prefs.getString("theme", "dark") == "light") {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
    }

    private fun render() {
        val p = palette()
        applySystemBars(p)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }

        if (currentPage == "settings") {
            buildSettings(content, p)
        } else {
            buildHome(content, p)
        }

        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(buildBottomNav(p))
        setContentView(page)
    }

    private fun buildHome(parent: LinearLayout, p: Palette) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.vish_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(58), dp(58)))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        heading.addView(text("VISH Keyboard", 22f, p.text, true))
        heading.addView(text("Sinhala + English", 13f, p.muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(header)

        parent.addView(space(20))
        val hero = card(p, p.surface).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Your keyboard. Your style.", 21f, p.text, true))
            addView(text(
                "Personalize your typing experience with VISH.",
                14f, p.muted
            ).apply { setPadding(0, dp(7), 0, dp(14)) })
            addView(keyboardPreview(p))
        }
        parent.addView(hero)

        parent.addView(section("Keyboard setup", p))
        val enabled = isVishEnabled()
        val selected = isVishSelected()
        parent.addView(infoRow(
            p,
            "1. Enable VISH Keyboard",
            if (enabled) "Enabled in Android settings" else "Allow VISH in keyboard settings",
            if (enabled) "✓" else "1"
        ))
        parent.addView(actionButton(
            if (enabled) "Manage enabled keyboards" else "Enable VISH Keyboard",
            p
        ) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        parent.addView(space(7))
        parent.addView(infoRow(
            p,
            "2. Select VISH Keyboard",
            if (selected) "VISH is currently selected" else "Choose VISH when you are ready to type",
            if (selected) "✓" else "2"
        ))
        parent.addView(actionButton("Select VISH Keyboard", p) {
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
            "The selected theme is shared by the VISH app and keyboard.",
            12f, p.muted
        ).apply { setPadding(0, dp(5), 0, 0) })

        parent.addView(section("Keyboard setup", p))
        parent.addView(actionButton("Enable VISH Keyboard", p) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        parent.addView(actionButton("Select VISH Keyboard", p) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })

        parent.addView(section("Keyboard size", p))
        val currentHeight = prefs.getInt("height", 390).coerceIn(320, 440)
        parent.addView(sliderSetting(
            "Keyboard height",
            currentHeight,
            320,
            440,
            "dp",
            p,
            "height"
        ))
        val currentKeySize = prefs.getInt("keySize", 52).coerceIn(46, 60)
        parent.addView(sliderSetting(
            "Letter key size",
            currentKeySize,
            46,
            60,
            "dp",
            p,
            "keySize"
        ))

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

        parent.addView(space(16))
        parent.addView(text("VISH Keyboard  •  Version 2.2", 13f, p.muted).apply {
            gravity = Gravity.CENTER
        })
        parent.addView(text("Developed by Vishath Kaushika", 12f, p.muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(8))
        })
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(p.raised, p.border)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        box.addView(text("VISH     ❤️   🙂   🔥   🥰   👍", 13f, p.text, true).apply {
            gravity = Gravity.CENTER
        })
        box.addView(space(5))
        listOf("q  w  e  r  t  y  u  i  o  p", "a  s  d  f  g  h  j  k  l", "⇧   z  x  c  v  b  n  m   ⌫").forEach {
            box.addView(text(it, 14f, p.text, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(5))
                background = rounded(p.surface, p.border)
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
