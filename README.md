# android-force-120hz

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

A workaround app to force 120Hz refresh rate on Android phones with aggressive LTPO displays.

---

## ⚠️ Try This First!

Before installing this app, **try the ADB commands** — they're battery-friendly and work on most devices:

```bash
# Set minimum refresh rate to max (forces 120Hz)
adb shell settings put system min_refresh_rate 120

# Alternative method
adb shell settings put system peak_refresh_rate 120
adb shell settings put secure min_refresh_rate 120.0
```

If ADB commands work for you — great! You don't need this app.

**This app is a last resort** for devices where:
- ADB commands don't work (looking at you, Vivo/OPPO/OnePlus with OriginOS/ColorOS)
- The display still drops to 60Hz on static content
- You've tried everything else and still get stutters

---

## How It Works

The app creates a tiny invisible overlay (1 pixel) that updates every frame. This tricks the LTPO controller into thinking content is always changing, so it keeps the display at max refresh rate.

**Battery impact**: Yes, there will be some. Running the display at 120Hz constantly uses more power than adaptive refresh. Expect ~10-15% less screen-on time. That's just physics — the display is working harder.

---

## Download

Grab the latest APK from [Releases](../../releases).

## Installation

1. Download APK from Releases
2. Install it (allow unknown sources if prompted)
3. Open the app
4. Tap "Open Accessibility Settings"
5. Find "Force 120Hz" → enable it
6. Return to app → toggle ON

The service will auto-start after reboot.

---

## Building from Source

```bash
# Windows
.\gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

---

## Requirements

- Android 8.0+ (API 26)
- High refresh rate display (120Hz/90Hz)

---

## Contributing

PRs and improvements are welcome! If you found a better way to achieve this, or want to optimize the code further — feel free to open an issue or submit a pull request.

---

## License

[CC BY-NC-SA 4.0](LICENSE) — Free to use and modify, but not for commercial purposes.
