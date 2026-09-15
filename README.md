# async_wallpaper

Flutter wallpaper plugin with support for:

- static wallpaper from URL, file path, content URI, or bytes
- home, lock, or both targets
- live wallpaper from video or OpenGL shader
- capability check and operation results that distinguish applied from preview-opened
- download to Photos (iOS and Android)

## Demo

| Example Demo | Example App Screenshot |
|---|---|
| ![Example Demo](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/demo.gif) | ![Example App Screenshot](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/image.jpg) |

## Version 3.2.0 highlights

- New structured API: `applyWallpaper`, `setVideoWallpaper`, `openLiveWallpaperPreview`, `setOpenGlLiveWallpaper`.
- New `WallpaperSource` (url, filePath, contentUri, bytes) with defensive copy for bytes.
- New `getCapabilities()` and `WallpaperOperationResult` with per-target results.
- Video needs prepare then preview. OpenGL needs GLSL ES 1.00 shader (1-60 FPS).
- 3.1 API stays available for source compatibility. `goToHome` stays but does nothing.

## Version 3.1.0 highlights

- Flutter baseline: `3.41.4`
- Android wallpaper rotation APIs: start, stop, status, and rotate now.
- Mixed rotation playlist support for URL and file sources with local caching.
- Rotation order modes: sequential and shuffle.
- Rotation triggers for interval, charging connected, and time-of-day windows.
- Minimum rotation interval of 15 minutes.
- Active hours configuration for time-of-day trigger.
- Basic iOS support with download-only capability (save wallpaper to Photos).
- Cross-platform `downloadWallpaper(...)` API (iOS + Android).

## Version 3.0.0 highlights

- Flutter baseline: `3.41.4`
- Android tooling: AGP `8.11.1`, Gradle `8.14`, Kotlin plugin `2.2.20`, Java `17`
- Android `minSdk` raised to `24`
- Android host code migrated from Java to Kotlin
- Breaking API redesign to typed enums, request objects, and result objects

## Requirements

- Flutter `>=3.41.4`
- Dart `>=3.9.0 <4.0.0`
- Android `minSdk 24`
- iOS `13.0+` (download support only)

## Installation

```yaml
dependencies:
  async_wallpaper: ^3.2.0
```

## Usage

```dart
import 'package:async_wallpaper/async_wallpaper.dart';
```

### Apply static wallpaper

```dart
final WallpaperOperationResult result = await AsyncWallpaper.applyWallpaper(
  const StaticWallpaperRequest(
    source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
    target: WallpaperTarget.both,
    scaleMode: WallpaperScaleMode.centerCrop,
    strategy: WallpaperApplyStrategy.direct,
  ),
);
if (result.status == WallpaperOperationStatus.applied) {
  // For `both`, make sure that you read `result.home` and `result.lock` separately.
}
```

Sources: `WallpaperSource.url`, `filePath`, `contentUri`, `bytes`. No picker dependency is necessary. See `example/README.md` for editable samples.

### Check capabilities first

```dart
final WallpaperCapabilities c = await AsyncWallpaper.getCapabilities();
if (c.supportsStaticWallpaper && c.canSetWallpaper) {
  // Show the action.
}
```

`requiresForeground` means one flow needs system UI. `direct` static apply can still run in background.

### Video live wallpaper

```dart
final req = VideoWallpaperRequest(
  source: WallpaperSource.filePath('/storage/emulated/0/Download/loop.mp4'),
  target: WallpaperTarget.home,
);
final prepared = await AsyncWallpaper.setVideoWallpaper(req);
if (prepared.status == WallpaperOperationStatus.awaitingUserConfirmation) {
  await AsyncWallpaper.openLiveWallpaperPreview(req);
}
```

`previewOpened` means the system UI opened. It does not mean the user accepted.

### OpenGL live wallpaper (Android only)

Check `supportsOpenGlLiveWallpaper` first. Use GLSL ES 1.00, `void main()`, 1-60 FPS.

### Download wallpaper (iOS + Android)

```dart
await AsyncWallpaper.downloadWallpaper(const DownloadWallpaperRequest(url: 'https://example.com/wallpaper.jpg'));
```

### Legacy 3.1 API

`setWallpaper`, `setLiveWallpaper`, `openWallpaperChooser`, and Material You helpers stay available. New code must use the structured API.

### Wallpaper rotation (Android only)

```dart
final result = await AsyncWallpaper.startWallpaperRotation(
  const WallpaperRotationRequest(
    sources: <WallpaperRotationSource>[
      WallpaperRotationSource(sourceType: WallpaperSourceType.url, source: 'https://example.com/wallpaper1.jpg'),
      WallpaperRotationSource(sourceType: WallpaperSourceType.file, source: '/storage/emulated/0/Download/wallpaper2.jpg'),
    ],
    target: WallpaperTarget.both,
    intervalMinutes: 60,
    order: WallpaperRotationOrder.shuffle,
    triggers: <WallpaperRotationTrigger>{
      WallpaperRotationTrigger.interval,
      WallpaperRotationTrigger.charging,
      WallpaperRotationTrigger.timeOfDay,
    },
    activeHoursStart: 6,
    activeHoursEnd: 23,
  ),
);
await AsyncWallpaper.getWallpaperRotationStatus();
await AsyncWallpaper.rotateWallpaperNow();
await AsyncWallpaper.stopWallpaperRotation();
```

Minimum interval is 15 minutes. URL entries are cached locally before rotation starts. Rotation
uses `WorkManager` for the interval and charging triggers and one inexact daily alarm for the
time-of-day trigger, so it needs no foreground service and no exact-alarm permission.

### Platform behavior

- Android: apply, video, OpenGL, chooser, rotation, and download — when `getCapabilities()` allows.
- iOS: download only. Other calls return `unsupported`.
- Web/desktop: return `unsupported`.
- The package shows no toast. Your app shows its own UI from the result.

### Background work

Use `WallpaperApplyStrategy.direct` for `WorkManager`. Do not use `systemCropper`, `systemPicker`, or preview from a worker. They need foreground and return `foregroundRequired`.

### iOS permission

Add to `Info.plist` for download:

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>Allows saving downloaded wallpapers to your Photos library.</string>
```

## Migration

- From 2.x: `HOME_SCREEN` etc. → `WallpaperTarget`. Boolean setters → typed requests. Toasts removed.
- From 3.1: `WallpaperRequest(sourceType+source)` → `StaticWallpaperRequest(source: WallpaperSource...)`. Boolean success → `WallpaperOperationResult`. `goToHome` does nothing.

Details: `doc/android-compatibility.md`, `doc/issues-3.2.0.md`.

## Bugs and Feature Requests

- Report bugs via: [Bug report template](https://github.com/codenameakshay/async_wallpaper/issues/new?template=bug_report.md)
- Request features via: [Feature request template](https://github.com/codenameakshay/async_wallpaper/issues/new?template=feature_request.md)
- Include device model, Android version, and steps to reproduce.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](https://github.com/codenameakshay/async_wallpaper/blob/main/CONTRIBUTING.md) before you open a PR.

## License

MIT
