# Android Force 120Hz

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

Force 120Hz on Android devices with aggressive LTPO downscaling (when refresh drops to 60Hz on static content).

## Try ADB First (Recommended)

If these commands work on your device, you probably do not need this app:

```bash
adb shell settings put system min_refresh_rate 120
adb shell settings put system peak_refresh_rate 120
adb shell settings put secure min_refresh_rate 120.0
```

## Download

Get the latest APK from [Releases](../../releases).

## Quick Start

1. Install APK (allow unknown sources if prompted).
2. Open app.
3. Tap `Open Accessibility Settings`.
4. Enable `Force 120Hz` service.
5. Return to app and enable animation.

## Stable After Reboot (Important)

For reliable always-on behavior after reboot, set all of these:

1. Accessibility service: enabled.
2. Battery mode: unrestricted / no optimization.
3. OEM auto-start: enabled for this app.

The app includes a `Startup Stability` section with quick links to these settings.

## How It Works

The service keeps a tiny 1px accessibility overlay animating every frame. This keeps display content "active", so LTPO panels are less likely to drop refresh rate.

Battery impact is expected: forcing high refresh rate uses more power than adaptive mode.

## Build From Source

```bash
# Windows
.\gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

Release APK output:

`app/build/outputs/apk/release/app-release.apk`

## Release Signing

Release build requires these environment variables:

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Example (PowerShell):

```powershell
$env:KEYSTORE_PATH="C:\path\forcehz-release.jks"
$env:KEYSTORE_PASSWORD="your_store_password"
$env:KEY_ALIAS="forcehz"
$env:KEY_PASSWORD="your_key_password"
```

Verify APK signature:

```bash
apksigner verify --verbose --print-certs app-release.apk
```

## Installation Issues

If installation is blocked by device security / Play Protect:

1. Install a release-signed APK (not debug).
2. If app was installed with a different signature before, uninstall old version first.
3. Allow install permission for your browser/file manager.
4. Temporarily disable Play Protect scan, install APK, then enable it back.

## Requirements

- Android 8.0+ (API 26)
- 90Hz/120Hz display

## License

[CC BY-NC-SA 4.0](LICENSE)
