# async_wallpaper

Android wallpaper plugin for Flutter with support for:

- static wallpaper from URL or file path
- home, lock, or both targets
- live wallpaper from local video file
- opening Android wallpaper chooser
- wallpaper rotation with interval, charging, and time-of-day triggers

## Demo

| Example Demo | Example App Screenshot |
|---|---|
| ![Example Demo](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/demo.gif) | ![Example App Screenshot](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/image.jpg) |

## Version 3.1.0 highlights

- Flutter baseline: `3.41.4`
- Android tooling: AGP `8.11.1`, Gradle `8.14`, Kotlin plugin `2.2.20`, Java `17`
- Android `minSdk` raised to `24`
- Android host code migrated from Java to Kotlin
- Rotation API support: start, stop, status, and rotate-now
- Rotation triggers: interval, charging connected, and time-of-day active hours
- Rotation playlist supports mixed URL and file sources with local caching

## Requirements

- Flutter `>=3.41.4`
- Dart `>=3.9.0 <4.0.0`
- Android `minSdk 24`

## Installation

```yaml
dependencies:
  async_wallpaper: ^3.1.0
```

## Usage

```dart
import 'package:async_wallpaper/async_wallpaper.dart';
```

### Set wallpaper from URL

```dart
final WallpaperResult result = await AsyncWallpaper.setWallpaper(
  const WallpaperRequest(
    target: WallpaperTarget.both,
    sourceType: WallpaperSourceType.url,
    source: 'https://example.com/wallpaper.jpg',
    goToHome: true,
  ),
);

if (!result.isSuccess) {
  debugPrint('Wallpaper failed: ${result.error?.message}');
}
```

### Set wallpaper from file

```dart
final WallpaperResult result = await AsyncWallpaper.setWallpaper(
  WallpaperRequest(
    target: WallpaperTarget.home,
    sourceType: WallpaperSourceType.file,
    source: '/storage/emulated/0/Download/wallpaper.jpg',
  ),
);
```

### Set live wallpaper

```dart
final WallpaperResult result = await AsyncWallpaper.setLiveWallpaper(
  const LiveWallpaperRequest(
    filePath: '/storage/emulated/0/Download/live.mp4',
    goToHome: false,
  ),
);
```

### Open wallpaper chooser

```dart
await AsyncWallpaper.openWallpaperChooser();
```

### Start wallpaper rotation (Android only)

```dart
final WallpaperResult result = await AsyncWallpaper.startWallpaperRotation(
  const WallpaperRotationRequest(
    sources: <WallpaperRotationSource>[
      WallpaperRotationSource(
        sourceType: WallpaperSourceType.url,
        source: 'https://example.com/wallpaper1.jpg',
      ),
      WallpaperRotationSource(
        sourceType: WallpaperSourceType.file,
        source: '/storage/emulated/0/Download/wallpaper2.jpg',
      ),
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
```

Notes:
- Minimum interval is `15` minutes.
- URL entries are cached locally before rotation starts.
- Rotation runs from local files only.
- Charging trigger rotates when power is connected.
- Time-of-day trigger rotates only between `activeHoursStart` and `activeHoursEnd`.

### Rotation controls

```dart
await AsyncWallpaper.rotateWallpaperNow();
await AsyncWallpaper.getWallpaperRotationStatus();
await AsyncWallpaper.stopWallpaperRotation();
```

### Material You support check

```dart
final MaterialYouSupport support = await AsyncWallpaper.checkMaterialYouSupport();
```

## Migration from 2.x

- `HOME_SCREEN`, `LOCK_SCREEN`, `BOTH_SCREENS` are replaced by `WallpaperTarget` enum.
- previous bool-based setter methods are replaced with typed request APIs.
- internal toasts were removed; handle UX messaging in your app.

## Bugs and Feature Requests

- Report bugs via: [Bug report template](https://github.com/codenameakshay/async_wallpaper/issues/new?template=bug_report.md)
- Request features via: [Feature request template](https://github.com/codenameakshay/async_wallpaper/issues/new?template=feature_request.md)
- Please include device model, Android version, and reproducible steps for wallpaper-related issues.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](https://github.com/codenameakshay/async_wallpaper/blob/main/CONTRIBUTING.md) before opening a PR.

## License

MIT
