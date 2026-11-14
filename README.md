# VISH Keyboard v2.1

Native Kotlin Android keyboard with Sinhala and English layouts.

## v2.1 updates
- Refined rounded key styling, spacing and touch feedback.

## v2.0 updates
- Home and Settings navigation in the VISH app.
- Dark and Light theme choices shared by the app and keyboard.
- Keyboard height control (300–430 dp).
- Letter key size control (42–58 dp).
- Existing logo, emoji strip, number row, Sinhala/English switch, background color and image picker retained.
- Key sound and vibration toggles.
- Developer credit: Vishath Kaushika.

## Build with GitHub Actions
Push this project to the `main` branch of your GitHub repository. The workflow in `.github/workflows/build.yml` builds a debug APK and uploads it as `VISH-Keyboard-debug`.

## Update your existing Termux project
1. Extract this ZIP.
2. Copy the contents of the extracted `VISH-Keyboard` folder over your existing `/storage/emulated/0/Download/VISH-Keyboard` folder. Allow file replacement. Do not delete your existing `.git` folder.
3. In Termux:
```sh
cd /storage/emulated/0/Download/VISH-Keyboard
git add .
git commit -m "VISH Keyboard v2.1 themes and settings"
git push
```
If Git says `nothing to commit`, check that you copied the extracted files into the correct folder and that `app/build.gradle.kts` contains `versionName = "2.0"`.

If your remote is missing:
```sh
git remote add origin https://github.com/chutimanikahima-art/Vish.git
git push -u origin main
```

## Enable the keyboard
- Open VISH and tap Enable VISH Keyboard.
- Enable VISH in Android's input method settings.
- Return to VISH and tap Select VISH Keyboard.
- Choose VISH from the keyboard picker.
- Open Settings in the VISH app and choose Dark or Light. The keyboard uses the same saved theme.


## Version 2.3 keyboard update
- Larger, evenly spaced QWERTY keys.
- Shift on the left and Backspace on the right of the letter row.
- Three input modes: English, Singlish-to-Sinhala (basic phonetic conversion), and Sinhala layout.
- Default keyboard height and letter key size increased.


VISH Keyboard v2.3 UI update: larger default key sizing (56dp), 420dp default keyboard height, rounded keys, and accent-color ripple feedback on key taps.
