# VISH Keyboard

A starter native Android Input Method Editor (IME) with a dark UI and English/Sinhala key layouts.

## Build with GitHub Actions
1. Create a GitHub repository named `VISH`.
2. Upload/push the contents of this folder to the repository root.
3. Open **Actions** → **Build VISH APK** → **Run workflow** (or push to `main`).
4. When complete, open the run → **Artifacts** → download `VISH-Keyboard-debug`.
5. Extract the ZIP and install `app-debug.apk` on your Android phone.

## Termux upload workflow
Install Termux from F-Droid or the official GitHub release, then:
```sh
pkg update -y
pkg install git -y
git clone https://github.com/YOUR-USERNAME/VISH.git
cd VISH
```
If you downloaded this project ZIP to your phone, extract it first and create a GitHub repo, then:
```sh
git init
git add .
git commit -m "Initial VISH keyboard app"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/VISH.git
git push -u origin main
```
GitHub will run the workflow automatically after the push. You can also run it manually from Actions.

## Enable on Android
1. Install the APK.
2. Open VISH.
3. Tap **Enable VISH Keyboard** and enable it in Android Settings.
4. Return to VISH and tap **Select VISH Keyboard**.
5. Select VISH. Open any text field to use it. Tap 🌐 to switch English/Sinhala.

## Notes
- This is a starter keyboard: Sinhala layout uses direct Sinhala Unicode keys (not phonetic transliteration).
- It currently does not provide predictive text, autocorrect, clipboard history, or a full emoji panel.
- A keyboard can process everything typed into it. This starter has no internet permission and does not transmit keystrokes.
- Debug APK is for testing. A release APK should be signed before public distribution.
