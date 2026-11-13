# VISH Keyboard Plus (v1.1)

Native Kotlin Android keyboard with English and direct Sinhala Unicode layouts, emoji quick bar, adjustable keyboard height, background color/image selection, VISH logo and developer credit.

## Build
Push this project root to GitHub branch `main`. GitHub Actions builds `app-debug.apk` and uploads it as `VISH-Keyboard-debug`.

## Termux update
Extract this ZIP into a new folder, or replace the old project files. Then:
```sh
cd /storage/emulated/0/Download/VISH-Keyboard-Plus
git init
git add .
git commit -m "VISH customization and emoji bar"
git branch -M main
git remote add origin https://github.com/chutimanikahima-art/Vish.git
git push -u origin main
```
If the remote already exists, use `git remote set-url origin https://github.com/chutimanikahima-art/Vish.git` instead of `git remote add`.
If Git says non-fast-forward because the previous project is already on GitHub, use:
```sh
git pull --rebase origin main
git push
```
Do not use force push.

## Enable
Install APK, enable VISH in Android Input Method settings, then select VISH. Use the app to change height, background color or image. Open a text field to see changes. The emoji quick bar is above the keys; 🌐 switches layouts.

## Notes
- Sinhala currently uses direct Unicode key entry, not phonetic transliteration.
- No network permission is declared; typed text is not transmitted by this starter.
- Image selection uses Android's document picker and persists the selected image permission.
