# async_wallpaper

Flutter wallpaper plugin with support for:

- static wallpaper from URL or file path
- home, lock, or both targets
- live wallpaper from local video file
- opening Android wallpaper chooser
- downloading wallpapers to Photos (iOS and Android)

## Demo

| Example Demo | Example App Screenshot |
|---|---|
| ![Example Demo](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/demo.gif) | ![Example App Screenshot](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/image.jpg) |

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

### Material You support check

```dart
final MaterialYouSupport support = await AsyncWallpaper.checkMaterialYouSupport();
```

### Download wallpaper (iOS + Android)

```dart
final WallpaperResult result = await AsyncWallpaper.downloadWallpaper(
  const DownloadWallpaperRequest(
    url: 'https://example.com/wallpaper.jpg',
  ),
);
```

### Platform behavior

- Android: wallpaper apply APIs + live wallpaper + chooser + download.
- iOS: download API supported; apply/live/chooser APIs return `unsupported`.

### iOS permission

For iOS downloads to Photos, add this key to your app `Info.plist`:

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>Allows saving downloaded wallpapers to your Photos library.</string>
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
