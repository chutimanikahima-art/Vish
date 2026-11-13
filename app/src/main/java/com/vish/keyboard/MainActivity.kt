package com.vish.keyboard

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    private val bg = Color.rgb(9, 13, 24)
    private val blue = Color.rgb(45, 105, 220)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "VISH"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Dark • Sinhala + English Keyboard"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 30)
        })
        root.addView(actionButton("1. Enable VISH Keyboard") {
            startActivity(Intent("android.settings.INPUT_METHOD_SETTINGS"))
        })
        root.addView(actionButton("2. Select VISH Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(TextView(this).apply {
            text = "Open any app, tap a text field, then use the keyboard's 🌐 key to switch English / Sinhala. VISH does not collect or send what you type."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(4, 28, 4, 0)
        })
        setContentView(root)
    }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(blue)
        setOnClickListener { action() }
        val p = LinearLayout.LayoutParams(-1, 58)
        p.setMargins(0, 10, 0, 10)
        layoutParams = p
    }
}
