<img src="app/src/main/res/drawable-nodpi/app_icon_sunrise.png" width="88" alt="Sky Wallpaper" />

# Sky Wallpaper

English · [简体中文](README.zh-CN.md)

An Android live wallpaper that switches among four periods based on solar elevation, with support for custom images and continuous gradients.

- Android 14 and later; Material 3 Expressive, Monet dynamic color, and system light and dark themes.
- Use built-in or custom images independently for dawn, day, dusk, and night.
- Linear, radial, and sweep gradients transition continuously across actual solar periods.
- Direct Boot support lets the wallpaper display before the first unlock.
- 20 languages across 21 locale variants, with Arabic RTL and support for large text.
- View the project license, third-party components, and full license texts offline in the app.

## Download

[Download the latest release](https://github.com/RiversLee9312/SkyWallpaper/releases/latest) for Android 14 and later. The release assets include the APK and `SHA256SUMS`.

The official release APK uses a different signing key from local debug builds, so Android cannot install it directly over a debug build.

## Use

1. Open the app and allow location access, or enter fixed coordinates.
2. Under “Settings → Wallpapers,” choose Images or Gradient, then edit each period.
3. Tap “Set wallpaper” and choose the Home screen or Lock screen in the system preview.

To update your location automatically as you move, set location access to “Allow all the time.” The period selector on the home screen changes only the preview; “Keep…” in Settings turns off automatic switching. Manually entered coordinates stay fixed until you refresh the device location.

Images are imported through the system photo picker, without requiring access to your entire photo library. The app corrects image orientation, limits images to 4096 pixels on the longest edge and 8 megapixels total, and saves a local copy so it no longer depends on the original photo library file.

## Gradients

All four periods share one gradient type so transitions can be interpolated continuously. Each period supports 2–6 colors and stop positions. The photo-style editor keeps a screen-proportioned preview separate from its tools. Drag the start/end points or center on the image and drag color stops along the color strip. Tap a color for live hex/HSV adjustments, or use Geometry for precise coordinates and stop positions. Both tools occupy their own pane, leaving the preview visible. Radial gradients also have a radius slider. Coordinates start at the top-left; the radius is based on the shorter side of the screen.

Dawn → day → dusk uses smoothstep interpolation between the actual period midpoints. Night fades in as geometric solar elevation falls from 0° to −6°, and fades out as it rises from −6° to 0°. Above the horizon the night palette has no influence; below −6° the night preset is used in full. Colors, stops, and geometry all transition together, including when adjacent presets have different stop counts. Midnight and high-latitude twilight remain continuous; without a location or when automatic switching is off, a fixed period is used. These transitions use geometric 0°, distinct from the −0.833° threshold used for the displayed sunrise and sunset events.

Dawn spans solar elevation rising from −6° to +10°, and dusk spans it falling from +10° to −6°. The remaining time is shown as day or night based on solar elevation. Solar position is calculated on-device, and event times are shown in UTC / GMT regardless of the phone's time zone.

## Privacy and background operation

The app has no network permission and uses no accounts or analytics services. Location and wallpaper settings are stored only on the device. Custom images and location preferences are excluded from cloud backups and device migration. Whether the system's fused location uses a network depends on system location services. See the [privacy statement](PRIVACY.md).

In automatic mode, the app attempts to update device location every 6 hours using Android's native `LocationManager.FUSED_PROVIDER`; system scheduling may delay an attempt. Each request lasts up to 60 seconds. If it fails, the previous coordinates are kept; manual locations are not refreshed in the background. Google Play services are not required. Gradients refresh once per second only while the wallpaper is visible, and system colors update at most once per minute.

## Build from source

You need JDK 21 and Android SDK 36. Open the project in Android Studio and sync Gradle, or run:

```sh
./gradlew :app:assembleDebug
```

On Windows:

```powershell
# Set JAVA_HOME to your local JDK 21 or Android Studio's JBR.
.\gradlew.bat :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Material 3 Expressive is pinned to `1.5.0-alpha14`, and Navigation Compose to `2.9.8`. For official release signing, see [Release signing](#release-signing).

## Release signing

On Windows, use PowerShell 7 with JDK 21 and Android SDK build-tools 36.0.0. Initialize a signing key once for a new application:

```powershell
pwsh -File tools/build_release.ps1 -InitializeSigningKey
```

For later updates, reuse the existing key:

```powershell
pwsh -File tools/build_release.ps1
```

Never initialize a new key for an update; updates must use the original signing key. By default, the PKCS12 key and Windows DPAPI-encrypted password are stored outside the repository in `~/.android/skywallpaper-signing/`. DPAPI is tied to the current Windows user. Keep independent secure backups of both the key and password; the DPAPI file alone is not a portable password backup.

On other platforms, set all four Gradle signing environment variables: `SKYWALLPAPER_KEYSTORE_FILE`, `SKYWALLPAPER_STORE_PASSWORD`, `SKYWALLPAPER_KEY_ALIAS`, and `SKYWALLPAPER_KEY_PASSWORD`. Then run:

```sh
./gradlew :app:assembleRelease --no-configuration-cache
```

## Verification and maintenance

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python tools\check_i18n.py

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w com.example.skywallpaper.test/androidx.test.runner.AndroidJUnitRunner
& $adb uninstall com.example.skywallpaper.test
```

Device tests run instrumentation directly to avoid Gradle connected tests uninstalling the target app during cleanup. Tests cover solar periods, gradient interpolation, image import, Direct Boot, state restoration, predictive back, locale formatting, and the offline license page. The physical test device is a OnePlus 11 running LOS 23.2 / Android 16.

- `tools/check_i18n.py`: checks language coverage, string keys, placeholders, newlines, and formatting. Translations were adapted with GPT-6 Luna (max).
- `tools/update_licenses.py`: updates the offline licenses and third-party inventory based on resolved dependencies; the first dependency resolution requires network access. Builds verify the inventory matches the dependencies.
- `tools/device_ui.py`: reads, clicks, and captures screenshots through ADB; temporary output goes in the ignored `verification/` directory.
- `tools/import_material_symbols.py`: imports Material Symbols Rounded vector assets.

Report issues through [Issues](https://github.com/RiversLee9312/SkyWallpaper/issues). Include the Android version, device model, and reproduction steps; do not include actual coordinates.

## License and assets

The project's original code, documentation, and project-owned assets are under the [Apache License 2.0](LICENSE). Copyright and notices must be retained; use, modification, and distribution are permitted under the license terms. Third-party components retain their own copyright and license terms; see [NOTICE](NOTICE) and the [third-party notices](THIRD_PARTY_NOTICES.md).

The interface icons come from Google's [Material Symbols Rounded](https://github.com/google/material-design-icons), converted from SVG to Android VectorDrawable. The original license is at `artwork/material-symbols-LICENSE.txt`. The app icon was generated with GPT-image; its prompt is at `artwork/app-icon-sunrise-prompt.txt`.

Design references: [Material 3 Expressive](https://design.google/library/expressive-material-design-google-research), [Apple interface writing guidance](https://developer.apple.com/videos/play/wwdc2022/10037/).
