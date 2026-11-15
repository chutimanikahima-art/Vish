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


## VISH Keyboard 2.4 – Yumi-inspired feature update

This update adds a Yumi-inspired customization experience while keeping VISH branding:
- Clipboard paste shortcut on the keyboard toolbar.
- Scientific input panel (SCI) with common math function keys.
- Touch ripple feedback, adjustable key opacity and roundness.
- Key font choice (rounded/default or monospace).
- Theme gallery presets and existing photo background picker.
- Larger default keyboard and key sizes.
- Existing English, Singlish and Sinhala modes remain.

Build with GitHub Actions using the existing workflow. The APK must be tested on-device; scientific keys insert expressions into the active text field and do not evaluate expressions inside the keyboard.


## Hima Board 2.5

- App and keyboard branding changed to Hima Board.
- Animated full-screen splash screen.
- Immersive full-screen app layout.
- First-run activation gate: app settings remain locked until Hima Board is enabled and selected as the active keyboard.
- Live animated activation status.
- Keyboard clipboard popup with current copied text and persistent pinned clips (up to 20).
- Tap a recent or pinned clip to paste it into the active text field.

Clipboard note: Android only exposes the current clipboard item to the keyboard when the user opens the clipboard panel. This version stores clips that the user explicitly pins; it does not silently collect clipboard history.


## First launch

Hima Board opens with a short animated splash. The full-screen setup screen asks the user to enable the keyboard and then select it. App settings and customization pages remain locked until both steps are complete.

## App background

The Settings page now includes a separate app background image picker and reset control. Keyboard photo background remains a separate option.

## Build status

Source package only. A successful Gradle build and on-device test have not yet been confirmed for this version.
