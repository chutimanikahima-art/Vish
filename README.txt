HIMA Board v2.5

This is the complete Android Studio / GitHub Actions project.

Features in this update:
- Hima Board app branding and app icon.
- Animated full-screen splash.
- Full-screen setup gate: enable and select Hima Board before opening settings.
- Live animated activation status.
- Custom app background image and keyboard photo background.
- Clipboard popup in the keyboard with persistent pinned clips.
- Existing keyboard themes, sizing, Singlish and Sinhala/English modes.

Build:
1. Upload/replace the project files in your GitHub repository.
2. Open GitHub Actions and run the Android build workflow.
3. Download the VISH/Hima Board debug APK artifact.
4. Install the APK, then Enable and Select Hima Board in the first-run screen.

Important:
- Keep your existing .git folder when replacing project files.
- Do not delete the GitHub Actions workflow in your repository.
- Clipboard pins are stored only when you explicitly tap Pin latest. Android does not let apps silently read the full clipboard history.
- Build and test on a real Android device before daily use.
