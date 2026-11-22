# HIMA Board v3.7

## v3.7 updates
- Live digital clock on the keyboard toolbar; tap it to view the current time, date, and Sri Lanka time zone.
- Tapping the HIMA logo now shows the animated message: “I love you / chuti manika / himasha❤️vishath”.
- Keeps the v3.6 RGB border effect and full RGB key/background color pickers.

## Build with GitHub Actions
Push this project to your existing GitHub repository and open **Actions** to download the APK artifact after the workflow completes.

## Update your existing Termux project
1. Extract this ZIP and copy the project files over your existing `~/Vish` folder. Keep the `.git` folder.
2. In Termux run:
```sh
cd ~/Vish
git add .
git commit -m "HIMA Board v3.7 clock and message animation"
git push
```
3. Open GitHub → **Actions** and check the APK build.
