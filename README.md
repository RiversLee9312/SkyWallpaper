<img src="app/src/main/res/drawable-nodpi/app_icon_sunrise.png" width="88" alt="Sky Wallpaper" />

# Sky Wallpaper

English · [简体中文](README.zh-CN.md)

An Android live wallpaper that switches among four periods based on solar elevation, with support for custom images and continuous gradients.

- Android 14 and later; Material 3 Expressive, Monet dynamic color, and system light and dark themes.
- Use built-in or custom images independently for dawn, day, dusk, and night.
- Linear, radial, and sweep gradients transition continuously across actual solar periods.
- Export and import wallpaper configurations as ZIP files with custom images.
- Direct Boot support lets the wallpaper display before the first unlock.
- 20 languages across 21 locale variants, with Arabic RTL and support for large text.
- View the project license, third-party components, and full license texts offline in the app.

## Download

[Download the latest release](https://github.com/RiversLee9312/SkyWallpaper/releases/latest) for Android 14 and later. The release assets include the APK and `SHA256SUMS`.

The official release APK uses a different signing key from local debug builds, so Android cannot install it directly over a debug build.

## Use

1. Add your device location from Settings or the preview card, or enter fixed coordinates.
2. Under “Settings → Wallpapers,” choose Images or Gradient, then edit each period.
3. Tap “Full screen preview” on the home screen to scrub through the solar day, or tap “Set wallpaper” to apply it through Android’s system preview. The two actions remain separate, including when the wallpaper is already active.

To update your location automatically as you move, set location access to “Allow all the time.” The preview fills the screen with the saved wallpaper. Drag the time slider or tap sunrise/sunset to inspect another moment; the sun chart and wallpaper update together. The top-right full-screen button hides the back button and time card and can restore them; system Back first leaves full screen. Previewing never changes the active wallpaper or automatic-switching preference. “Keep…” in Settings fixes the last previewed period and turns off automatic switching. Manually entered coordinates stay fixed until you refresh the device location.

Images are imported through the system photo picker, without requiring access to your entire photo library. The app corrects image orientation, limits images to 4096 pixels on the longest edge and 8 megapixels total, and saves a local copy so it no longer depends on the original photo library file.

## Gradients

All four periods share one gradient type so transitions can be interpolated continuously. Each period supports 2–6 colors and stop positions. The photo-style editor keeps a screen-proportioned preview separate from its tools. Drag the start/end points or center on the image and drag color stops along the color strip. Tap a color for live hex/HSV adjustments, or use Geometry for precise coordinates and stop positions. Both tools occupy their own pane, leaving the preview visible. Radial gradients also have a radius slider ranging from 10% to 400%. Coordinates start at the top-left; the radius is based on the shorter side of the screen.

Each gradient type saves its own four period configurations. Switching types restores that type’s colors, stops and geometry. The editor saves to the type it was opened with. On upgrade from 1.0.2, the surviving shared configuration is assigned to the previously selected type; the original records are retained locally. Values already overwritten by older versions cannot be reconstructed.

The bundled defaults reproduce all 12 gradient profiles exported from the device on 2026-10-28, including custom centers, radii and stop positions. New defaults apply to unsaved or reset profiles; existing saved settings are retained. The source is `app/src/main/assets/default_gradients.json`; regenerate Kotlin defaults with `python tools/generate_gradient_defaults.py`.

Dawn blends into day as geometric solar elevation rises from 0° to 40°, and day blends into dusk as it falls from 40° to 0°, using smoothstep interpolation. At or above 40° the day preset is used in full. Night fades in as elevation falls from 0° to −6°, and fades out as it rises from −6° to 0°. Above the horizon the night palette has no influence; below −6° the night preset is used in full. Colors, stops and geometry transition together, including with unequal stop counts. When the sun turns around without reaching 40° or −6°, adjacent dawn/dusk palettes blend continuously while day/night weights still follow the actual elevation. Without a location or with automatic switching off, a fixed period is used. These transitions use geometric 0°, distinct from the −0.833° threshold used for the displayed sunrise and sunset events.

In image mode, dawn spans solar elevation rising from −6° to +10°, and dusk spans it falling from +10° to −6°. The remaining time is shown as day or night based on solar elevation. Solar position is calculated on-device, and event times are shown in UTC / GMT regardless of the phone's time zone.

## Export and import

Open “Settings → Wallpapers → Wallpaper configuration.” Export creates a ZIP with `config.json` and custom images in `images/DAWN.jpg`, `images/DAY.jpg`, `images/DUSK.jpg` and `images/NIGHT.jpg`, when present. Built-in images need no additional files. The JSON includes the active image/gradient mode, selected gradient type, all 12 gradient profiles, automatic switching, and the fixed period. Location, app language and permissions are excluded.

Import checks the format version, gradient parameters, ZIP paths, file sizes and images before asking to replace the current wallpaper configuration. Cancelling or an invalid package leaves the current configuration intact. Selected custom images are copied into Direct Boot storage, so the imported wallpaper does not depend on the ZIP afterward. Files are selected through Android’s document picker without broad storage permission. Version 1 packages are limited to 100 MiB, with images subject to the same 4096-pixel / 8-megapixel limits as photo imports.

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

Device tests run instrumentation directly to avoid Gradle connected tests uninstalling the target app during cleanup. Tests cover solar periods, gradient interpolation and type isolation, configuration ZIP round trips and rejection of invalid packages, image import, Direct Boot, state restoration, predictive back, locale formatting, and the offline license page. The physical test device is a OnePlus 11 running LOS 23.2 / Android 16.

- `tools/check_i18n.py`: checks language coverage, string keys, placeholders, newlines, and formatting. Translations were adapted with GPT-6 Luna (max).
- `tools/update_licenses.py`: updates the offline licenses and third-party inventory based on resolved dependencies; the first dependency resolution requires network access. Builds verify the inventory matches the dependencies.
- `tools/device_ui.py`: reads, clicks, and captures screenshots through ADB; temporary output goes in the ignored `verification/` directory.
- `tools/import_material_symbols.py`: imports Material Symbols Rounded vector assets.

Report issues through [Issues](https://github.com/RiversLee9312/SkyWallpaper/issues). Include the Android version, device model, and reproduction steps; do not include actual coordinates.

## License and assets

The project's original code, documentation, and project-owned assets are under the [Apache License 2.0](LICENSE). Copyright and notices must be retained; use, modification, and distribution are permitted under the license terms. Third-party components retain their own copyright and license terms; see [NOTICE](NOTICE) and the [third-party notices](THIRD_PARTY_NOTICES.md).

The interface icons come from Google's [Material Symbols Rounded](https://github.com/google/material-design-icons), converted from SVG to Android VectorDrawable. The original license is at `artwork/material-symbols-LICENSE.txt`. The app icon was generated with GPT-image; its prompt is at `artwork/app-icon-sunrise-prompt.txt`.

Design references: [Material 3 Expressive](https://design.google/library/expressive-material-design-google-research), [Apple interface writing guidance](https://developer.apple.com/videos/play/wwdc2022/10037/).
