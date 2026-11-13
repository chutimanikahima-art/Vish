package com.vish.keyboard

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("vish_settings", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(9, 13, 24)
    private val blue = Color.rgb(40, 112, 230)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(bg)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.vish_logo)
            layoutParams = LinearLayout.LayoutParams(92, 92).apply { bottomMargin = 10 }
        }
        root.addView(logo)
        root.addView(TextView(this).apply {
            text = "VISH Keyboard"
            textSize = 27f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Sinhala + English  •  Dark Edition"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 20)
        })
        root.addView(actionButton("1. Enable VISH Keyboard") {
            startActivity(Intent("android.settings.INPUT_METHOD_SETTINGS"))
        })
        root.addView(actionButton("2. Select VISH Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
        })
        root.addView(section("Keyboard customization"))
        root.addView(TextView(this).apply {
            text = "Keyboard height / size"
            setTextColor(white); textSize = 14f
        })
        val seek = SeekBar(this).apply {
            max = 140
            progress = (prefs.getInt("height", 300) - 220).coerceIn(0, 140)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, user: Boolean) {
                    if (user) prefs.edit().putInt("height", value + 220).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(seek)
        root.addView(TextView(this).apply {
            text = "Height: ${prefs.getInt("height", 300)} dp (220–360)"
            setTextColor(Color.LTGRAY); textSize = 12f
        })
        root.addView(TextView(this).apply {
            text = "Keyboard background color"
            setTextColor(white); textSize = 14f; setPadding(0, 14, 0, 5)
        })
        val colors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("#0C121F", "#151B2E", "#17243A", "#202020", "#241638").forEach { hex ->
            val c = Color.parseColor(hex)
            val b = Button(this).apply {
                text = "●"; textSize = 19f; setTextColor(c); setBackgroundColor(Color.DKGRAY)
                setOnClickListener { prefs.edit().putInt("color", c).remove("image").apply(); Toast.makeText(this@MainActivity, "Background color saved", Toast.LENGTH_SHORT).show() }
            }
            colors.addView(b, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 0, 3, 0) })
        }
        root.addView(colors)
        root.addView(actionButton("Choose keyboard background image") { chooseImage() })
        root.addView(actionButton("Remove background image") {
            prefs.edit().remove("image").apply()
            Toast.makeText(this, "Background image removed", Toast.LENGTH_SHORT).show()
        })
        root.addView(TextView(this).apply {
            text = "Emoji bar is shown above the keys. Tap 🌐 on the keyboard to switch English / Sinhala."
            setTextColor(Color.LTGRAY); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, 14, 0, 12)
        })
        root.addView(TextView(this).apply {
            text = "Developed by Vishath Kaushika"
            setTextColor(Color.rgb(130, 170, 230)); textSize = 12f; gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
        })
        setContentView(root)
    }

    private fun section(label: String) = TextView(this).apply {
        text = label; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(100, 165, 255)); setPadding(0, 22, 0, 10)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setTextColor(white); setBackgroundColor(blue); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, 52).apply { setMargins(0, 5, 0, 5) }
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
        if (requestCode == 501 && resultCode == RESULT_OK) {
            data?.data?.let { uri: Uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                prefs.edit().putString("image", uri.toString()).apply()
                Toast.makeText(this, "Keyboard image saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
