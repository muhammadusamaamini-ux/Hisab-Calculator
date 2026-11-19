# Hisab Calculator

Personal offline engineering calculator for Android (WebView) and Windows (Electron).

- `web/index.html` is the whole calculator (Scientific, Units, Feet-inches and Site tabs). Both apps load it.
- `app/` is the Android shell, `pc/` is the Windows shell. `app/hisab.keystore` and the app ID `com.usama.hisab` must never change.
- GitHub Actions builds both on every push to `main` and publishes a release tagged `build-N`.
  **The commit message becomes the "What's new" text** shown in the in-app update notification, so write it for the user:
  a short title line, then `- ` bullet lines of user-facing changes.
- Every version must keep "Check for updates" (MODE → App) and the update notification with what's new.
- Do not send the user download links for updates; they update from inside the app.
- New site calculators: follow `docs/ADDING-CALCULATORS.md`.
- After changing `web/index.html`, also republish the browser artifact from it (swap `fonts/fonts.css` for the Google Fonts link — Roboto Condensed, DotGothic16, JetBrains Mono — and drop the html/head/body wrapper).
- Look: "Classic Casio" (silver body, grey-green LCD in a dark bezel, black function keys, light number keys); icon: blueprint set square.
