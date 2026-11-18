# Hisab Calculator

A personal engineering calculator for Android and Windows. It works fully offline.

- **Scientific:** a Casio-style keypad with SHIFT and ALPHA, fractions, recurring decimals, CALC, SOLVE, EQN (equations and inequalities), MATRIX, VECTOR, STAT, DISTR, CMPLX, a FUNC table, constants and history.
- **Units:** 14 kinds of units, including marla and kanal, MPa, psi, kN and unit weight.
- **Feet-inches:** a tape calculator with fraction keys (1/8 and 1/16) and Revit-style typing (65 2 3/8).

## Get the app
Open the **Releases** page of this repository:
- **Phone:** download `HisabCalculator.apk` and open it to install.
- **Windows:** download `HisabCalculator-Setup.exe` and run it.

## Updates
In the app: **MODE → App → Check for updates**. When a new version is ready, the app also shows a notification that says what's new.
Updates install over the old version, so your history, memory and settings are kept. Do not uninstall first.

## How it is built
`web/index.html` is the whole calculator. The phone app shows it in a WebView and the Windows app shows it with Electron.
GitHub Actions builds both on every push to `main` and publishes them to Releases. The text of the commit becomes "What's new".
The app ID `com.usama.hisab` and the signing key `app/hisab.keystore` never change, so updates keep your data.
