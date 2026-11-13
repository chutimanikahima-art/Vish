VISH Keyboard v1.2 layout patch

1. Extract this ZIP.
2. Copy the app folder into your existing VISH-Keyboard project folder and choose overwrite.
3. In app/build.gradle.kts change versionCode to 3 and versionName to "1.2".
4. In MainActivity.kt change the height setting default to 370 and range to 300–430:
   prefs.getInt("height", 370)
   max = 130; progress = (height - 300).coerceIn(0, 130)
   save value + 300
5. Commit and push:
   git add .
   git commit -m "VISH v1.2 larger keys and emoji row"
   git push

This patch adds a compact VISH toolbar, emoji strip, number row, larger 49dp letter keys, staggered QWERTY rows and a wider bottom space key. Select VISH again after installing the new APK.
